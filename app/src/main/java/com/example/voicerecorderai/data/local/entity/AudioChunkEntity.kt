package com.example.voicerecorderai.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audio_chunks",
    foreignKeys = [
        ForeignKey(
            entity = RecordingEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("recordingId")]
)
data class AudioChunkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val recordingId: Long,
    val chunkIndex: Int,
    val filePath: String,
    val startTime: Long,
    val endTime: Long,
    val duration: Long,
    val transcription: String? = null,
    val isTranscribed: Boolean = false,
    val isSilent: Boolean = false,
    val uploadAttempts: Int = 0,
    val lastUploadAttempt: Long? = null
)

