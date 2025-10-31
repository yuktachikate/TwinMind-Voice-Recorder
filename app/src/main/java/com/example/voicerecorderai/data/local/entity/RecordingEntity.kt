package com.example.voicerecorderai.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String = "New Recording",
    val startTime: Long,
    val endTime: Long? = null,
    val duration: Long = 0,
    val status: RecordingStatus,
    val filePaths: String = "", // JSON list of file paths
    val transcription: String? = null,
    val summary: String? = null,
    val actionItems: String? = null,
    val keyPoints: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

enum class RecordingStatus {
    RECORDING,
    PAUSED,
    STOPPED,
    TRANSCRIBING,
    TRANSCRIBED,
    SUMMARIZING,
    COMPLETED,
    ERROR
}

