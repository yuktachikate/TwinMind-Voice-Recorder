package com.example.voicerecorderai.data.remote

import com.example.voicerecorderai.data.remote.model.SummaryResponse
import com.example.voicerecorderai.data.remote.model.TranscriptionResponse
import java.io.File

/**
 * Interface for voice transcription and summarization APIs
 *
 * Implementations:
 * - MockApiService: Simulated responses for testing
 * - OpenAIApiService: Real OpenAI Whisper + GPT API calls
 */
interface VoiceApi {

    /**
     * Transcribe audio file to text
     * @param audioFile Audio file to transcribe (PCM, M4A, etc.)
     * @return TranscriptionResponse with text, language, and duration
     */
    suspend fun transcribeAudio(audioFile: File): TranscriptionResponse

    /**
     * Generate summary from transcript
     * @param transcript Text to summarize
     * @return SummaryResponse with title, summary, action items, key points
     */
    suspend fun generateSummary(transcript: String): SummaryResponse

    /**
     * Generate summary with streaming response
     * @param transcript Text to summarize
     * @param onChunk Callback for each chunk of text
     */
    suspend fun generateSummaryStream(
        transcript: String,
        onChunk: (String) -> Unit
    )
}

