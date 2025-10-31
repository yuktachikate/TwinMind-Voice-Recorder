package com.example.voicerecorderai.ui.viewmodel

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.voicerecorderai.data.repository.RecordingRepository
import com.example.voicerecorderai.domain.model.Recording
import com.example.voicerecorderai.service.RecordingService
import com.example.voicerecorderai.service.RecordingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecordingViewModel @Inject constructor(
    private val repository: RecordingRepository
) : ViewModel() {

    private var recordingService: RecordingService? = null
    private var isBound = false
    private var lastStoppedState: RecordingState.Stopped? = null  // Preserve stopped state

    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted.asStateFlow()

    private val _currentRecording = MutableStateFlow<Recording?>(null)
    val currentRecording: StateFlow<Recording?> = _currentRecording.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as RecordingService.RecordingBinder
            recordingService = binder.getService()
            isBound = true

            // Start observing recording state
            viewModelScope.launch {
                recordingService?.recordingState?.collect { state ->
                    _recordingState.value = state

                    // Preserve stopped state for navigation
                    if (state is RecordingState.Stopped) {
                        lastStoppedState = state
                    }

                    // Load current recording when recording starts
                    when (state) {
                        is RecordingState.Recording -> loadRecording(state.recordingId)
                        is RecordingState.Paused -> loadRecording(state.recordingId)
                        else -> { /* No action needed */ }
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            recordingService = null
            isBound = false

            // Restore stopped state if we had one (service stopped normally)
            lastStoppedState?.let {
                _recordingState.value = it
            }
        }
    }

    fun bindService(context: Context) {
        val intent = Intent(context, RecordingService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun unbindService(context: Context) {
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
        }
    }

    fun checkPermissions(context: Context) {
        val audioPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val notificationPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        _permissionsGranted.value = audioPermission && notificationPermission
    }

    fun startRecording(context: Context) {
        viewModelScope.launch {
            val intent = Intent(context, RecordingService::class.java)
            ContextCompat.startForegroundService(context, intent)

            // Wait for service to bind
            while (recordingService == null) {
                kotlinx.coroutines.delay(100)
            }

            recordingService?.startRecording()
        }
    }

    fun pauseRecording() {
        recordingService?.pauseRecording()
    }

    fun resumeRecording() {
        recordingService?.resumeRecording()
    }

    fun stopRecording() {
        viewModelScope.launch {
            recordingService?.stopRecording()
        }
    }

    private fun loadRecording(recordingId: Long) {
        viewModelScope.launch {
            repository.getRecordingById(recordingId).collect { recording ->
                _currentRecording.value = recording
            }
        }
    }

    fun getRecordingById(id: Long): Flow<Recording?> {
        return repository.getRecordingById(id)
    }

    override fun onCleared() {
        super.onCleared()
        recordingService = null
    }
}

