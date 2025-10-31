package com.example.voicerecorderai.data.remote

import com.example.voicerecorderai.data.remote.model.SummaryResponse
import com.example.voicerecorderai.data.remote.model.TranscriptionResponse
import kotlinx.coroutines.delay
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Mock API Service for development and testing
 *
 * This service simulates API calls to transcription and summarization services.
 * In production, replace this with actual API implementations:
 * - OpenAI Whisper API for transcription
 * - OpenAI GPT or Google Gemini for summarization
 *
 * See API_SETUP_GUIDE.md for integration instructions.
 */
@Singleton
class MockApiService @Inject constructor() : VoiceApi {

    override suspend fun transcribeAudio(audioFile: File): TranscriptionResponse {
        // Simulate network delay
        delay(1500 + Random.nextLong(500, 1500))

        // Generate more realistic mock transcription based on file duration
        val durationSeconds = audioFile.length() / (16000 * 2) // Assuming 16kHz, 16-bit mono
        val wordCount = (durationSeconds * 2.5).toInt().coerceAtLeast(10) // ~2.5 words per second

        val transcriptionText = generateRealisticTranscription(wordCount, audioFile.name)

        return TranscriptionResponse(
            text = transcriptionText,
            language = "en",
            duration = durationSeconds.toDouble()
        )
    }

    override suspend fun generateSummary(transcript: String): SummaryResponse {
        // Simulate network delay for summary generation
        delay(2000 + Random.nextLong(500, 2000))

        // Generate contextual summary based on transcript length
        val wordCount = transcript.split(" ").size
        val topicIndicators = extractTopics(transcript)

        return SummaryResponse(
            title = generateTitle(topicIndicators),
            summary = generateContextualSummary(transcript, wordCount, topicIndicators),
            actionItems = generateActionItems(topicIndicators),
            keyPoints = generateKeyPoints(topicIndicators)
        )
    }

    override suspend fun generateSummaryStream(transcript: String, onChunk: (String) -> Unit) {
        val summary = generateSummary(transcript)

        // Simulate streaming by sending the summary in chunks
        val fullText = buildString {
            append("**${summary.title}**\n\n")
            append("${summary.summary}\n\n")
            append("**Action Items:**\n")
            summary.actionItems.forEachIndexed { index, item ->
                append("${index + 1}. $item\n")
            }
            append("\n**Key Points:**\n")
            summary.keyPoints.forEach { append("• $it\n") }
        }

        // Stream with realistic word-by-word delay
        fullText.split(" ").forEach { word ->
            onChunk("$word ")
            delay(30 + Random.nextLong(0, 40)) // Variable delay per word
        }
    }

    private fun generateRealisticTranscription(wordCount: Int, fileName: String): String {
        val samplePhrases = listOf(
            "Let's discuss the project timeline and deliverables",
            "We need to focus on the key priorities for this quarter",
            "The team has been working hard on implementing the new features",
            "I think we should review the customer feedback carefully",
            "Can you provide an update on the current status",
            "We should schedule a follow-up meeting to address this",
            "The data shows some interesting trends we should explore",
            "Let's make sure everyone is aligned on the objectives",
            "I appreciate everyone's efforts on this initiative",
            "We need to consider the budget constraints for this project"
        )

        val words = mutableListOf<String>()
        while (words.size < wordCount) {
            words.addAll(samplePhrases.random().split(" "))
        }

        return words.take(wordCount).joinToString(" ") + "."
    }

    private fun extractTopics(transcript: String): List<String> {
        val keywords = transcript.lowercase().split(" ")
        val topics = mutableListOf<String>()

        if (keywords.any { it.contains("project") || it.contains("timeline") }) topics.add("project")
        if (keywords.any { it.contains("team") || it.contains("meeting") }) topics.add("team")
        if (keywords.any { it.contains("customer") || it.contains("feedback") }) topics.add("customer")
        if (keywords.any { it.contains("data") || it.contains("analysis") }) topics.add("data")
        if (keywords.any { it.contains("budget") || it.contains("cost") }) topics.add("budget")

        return topics.ifEmpty { listOf("general discussion") }
    }

    private fun generateTitle(topics: List<String>): String {
        return when {
            topics.contains("project") -> "Project Planning Meeting"
            topics.contains("customer") -> "Customer Feedback Review"
            topics.contains("data") -> "Data Analysis Discussion"
            topics.contains("budget") -> "Budget Review Meeting"
            else -> "Team Discussion Summary"
        }
    }

    private fun generateContextualSummary(
        transcript: String,
        wordCount: Int,
        topics: List<String>
    ): String {
        val complexity = when {
            wordCount < 50 -> "brief"
            wordCount < 200 -> "moderate"
            else -> "detailed"
        }

        return buildString {
            append("This $complexity discussion covered ${topics.joinToString(", ")}. ")
            append("The conversation included ${wordCount} words across approximately ${wordCount / 150} minutes of recording. ")

            when {
                topics.contains("project") -> append("The team reviewed project milestones, discussed timeline adjustments, and aligned on next steps. ")
                topics.contains("customer") -> append("Customer feedback was analyzed and the team identified several areas for improvement. ")
                topics.contains("data") -> append("Data trends were examined and insights were shared across the team. ")
                topics.contains("budget") -> append("Budget considerations were discussed and financial constraints were addressed. ")
                else -> append("Various topics were discussed and team members provided valuable input. ")
            }

            append("All participants contributed to the discussion and key decisions were documented.")
        }
    }

    private fun generateActionItems(topics: List<String>): List<String> {
        val baseActions = listOf(
            "Schedule follow-up meeting to review progress",
            "Share meeting notes with all stakeholders",
            "Update project documentation with decisions made"
        )

        val topicSpecificActions = when {
            topics.contains("project") -> listOf(
                "Update project timeline in tracking system",
                "Assign tasks to team members by end of week"
            )
            topics.contains("customer") -> listOf(
                "Respond to customer inquiries within 24 hours",
                "Implement top 3 customer-requested features"
            )
            topics.contains("data") -> listOf(
                "Complete data analysis report by Friday",
                "Present findings to leadership team"
            )
            topics.contains("budget") -> listOf(
                "Review and approve budget adjustments",
                "Identify cost-saving opportunities"
            )
            else -> listOf("Review action items and assign owners")
        }

        return (baseActions + topicSpecificActions).take(5)
    }

    private fun generateKeyPoints(topics: List<String>): List<String> {
        val basePoints = listOf(
            "All team members actively participated in the discussion",
            "Clear next steps were established for moving forward"
        )

        val topicSpecificPoints = when {
            topics.contains("project") -> listOf(
                "Project is on track to meet major milestones",
                "Resource allocation was optimized for efficiency",
                "Risk mitigation strategies were identified"
            )
            topics.contains("customer") -> listOf(
                "Customer satisfaction metrics show positive trends",
                "Several high-impact improvements were identified",
                "Customer feedback will drive upcoming features"
            )
            topics.contains("data") -> listOf(
                "Data analysis revealed actionable insights",
                "Key performance indicators are trending positively",
                "Data-driven decision making was emphasized"
            )
            topics.contains("budget") -> listOf(
                "Budget is within acceptable ranges for the quarter",
                "Cost optimization opportunities were identified",
                "Financial projections were reviewed and validated"
            )
            else -> listOf(
                "Important decisions were made collaboratively",
                "Team alignment was achieved on key priorities"
            )
        }

        return (basePoints + topicSpecificPoints).take(6)
    }
}
