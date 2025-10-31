package com.example.voicerecorderai.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.voicerecorderai.data.repository.RecordingRepository
import com.example.voicerecorderai.domain.model.Recording
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: RecordingRepository
) : ViewModel() {

    private val _recordings = MutableStateFlow<List<Recording>>(emptyList())
    val recordings: StateFlow<List<Recording>> = _recordings.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadRecordings()
    }

    private fun loadRecordings() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getAllRecordings().collect { recordings ->
                _recordings.value = recordings
                _isLoading.value = false
            }
        }
    }

    fun deleteRecording(recording: Recording) {
        viewModelScope.launch {
            repository.deleteRecording(recording)
        }
    }

    fun refreshRecordings() {
        loadRecordings()
    }
}

