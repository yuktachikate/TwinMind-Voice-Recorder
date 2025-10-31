package com.example.voicerecorderai.data.remote.model

data class TranscriptionRequest(
    val audioBase64: String? = null,
    val audioUrl: String? = null
)

data class TranscriptionResponse(
    val text: String,
    val language: String? = null,
    val duration: Double? = null
)

data class SummaryRequest(
    val transcript: String
)

data class SummaryResponse(
    val title: String,
    val summary: String,
    val actionItems: List<String>,
    val keyPoints: List<String>
)

