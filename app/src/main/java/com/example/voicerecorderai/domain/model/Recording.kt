package com.example.voicerecorderai.domain.model

data class Recording(
    val id: Long = 0,
    val title: String = "New Recording",
    val startTime: Long,
    val endTime: Long? = null,
    val duration: Long = 0,
    val status: RecordingStatus,
    val transcription: String? = null,
    val summary: Summary? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class Summary(
    val title: String,
    val content: String,
    val actionItems: List<String>,
    val keyPoints: List<String>
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

data class AudioChunk(
    val id: Long = 0,
    val recordingId: Long,
    val chunkIndex: Int,
    val filePath: String,
    val startTime: Long,
    val endTime: Long,
    val duration: Long,
    val transcription: String? = null,
    val isTranscribed: Boolean = false
)

