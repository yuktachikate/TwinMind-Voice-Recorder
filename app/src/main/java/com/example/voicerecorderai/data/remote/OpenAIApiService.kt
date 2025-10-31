package com.example.voicerecorderai.data.remote

import android.util.Log
import com.example.voicerecorderai.data.remote.model.SummaryResponse
import com.example.voicerecorderai.data.remote.model.TranscriptionResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OpenAI API Service for real transcription and summarization
 *
 * Uses:
 * - Whisper API for audio transcription
 * - GPT-4 Turbo for meeting summarization with streaming
 */
@Singleton
class OpenAIApiService @Inject constructor(
    private val apiKey: String
) : VoiceApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $apiKey")
                .build()
            chain.proceed(request)
        }
        .build()

    companion object {
        private const val TAG = "OpenAIApiService"
        private const val WHISPER_URL = "https://api.openai.com/v1/audio/transcriptions"
        private const val CHAT_URL = "https://api.openai.com/v1/chat/completions"
    }

    override suspend fun transcribeAudio(audioFile: File): TranscriptionResponse = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Transcribing audio file: ${audioFile.name}, size: ${audioFile.length()} bytes")

            // Convert PCM to WAV if needed
            val fileToUpload = if (audioFile.extension == "pcm") {
                Log.d(TAG, "Converting PCM to WAV format")
                convertPcmToWav(audioFile)
            } else {
                audioFile
            }

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    fileToUpload.name,
                    fileToUpload.asRequestBody("audio/wav".toMediaType())
                )
                .addFormDataPart("model", "whisper-1")
                .addFormDataPart("language", "en")
                .addFormDataPart("response_format", "json")
                .build()

            val request = Request.Builder()
                .url(WHISPER_URL)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: throw Exception("Empty response from Whisper API")

            Log.d(TAG, "Whisper API response code: ${response.code}")

            if (!response.isSuccessful) {
                Log.e(TAG, "Whisper API error: $responseBody")
                throw Exception("Whisper API error: ${response.code} - $responseBody")
            }

            val json = JSONObject(responseBody)
            val text = json.getString("text")
            val duration = json.optDouble("duration", 0.0)

            Log.d(TAG, "Transcription successful: ${text.take(100)}...")

            // Clean up temporary WAV file if we created one
            if (fileToUpload != audioFile) {
                fileToUpload.delete()
            }

            TranscriptionResponse(
                text = text,
                language = "en",
                duration = duration
            )
        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed", e)
            throw Exception("Failed to transcribe audio: ${e.message}", e)
        }
    }

    /**
     * Convert PCM audio to WAV format by adding proper WAV header
     * PCM format: 16-bit, 16kHz, Mono
     */
    private fun convertPcmToWav(pcmFile: File): File {
        val wavFile = File(pcmFile.parentFile, pcmFile.nameWithoutExtension + ".wav")

        java.io.FileOutputStream(wavFile).use { output ->
            val pcmData = pcmFile.readBytes()
            val pcmSize = pcmData.size

            // WAV Header (44 bytes)
            val sampleRate = 16000  // 16kHz
            val channels = 1         // Mono
            val bitsPerSample = 16   // 16-bit

            val byteRate = sampleRate * channels * bitsPerSample / 8
            val blockAlign = channels * bitsPerSample / 8

            // RIFF header
            output.write("RIFF".toByteArray())
            output.write(intToBytes(pcmSize + 36))  // ChunkSize
            output.write("WAVE".toByteArray())

            // fmt subchunk
            output.write("fmt ".toByteArray())
            output.write(intToBytes(16))  // Subchunk1Size (16 for PCM)
            output.write(shortToBytes(1))  // AudioFormat (1 = PCM)
            output.write(shortToBytes(channels.toShort()))
            output.write(intToBytes(sampleRate))
            output.write(intToBytes(byteRate))
            output.write(shortToBytes(blockAlign.toShort()))
            output.write(shortToBytes(bitsPerSample.toShort()))

            // data subchunk
            output.write("data".toByteArray())
            output.write(intToBytes(pcmSize))
            output.write(pcmData)
        }

        Log.d(TAG, "Converted PCM to WAV: ${wavFile.name}, size: ${wavFile.length()} bytes")
        return wavFile
    }

    private fun intToBytes(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }

    private fun shortToBytes(value: Short): ByteArray {
        return byteArrayOf(
            (value.toInt() and 0xFF).toByte(),
            ((value.toInt() shr 8) and 0xFF).toByte()
        )
    }

    override suspend fun generateSummary(transcript: String): SummaryResponse = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Generating summary for transcript of ${transcript.length} characters")

            val systemPrompt = """
                You are a helpful assistant that creates comprehensive meeting summaries.
                
                Analyze the transcript and provide a structured summary with these sections:
                
                1. Title: A descriptive title that captures the main topic (max 60 characters)
                
                2. Summary: A detailed overview (5-8 sentences) covering:
                   - Main topic and purpose
                   - Key discussions and decisions
                   - Important outcomes or conclusions
                   - Context and background mentioned
                
                3. Action Items: List concrete action items ONLY IF they are explicitly mentioned or clearly implied in the transcript.
                   - If there are NO action items, use an empty array []
                   - Each action item should be specific and actionable
                   - Include who is responsible if mentioned
                
                4. Key Points: Comprehensive list of important points discussed (6-10 items):
                   - Main arguments or ideas presented
                   - Important facts or data mentioned
                   - Decisions made
                   - Questions raised
                   - Notable insights or observations
                
                Format your response as JSON:
                {
                  "title": "Meeting title",
                  "summary": "Detailed 5-8 sentence overview covering all major aspects...",
                  "actionItems": ["Action 1", "Action 2"] or [],
                  "keyPoints": ["Point 1", "Point 2", "Point 3", "Point 4", "Point 5", "Point 6", ...]
                }
                
                Important: Be comprehensive and detailed. Don't force action items if none exist.
            """.trimIndent()

            val requestBody = JSONObject().apply {
                put("model", "gpt-4o")  // GPT-4o for better and faster summarization
                put("messages", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "Summarize this transcript in detail:\n\n$transcript")
                    })
                })
                put("temperature", 0.7)
                put("max_tokens", 2000)  // Increased from 1000 to 2000 for more detailed summaries
                put("response_format", JSONObject().put("type", "json_object"))
            }.toString()

            val request = Request.Builder()
                .url(CHAT_URL)
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: throw Exception("Empty response from GPT API")

            Log.d(TAG, "GPT API response code: ${response.code}")

            if (!response.isSuccessful) {
                Log.e(TAG, "GPT API error: $responseBody")
                throw Exception("GPT API error: ${response.code} - $responseBody")
            }

            val json = JSONObject(responseBody)
            val content = json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")

            val summaryJson = JSONObject(content)

            val title = summaryJson.getString("title")
            val summary = summaryJson.getString("summary")
            val actionItems = summaryJson.getJSONArray("actionItems").let { array ->
                (0 until array.length()).map { array.getString(it) }
            }
            val keyPoints = summaryJson.getJSONArray("keyPoints").let { array ->
                (0 until array.length()).map { array.getString(it) }
            }

            Log.d(TAG, "Summary generated successfully: $title")

            SummaryResponse(
                title = title,
                summary = summary,
                actionItems = actionItems,
                keyPoints = keyPoints
            )
        } catch (e: Exception) {
            Log.e(TAG, "Summary generation failed", e)
            throw Exception("Failed to generate summary: ${e.message}", e)
        }
    }

    override suspend fun generateSummaryStream(
        transcript: String,
        onChunk: (String) -> Unit
    ): Unit = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Generating streaming summary for transcript of ${transcript.length} characters")

            val systemPrompt = """
                You are a helpful assistant that creates comprehensive meeting summaries.
                
                Provide a detailed structured summary in this format:
                
                **Title**
                [Descriptive title capturing the main topic, max 60 characters]
                
                **Summary**
                [Detailed 5-8 sentence overview covering:
                - Main topic and purpose
                - Key discussions and decisions
                - Important outcomes or conclusions
                - Context and relevant background]
                
                **Action Items:**
                [List concrete action items ONLY if they are explicitly mentioned in the transcript.
                If there are NO action items, write "No specific action items identified."
                Otherwise list them as:
                1. [Specific actionable item with responsible party if mentioned]
                2. [Another action item]
                ...]
                
                **Key Points:**
                [Comprehensive list of 6-10 important points:
                • [Main arguments or ideas presented]
                • [Important facts or data mentioned]
                • [Decisions made]
                • [Questions raised]
                • [Notable insights or observations]
                • [Other significant discussion points]
                ...]
                
                Important: Be thorough and detailed. Don't force action items if none exist.
            """.trimIndent()

            val requestBody = JSONObject().apply {
                put("model", "gpt-4o")  // GPT-4o for better and faster summarization
                put("messages", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "Summarize this transcript in detail:\n\n$transcript")
                    })
                })
                put("stream", true)
                put("temperature", 0.7)
                put("max_tokens", 2000)  // Increased from 1000 to 2000 for more detailed summaries
            }.toString()

            val request = Request.Builder()
                .url(CHAT_URL)
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "GPT streaming error: $errorBody")
                throw Exception("GPT streaming error: ${response.code} - $errorBody")
            }

            response.body?.byteStream()?.bufferedReader()?.use { reader ->
                reader.forEachLine { line ->
                    if (line.startsWith("data: ")) {
                        val data = line.substring(6)
                        if (data == "[DONE]") {
                            return@forEachLine
                        }

                        try {
                            val json = JSONObject(data)
                            val delta = json.getJSONArray("choices")
                                .getJSONObject(0)
                                .optJSONObject("delta")

                            val content = delta?.optString("content", "") ?: ""
                            if (content.isNotEmpty()) {
                                onChunk(content)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse streaming chunk: $data", e)
                        }
                    }
                }
            }

            Log.d(TAG, "Streaming summary completed")
        } catch (e: Exception) {
            Log.e(TAG, "Streaming summary failed", e)
            throw Exception("Failed to generate streaming summary: ${e.message}", e)
        }
    }
}

