package com.example.voicerecorderai.data.repository

import android.util.Log
import com.example.voicerecorderai.data.local.dao.AudioChunkDao
import com.example.voicerecorderai.data.local.dao.RecordingDao
import com.example.voicerecorderai.data.local.entity.AudioChunkEntity
import com.example.voicerecorderai.data.local.entity.RecordingEntity
import com.example.voicerecorderai.data.local.entity.RecordingStatus as EntityRecordingStatus
import com.example.voicerecorderai.data.remote.VoiceApi
import com.example.voicerecorderai.domain.model.AudioChunk
import com.example.voicerecorderai.domain.model.Recording
import com.example.voicerecorderai.domain.model.RecordingStatus
import com.example.voicerecorderai.domain.model.Summary
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingRepository @Inject constructor(
    private val recordingDao: RecordingDao,
    private val audioChunkDao: AudioChunkDao,
    private val voiceApi: VoiceApi,
    private val gson: Gson
) {

    private val transcriptionLocks = ConcurrentHashMap<Long, Mutex>()

    fun getAllRecordings(): Flow<List<Recording>> {
        return recordingDao.getAllRecordings().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    fun getRecordingById(id: Long): Flow<Recording?> {
        return recordingDao.getRecordingById(id).map { it?.toDomainModel() }
    }

    suspend fun getRecordingByIdOnce(id: Long): Recording? {
        return recordingDao.getRecordingByIdOnce(id)?.toDomainModel()
    }

    suspend fun createRecording(): Long {
        val recording = RecordingEntity(
            startTime = System.currentTimeMillis(),
            status = EntityRecordingStatus.RECORDING
        )
        return recordingDao.insertRecording(recording)
    }

    suspend fun updateRecordingStatus(id: Long, status: RecordingStatus) {
        recordingDao.updateRecordingStatus(id, status.toEntityStatus())
    }

    suspend fun updateRecordingTitle(recordingId: Long, newTitle: String) {
        recordingDao.updateRecordingTitle(recordingId, newTitle)
    }

    suspend fun stopRecording(id: Long) {
        val endTime = System.currentTimeMillis()
        val recording = recordingDao.getRecordingByIdOnce(id)
        recording?.let {
            val duration = endTime - it.startTime
            recordingDao.updateRecordingEnd(id, endTime, duration)
            recordingDao.updateRecordingStatus(id, EntityRecordingStatus.STOPPED)
        }
    }

    suspend fun insertAudioChunk(chunk: AudioChunk): Long {
        return audioChunkDao.insertChunk(chunk.toEntity())
    }

    suspend fun getChunksByRecordingId(recordingId: Long): List<AudioChunk> {
        return audioChunkDao.getChunksByRecordingIdOnce(recordingId).map { it.toDomainModel() }
    }

    suspend fun transcribeChunk(chunk: AudioChunk): String {
        val audioFile = File(chunk.filePath)
        val response = voiceApi.transcribeAudio(audioFile)

        // Update chunk with transcription
        audioChunkDao.updateChunkTranscription(chunk.id, response.text)

        return response.text
    }

    suspend fun finalizeLastChunk(recordingId: Long) {
        // Mark recording as stopped if it was still in recording state
        val recording = recordingDao.getRecordingByIdOnce(recordingId)
        if (recording != null && recording.status == EntityRecordingStatus.RECORDING) {
            stopRecording(recordingId)
        }

        // No need to do anything special with chunks - they're already saved
        // The transcribeRecording method will handle any untranscribed chunks
    }

    suspend fun transcribeRecording(recordingId: Long): String {
        Log.d("RecordingRepository", "transcribeRecording() called for recording $recordingId")

        val lock = transcriptionLocks.getOrPut(recordingId) { Mutex() }

        if (lock.isLocked) {
            Log.w("RecordingRepository", "Transcription for recording $recordingId is already in progress. Skipping duplicate call.")
            return ""
        }

        Log.d("RecordingRepository", "Acquiring lock for recording $recordingId")
        return lock.withLock {
            try {
                Log.d("RecordingRepository", "Lock acquired for recording $recordingId. Starting transcription...")
                recordingDao.updateRecordingStatus(recordingId, EntityRecordingStatus.TRANSCRIBING)

                val chunks = audioChunkDao.getUntranscribedChunks(recordingId)
                Log.d("RecordingRepository", "Found ${chunks.size} untranscribed chunks for recording $recordingId")

                for (chunk in chunks) {
                    try {
                        Log.d("RecordingRepository", "Transcribing chunk ${chunk.chunkIndex} for recording $recordingId")
                        val audioFile = File(chunk.filePath)
                        if (audioFile.exists()) {
                            val response = voiceApi.transcribeAudio(audioFile)
                            audioChunkDao.updateChunkTranscription(chunk.id, response.text)
                            Log.d("RecordingRepository", "Successfully transcribed chunk ${chunk.chunkIndex}")
                        } else {
                            Log.e("RecordingRepository", "Audio file not found: ${chunk.filePath}")
                        }
                    } catch (e: Exception) {
                        Log.e("RecordingRepository", "Failed to transcribe chunk ${chunk.chunkIndex}: ${e.message}", e)
                        audioChunkDao.incrementUploadAttempts(chunk.id, System.currentTimeMillis())
                        // Continue with other chunks
                    }
                }

                // Get ALL chunks (not just newly transcribed) and combine in order
                val allChunks = audioChunkDao.getChunksByRecordingIdOnce(recordingId)
                Log.d("RecordingRepository", "Combining transcriptions from ${allChunks.size} total chunks")

                val fullTranscription = allChunks
                    .sortedBy { it.chunkIndex }
                    .mapNotNull { it.transcription }
                    .filter { it.isNotBlank() }
                    .joinToString(" ")

                Log.d("RecordingRepository", "Full transcription length: ${fullTranscription.length} characters")

                // Update the recording with the complete transcription
                recordingDao.updateTranscription(
                    recordingId,
                    fullTranscription,
                    EntityRecordingStatus.TRANSCRIBED
                )

                Log.d("RecordingRepository", "Transcription completed successfully for recording $recordingId")
                fullTranscription
            } finally {
                transcriptionLocks.remove(recordingId)
                Log.d("RecordingRepository", "Lock released for recording $recordingId")
            }
        }
    }

    suspend fun generateSummary(recordingId: Long, fullTranscription: String, onProgress: (String) -> Unit) {
        Log.d("RecordingRepository", "generateSummary() called for recording $recordingId")

        if (fullTranscription.isBlank()) {
            Log.e("RecordingRepository", "No transcription available for recording $recordingId")
            recordingDao.updateRecordingStatus(recordingId, EntityRecordingStatus.ERROR)
            return
        }

        Log.d("RecordingRepository", "Generating summary for ${fullTranscription.length} characters")

        recordingDao.updateRecordingStatus(recordingId, EntityRecordingStatus.SUMMARIZING)

        try {
            Log.d("RecordingRepository", "Starting streaming summary generation...")
            // Use streaming API with FULL transcription
            voiceApi.generateSummaryStream(fullTranscription) { chunk ->
                onProgress(chunk)
            }

            Log.d("RecordingRepository", "Streaming completed. Getting final summary...")
            // Get final summary with FULL transcription
            val summary = voiceApi.generateSummary(fullTranscription)

            Log.d("RecordingRepository", "Summary generated successfully. Title: ${summary.title}, Summary length: ${summary.summary.length}")

            // Update the recording title with the generated title
            recordingDao.updateRecordingTitle(recordingId, summary.title)

            recordingDao.updateSummary(
                recordingId,
                summary.summary,
                gson.toJson(summary.actionItems),
                gson.toJson(summary.keyPoints),
                EntityRecordingStatus.COMPLETED
            )

            Log.d("RecordingRepository", "Summary and title saved to database for recording $recordingId")
        } catch (e: Exception) {
            Log.e("RecordingRepository", "Summary generation failed for recording $recordingId: ${e.message}", e)
            recordingDao.updateRecordingStatus(recordingId, EntityRecordingStatus.ERROR)
        }
    }

    suspend fun deleteRecording(recording: Recording) {
        // Delete audio files
        val chunks = audioChunkDao.getChunksByRecordingIdOnce(recording.id)
        chunks.forEach { chunk ->
            File(chunk.filePath).delete()
        }

        // Delete from database
        recordingDao.deleteRecording(recording.toEntity())
    }

    // Extension functions for mapping
    private fun RecordingEntity.toDomainModel(): Recording {
        val actionItems = try {
            actionItems?.let { gson.fromJson(it, Array<String>::class.java).toList() } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val keyPoints = try {
            keyPoints?.let { gson.fromJson(it, Array<String>::class.java).toList() } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        return Recording(
            id = id,
            title = title,
            startTime = startTime,
            endTime = endTime,
            duration = duration,
            status = status.toDomainStatus(),
            transcription = transcription,
            summary = if (summary != null) {
                Summary(
                    title = title,
                    content = summary,
                    actionItems = actionItems,
                    keyPoints = keyPoints
                )
            } else null,
            createdAt = createdAt
        )
    }

    private fun Recording.toEntity(): RecordingEntity {
        return RecordingEntity(
            id = id,
            title = title,
            startTime = startTime,
            endTime = endTime,
            duration = duration,
            status = status.toEntityStatus(),
            transcription = transcription,
            summary = summary?.content,
            actionItems = summary?.actionItems?.let { gson.toJson(it) },
            keyPoints = summary?.keyPoints?.let { gson.toJson(it) },
            createdAt = createdAt
        )
    }

    private fun AudioChunkEntity.toDomainModel(): AudioChunk {
        return AudioChunk(
            id = id,
            recordingId = recordingId,
            chunkIndex = chunkIndex,
            filePath = filePath,
            startTime = startTime,
            endTime = endTime,
            duration = duration,
            transcription = transcription,
            isTranscribed = isTranscribed
        )
    }

    private fun AudioChunk.toEntity(): AudioChunkEntity {
        return AudioChunkEntity(
            id = id,
            recordingId = recordingId,
            chunkIndex = chunkIndex,
            filePath = filePath,
            startTime = startTime,
            endTime = endTime,
            duration = duration,
            transcription = transcription,
            isTranscribed = isTranscribed
        )
    }

    private fun EntityRecordingStatus.toDomainStatus(): RecordingStatus {
        return when (this) {
            EntityRecordingStatus.RECORDING -> RecordingStatus.RECORDING
            EntityRecordingStatus.PAUSED -> RecordingStatus.PAUSED
            EntityRecordingStatus.STOPPED -> RecordingStatus.STOPPED
            EntityRecordingStatus.TRANSCRIBING -> RecordingStatus.TRANSCRIBING
            EntityRecordingStatus.TRANSCRIBED -> RecordingStatus.TRANSCRIBED
            EntityRecordingStatus.SUMMARIZING -> RecordingStatus.SUMMARIZING
            EntityRecordingStatus.COMPLETED -> RecordingStatus.COMPLETED
            EntityRecordingStatus.ERROR -> RecordingStatus.ERROR
        }
    }

    private fun RecordingStatus.toEntityStatus(): EntityRecordingStatus {
        return when (this) {
            RecordingStatus.RECORDING -> EntityRecordingStatus.RECORDING
            RecordingStatus.PAUSED -> EntityRecordingStatus.PAUSED
            RecordingStatus.STOPPED -> EntityRecordingStatus.STOPPED
            RecordingStatus.TRANSCRIBING -> EntityRecordingStatus.TRANSCRIBING
            RecordingStatus.TRANSCRIBED -> EntityRecordingStatus.TRANSCRIBED
            RecordingStatus.SUMMARIZING -> EntityRecordingStatus.SUMMARIZING
            RecordingStatus.COMPLETED -> EntityRecordingStatus.COMPLETED
            RecordingStatus.ERROR -> EntityRecordingStatus.ERROR
        }
    }
}

