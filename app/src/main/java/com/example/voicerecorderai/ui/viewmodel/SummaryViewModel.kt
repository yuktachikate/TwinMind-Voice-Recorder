package com.example.voicerecorderai.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.voicerecorderai.data.repository.RecordingRepository
import com.example.voicerecorderai.domain.model.Recording
import com.example.voicerecorderai.domain.model.RecordingStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SummaryViewModel @Inject constructor(
    private val repository: RecordingRepository
) : ViewModel() {

    private val _recording = MutableStateFlow<Recording?>(null)
    val recording: StateFlow<Recording?> = _recording.asStateFlow()

    private val _summaryProgress = MutableStateFlow("")
    val summaryProgress: StateFlow<String> = _summaryProgress.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadRecording(recordingId: Long) {
        viewModelScope.launch {
            repository.getRecordingById(recordingId).collect { recording ->
                _recording.value = recording

                // Auto-generate summary if transcription is complete but summary not generated
                if (recording?.status == RecordingStatus.TRANSCRIBED && recording.summary == null) {
                    generateSummary(recordingId)
                }
            }
        }
    }

    fun generateSummary(recordingId: Long) {
        viewModelScope.launch {
            val recording = _recording.value
            if (recording == null || recording.transcription.isNullOrBlank()) {
                _error.value = "Transcription not available to generate summary."
                return@launch
            }

            try {
                _isGenerating.value = true
                _error.value = null
                _summaryProgress.value = ""

                repository.generateSummary(recordingId, recording.transcription) { chunk ->
                    _summaryProgress.value += chunk
                }

                _isGenerating.value = false

            } catch (e: Exception) {
                _isGenerating.value = false
                _error.value = e.message ?: "Failed to generate summary"
            }
        }
    }

    fun updateRecordingTitle(recordingId: Long, newTitle: String) {
        viewModelScope.launch {
            repository.updateRecordingTitle(recordingId, newTitle)
        }
    }


    fun retryGenerateSummary() {
        _recording.value?.let { recording ->
            generateSummary(recording.id)
        }
    }

    fun clearError() {
        _error.value = null
    }
}

