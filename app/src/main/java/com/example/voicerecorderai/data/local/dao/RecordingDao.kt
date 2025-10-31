package com.example.voicerecorderai.data.local.dao

import androidx.room.*
import com.example.voicerecorderai.data.local.entity.RecordingEntity
import com.example.voicerecorderai.data.local.entity.RecordingStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {

    @Query("SELECT * FROM recordings ORDER BY createdAt DESC")
    fun getAllRecordings(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE id = :id")
    fun getRecordingById(id: Long): Flow<RecordingEntity?>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getRecordingByIdOnce(id: Long): RecordingEntity?

    @Query("SELECT * FROM recordings WHERE status = :status LIMIT 1")
    suspend fun getRecordingByStatus(status: RecordingStatus): RecordingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: RecordingEntity): Long

    @Update
    suspend fun updateRecording(recording: RecordingEntity)

    @Delete
    suspend fun deleteRecording(recording: RecordingEntity)

    @Query("UPDATE recordings SET status = :status WHERE id = :id")
    suspend fun updateRecordingStatus(id: Long, status: RecordingStatus)

    @Query("UPDATE recordings SET title = :newTitle WHERE id = :recordingId")
    suspend fun updateRecordingTitle(recordingId: Long, newTitle: String)

    @Query("UPDATE recordings SET transcription = :transcription, status = :status WHERE id = :id")
    suspend fun updateTranscription(id: Long, transcription: String, status: RecordingStatus)

    @Query("UPDATE recordings SET summary = :summary, actionItems = :actionItems, keyPoints = :keyPoints, status = :status WHERE id = :id")
    suspend fun updateSummary(
        id: Long,
        summary: String?,
        actionItems: String?,
        keyPoints: String?,
        status: RecordingStatus
    )

    @Query("UPDATE recordings SET endTime = :endTime, duration = :duration WHERE id = :id")
    suspend fun updateRecordingEnd(id: Long, endTime: Long, duration: Long)
}

