package com.example.voicerecorderai.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.voicerecorderai.data.repository.RecordingRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Worker to handle transcription recovery after process death
 * Ensures no data is lost if the app crashes during recording
 */
@HiltWorker
class TranscriptionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: RecordingRepository
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "TranscriptionWorker"
        const val KEY_RECORDING_ID = "recordingId"
        const val KEY_FINALIZE_CHUNK = "finalizeChunk"
    }

    override suspend fun doWork(): Result {
        val recordingId = inputData.getLong(KEY_RECORDING_ID, -1L)
        val shouldFinalizeChunk = inputData.getBoolean(KEY_FINALIZE_CHUNK, false)

        Log.d(TAG, "===== TranscriptionWorker started for recording $recordingId =====")
        Log.d(TAG, "shouldFinalizeChunk: $shouldFinalizeChunk, runAttemptCount: $runAttemptCount")

        if (recordingId == -1L) {
            Log.e(TAG, "Invalid recording ID")
            return Result.failure()
        }

        return try {
            Log.d(TAG, "Starting transcription recovery for recording $recordingId")

            // Finalize last chunk if needed (process was killed during recording)
            if (shouldFinalizeChunk) {
                Log.d(TAG, "Finalizing last chunk for recording $recordingId")
                repository.finalizeLastChunk(recordingId)
                Log.d(TAG, "Last chunk finalized")
            }

            // Resume transcription for any untranscribed chunks
            Log.d(TAG, "Calling transcribeRecording for recording $recordingId")
            val fullTranscription = repository.transcribeRecording(recordingId)
            Log.d(TAG, "transcribeRecording returned. Transcription length: ${fullTranscription.length}")

            // Automatically generate summary after transcription completes
            if (fullTranscription.isNotBlank()) {
                Log.d(TAG, "Transcription completed, generating summary for recording $recordingId")
                repository.generateSummary(recordingId, fullTranscription) { progress ->
                    Log.d(TAG, "Summary progress: ${progress.take(50)}...")
                }
                Log.d(TAG, "Summary generation completed for recording $recordingId")
            } else {
                Log.w(TAG, "Transcription is blank for recording $recordingId, skipping summary generation")
            }

            Log.d(TAG, "===== TranscriptionWorker completed successfully for recording $recordingId =====")
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "TranscriptionWorker failed for recording $recordingId: ${e.message}", e)
            e.printStackTrace()

            // Retry on failure (up to 3 times by default)
            if (runAttemptCount < 3) {
                Log.d(TAG, "Retrying... Attempt ${runAttemptCount + 1} for recording $recordingId")
                Result.retry()
            } else {
                Log.e(TAG, "Max retry attempts reached for recording $recordingId, giving up")
                Result.failure()
            }
        }
    }
}

