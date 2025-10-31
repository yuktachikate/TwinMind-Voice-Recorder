package com.example.voicerecorderai.data.local.dao

import androidx.room.*
import com.example.voicerecorderai.data.local.entity.AudioChunkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioChunkDao {

    @Query("SELECT * FROM audio_chunks WHERE recordingId = :recordingId ORDER BY chunkIndex ASC")
    fun getChunksByRecordingId(recordingId: Long): Flow<List<AudioChunkEntity>>

    @Query("SELECT * FROM audio_chunks WHERE recordingId = :recordingId ORDER BY chunkIndex ASC")
    suspend fun getChunksByRecordingIdOnce(recordingId: Long): List<AudioChunkEntity>

    @Query("SELECT * FROM audio_chunks WHERE id = :id")
    suspend fun getChunkById(id: Long): AudioChunkEntity?

    @Query("SELECT * FROM audio_chunks WHERE recordingId = :recordingId AND isTranscribed = 0 ORDER BY chunkIndex ASC")
    suspend fun getUntranscribedChunks(recordingId: Long): List<AudioChunkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunk(chunk: AudioChunkEntity): Long

    @Update
    suspend fun updateChunk(chunk: AudioChunkEntity)

    @Delete
    suspend fun deleteChunk(chunk: AudioChunkEntity)

    @Query("UPDATE audio_chunks SET transcription = :transcription, isTranscribed = 1 WHERE id = :id")
    suspend fun updateChunkTranscription(id: Long, transcription: String)

    @Query("UPDATE audio_chunks SET uploadAttempts = uploadAttempts + 1, lastUploadAttempt = :timestamp WHERE id = :id")
    suspend fun incrementUploadAttempts(id: Long, timestamp: Long)

    @Query("DELETE FROM audio_chunks WHERE recordingId = :recordingId")
    suspend fun deleteChunksByRecordingId(recordingId: Long)
}

