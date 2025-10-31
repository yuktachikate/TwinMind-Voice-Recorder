package com.example.voicerecorderai.service

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.StatFs
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.voicerecorderai.MainActivity
import com.example.voicerecorderai.R
import com.example.voicerecorderai.VoiceRecorderApplication
import com.example.voicerecorderai.data.repository.RecordingRepository
import com.example.voicerecorderai.domain.model.AudioChunk
import com.example.voicerecorderai.domain.model.RecordingStatus
import com.example.voicerecorderai.worker.TranscriptionWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class RecordingService : Service() {

    @Inject
    lateinit var repository: RecordingRepository

    private val binder = RecordingBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var isPaused = false
    private var currentRecordingId: Long = 0
    private var chunkIndex = 0
    private var recordingStartTime = 0L
    private var chunkStartTime = 0L
    private var elapsedTime = 0L
    private var pauseStartTime = 0L

    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState

    private var telephonyManager: TelephonyManager? = null
    private var phoneStateListener: PhoneStateListener? = null
    private var audioManager: AudioManager? = null
    private var audioFocusListener: AudioManager.OnAudioFocusChangeListener? = null
    private var headsetReceiver: android.content.BroadcastReceiver? = null
    private var notificationUpdateJob: Job? = null

    // Track the recording job to ensure it completes before worker starts
    private var recordingJob: Job? = null

    // Track chunk transcription jobs to ensure they complete before worker starts
    private val chunkTranscriptionJobs = mutableListOf<Job>()

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "ACTION_STOP"
        private const val ACTION_PAUSE = "ACTION_PAUSE"
        private const val ACTION_RESUME = "ACTION_RESUME"

        private const val SAMPLE_RATE = 16000  // 16kHz - optimal for Whisper API
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val CHUNK_DURATION_MS = 30_000L // 30 seconds
        private const val OVERLAP_DURATION_MS = 2_000L // 2 seconds overlap
        private const val SILENCE_THRESHOLD = 500
        private const val SILENCE_DETECTION_DURATION = 10_000L // 10 seconds
        private const val MIN_STORAGE_BYTES = 100 * 1024 * 1024L // 100 MB
    }

    inner class RecordingBinder : Binder() {
        fun getService(): RecordingService = this@RecordingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        setupPhoneStateListener()
        setupAudioFocusListener()
        setupHeadsetReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.d("RecordingService", "Stop action received")
                serviceScope.launch {
                    stopRecording()
                }
            }
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
        }
        return START_STICKY
    }

    private fun setupPhoneStateListener() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        phoneStateListener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                when (state) {
                    TelephonyManager.CALL_STATE_RINGING,
                    TelephonyManager.CALL_STATE_OFFHOOK -> {
                        if (isRecording && !isPaused) {
                            pauseRecording("Paused - Phone call")
                        }
                    }
                    TelephonyManager.CALL_STATE_IDLE -> {
                        if (isRecording && isPaused) {
                            resumeRecording()
                        }
                    }
                }
            }
        }
        telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    private fun setupAudioFocusListener() {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    if (isRecording && !isPaused) {
                        pauseRecording("Paused - Audio focus lost")
                    }
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    if (isRecording && isPaused) {
                        resumeRecording()
                    }
                }
            }
        }
    }

    private fun setupHeadsetReceiver() {
        headsetReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (!isRecording) return  // Only notify if actively recording

                when (intent?.action) {
                    AudioManager.ACTION_HEADSET_PLUG -> {
                        val state = intent.getIntExtra("state", -1)
                        val name = intent.getStringExtra("name") ?: "headset"

                        if (state == 1) {
                            // Headset plugged in
                            updateNotification("Microphone source changed - $name connected", isRecording)
                            Log.d("RecordingService", "Headset connected: $name")
                        } else if (state == 0) {
                            // Headset unplugged
                            updateNotification("Microphone source changed - $name disconnected", isRecording)
                            Log.d("RecordingService", "Headset disconnected: $name")
                        }

                        // Continue recording - no need to restart AudioRecord
                    }

                    AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED -> {
                        val state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)

                        when (state) {
                            AudioManager.SCO_AUDIO_STATE_CONNECTED -> {
                                updateNotification("Microphone source changed - Bluetooth connected", isRecording)
                                Log.d("RecordingService", "Bluetooth headset connected")
                            }
                            AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> {
                                updateNotification("Microphone source changed - Bluetooth disconnected", isRecording)
                                Log.d("RecordingService", "Bluetooth headset disconnected")
                            }
                        }

                        // Continue recording - AudioRecord handles routing automatically
                    }
                }
            }
        }

        val filter = android.content.IntentFilter().apply {
            addAction(AudioManager.ACTION_HEADSET_PLUG)
            addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
        }
        registerReceiver(headsetReceiver, filter)
        Log.d("RecordingService", "Headset receiver registered")
    }

    suspend fun startRecording() {
        if (isRecording) return

        // Check storage
        if (!hasEnoughStorage()) {
            _recordingState.value = RecordingState.Error("Recording stopped - Low storage")
            return
        }

        // Request audio focus
        val result = audioManager?.requestAudioFocus(
            audioFocusListener,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN
        )

        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            _recordingState.value = RecordingState.Error("Could not gain audio focus")
            return
        }

        try {
            // Create recording in database
            currentRecordingId = repository.createRecording()
            chunkIndex = 0
            recordingStartTime = System.currentTimeMillis()
            elapsedTime = 0L

            // Start foreground service
            startForeground()

            isRecording = true
            isPaused = false

            _recordingState.value = RecordingState.Recording(
                recordingId = currentRecordingId,
                elapsedTime = 0L,
                status = "Recording..."
            )

            // Start recording in background
            serviceScope.launch {
                startAudioRecording()
            }

            // Start timer
            startTimer()

        } catch (e: Exception) {
            _recordingState.value = RecordingState.Error(e.message ?: "Failed to start recording")
        }
    }

    private fun startForeground() {
        val notification = createNotification("Recording...", true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startAudioRecording() {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            _recordingState.value = RecordingState.Error("Microphone permission not granted")
            return
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,  // Better for speech, includes noise suppression
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            _recordingState.value = RecordingState.Error("Failed to initialize AudioRecord")
            return
        }

        audioRecord?.startRecording()

        recordingJob = serviceScope.launch {
            recordAudioChunks(bufferSize)
        }
    }

    private suspend fun recordAudioChunks(bufferSize: Int) {
        val buffer = ShortArray(bufferSize)
        chunkStartTime = System.currentTimeMillis()
        var currentChunkData = mutableListOf<Short>()
        var lastChunkOverlap = mutableListOf<Short>()
        var silenceStartTime: Long? = null

        while (isRecording) {
            if (isPaused) {
                delay(100)
                continue
            }

            val readResult = audioRecord?.read(buffer, 0, bufferSize) ?: -1
            if (readResult > 0) {
                // Check for silence
                val isSilent = isSilent(buffer, readResult)

                if (isSilent) {
                    if (silenceStartTime == null) {
                        silenceStartTime = System.currentTimeMillis()
                    } else if (System.currentTimeMillis() - silenceStartTime!! > SILENCE_DETECTION_DURATION) {
                        withContext(Dispatchers.Main) {
                            _recordingState.value = RecordingState.Recording(
                                recordingId = currentRecordingId,
                                elapsedTime = getElapsedTime(),
                                status = "No audio detected - Check microphone"
                            )
                            updateNotification("No audio detected - Check microphone", true)
                        }
                    }
                } else {
                    silenceStartTime = null
                    withContext(Dispatchers.Main) {
                        if (_recordingState.value is RecordingState.Recording) {
                            val current = _recordingState.value as RecordingState.Recording
                            if (current.status.contains("No audio detected")) {
                                _recordingState.value = current.copy(status = "Recording...")
                                updateNotification("Recording...", true)
                            }
                        }
                    }
                }

                // Add to current chunk
                currentChunkData.addAll(buffer.take(readResult))

                // Check if chunk duration reached
                val chunkDuration = System.currentTimeMillis() - chunkStartTime
                if (chunkDuration >= CHUNK_DURATION_MS) {
                    // Save chunk with overlap from previous chunk
                    val fullChunkData = lastChunkOverlap + currentChunkData
                    saveAudioChunk(fullChunkData.toShortArray(), chunkStartTime, System.currentTimeMillis())

                    // Calculate overlap (last 2 seconds of current chunk)
                    val overlapSamples = (SAMPLE_RATE * (OVERLAP_DURATION_MS / 1000.0)).toInt()
                    lastChunkOverlap = if (currentChunkData.size >= overlapSamples) {
                        currentChunkData.takeLast(overlapSamples).toMutableList()
                    } else {
                        currentChunkData.toMutableList()
                    }

                    // Reset for next chunk
                    currentChunkData.clear()
                    chunkStartTime = System.currentTimeMillis()
                    chunkIndex++

                    // Check storage before next chunk
                    if (!hasEnoughStorage()) {
                        withContext(Dispatchers.Main) {
                            stopRecordingInternal()
                            _recordingState.value = RecordingState.Error("Recording stopped - Low storage")
                        }
                        break
                    }
                }
            }
        }

        // Save final chunk if any data remains
        if (currentChunkData.isNotEmpty()) {
            val fullChunkData = lastChunkOverlap + currentChunkData
            saveAudioChunk(fullChunkData.toShortArray(), chunkStartTime, System.currentTimeMillis())
        }
    }

    private fun isSilent(buffer: ShortArray, readSize: Int): Boolean {
        var sum = 0.0
        for (i in 0 until readSize) {
            sum += abs(buffer[i].toInt())
        }
        val average = sum / readSize
        return average < SILENCE_THRESHOLD
    }

    private suspend fun saveAudioChunk(audioData: ShortArray, startTime: Long, endTime: Long) {
        val audioDir = getAudioDirectory()
        val fileName = "recording_${currentRecordingId}_chunk_${chunkIndex}.pcm"
        val file = File(audioDir, fileName)

        try {
            FileOutputStream(file).use { fos ->
                val buffer = ByteArray(audioData.size * 2)
                for (i in audioData.indices) {
                    buffer[i * 2] = (audioData[i].toInt() and 0xFF).toByte()
                    buffer[i * 2 + 1] = (audioData[i].toInt() shr 8).toByte()
                }
                fos.write(buffer)
            }

            // Save chunk to database
            val chunk = AudioChunk(
                recordingId = currentRecordingId,
                chunkIndex = chunkIndex,
                filePath = file.absolutePath,
                startTime = startTime,
                endTime = endTime,
                duration = endTime - startTime
            )
            val chunkId = repository.insertAudioChunk(chunk)

            // Start transcription for this chunk and track the job
            val transcriptionJob = serviceScope.launch {
                try {
                    repository.transcribeChunk(chunk.copy(id = chunkId))
                } catch (e: Exception) {
                    // Log error but continue recording
                }
            }
            chunkTranscriptionJobs.add(transcriptionJob)

        } catch (e: Exception) {
            // Handle error
        }
    }

    private fun getAudioDirectory(): File {
        val dir = File(filesDir, "audio_recordings")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun hasEnoughStorage(): Boolean {
        val path = filesDir
        val stat = StatFs(path.absolutePath)
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
        return availableBytes > MIN_STORAGE_BYTES
    }

    fun pauseRecording(reason: String = "Paused") {
        if (!isRecording || isPaused) return

        // Update elapsed time BEFORE setting isPaused to true
        elapsedTime += System.currentTimeMillis() - recordingStartTime
        isPaused = true
        pauseStartTime = System.currentTimeMillis()

        serviceScope.launch {
            repository.updateRecordingStatus(currentRecordingId, RecordingStatus.PAUSED)
        }

        _recordingState.value = RecordingState.Paused(
            recordingId = currentRecordingId,
            elapsedTime = elapsedTime,  // Use the updated elapsedTime directly
            reason = reason
        )

        // Update notification with timer
        val formattedTime = formatElapsedTime(elapsedTime)
        updateNotification("$reason $formattedTime", false)
    }

    fun resumeRecording() {
        if (!isRecording || !isPaused) return

        // Don't add to elapsedTime here, it's already accumulated
        isPaused = false
        recordingStartTime = System.currentTimeMillis()  // Reset start time for new recording segment

        serviceScope.launch {
            repository.updateRecordingStatus(currentRecordingId, RecordingStatus.RECORDING)
        }

        _recordingState.value = RecordingState.Recording(
            recordingId = currentRecordingId,
            elapsedTime = getElapsedTime(),
            status = "Recording..."
        )

        // Update notification with timer (timer job will continue updating)
        val formattedTime = formatElapsedTime(getElapsedTime())
        updateNotification("Recording $formattedTime", true)
    }

    private fun stopRecordingInternal() {
        if (!isRecording) return

        isRecording = false
        isPaused = false

        // Cancel notification updates
        notificationUpdateJob?.cancel()

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        audioManager?.abandonAudioFocus(audioFocusListener)

        // Set stopped state FIRST before launching coroutine
        _recordingState.value = RecordingState.Stopped(
            recordingId = currentRecordingId,
            duration = getElapsedTime()
        )

        // Launch background tasks
        serviceScope.launch {
            try {
                // First, wait for the recording job to complete (this ensures the final chunk is saved)
                Log.d("RecordingService", "Waiting for recording job to complete")
                recordingJob?.join()
                Log.d("RecordingService", "Recording job completed")

                // Now wait for all chunk transcriptions to complete
                Log.d("RecordingService", "Waiting for ${chunkTranscriptionJobs.size} chunk transcription jobs to complete")
                chunkTranscriptionJobs.joinAll()
                Log.d("RecordingService", "All chunk transcriptions completed")

                repository.stopRecording(currentRecordingId)

                // Enqueue worker to handle transcription and summary generation
                // The worker will handle the complete flow: transcription -> summary
                Log.d("RecordingService", "Enqueueing TranscriptionWorker for recording $currentRecordingId")
                enqueueTranscriptionWorker(currentRecordingId, shouldFinalizeChunk = true)
                Log.d("RecordingService", "Enqueued transcription worker for recording $currentRecordingId")

                // Clear the jobs list
                chunkTranscriptionJobs.clear()
                recordingJob = null
            } catch (e: Exception) {
                Log.e("RecordingService", "Error in stop recording background task", e)
            } finally {
                // Only NOW stop the service, after all work is complete
                // Delay to allow UI to navigate (2.5s for navigation + buffer)
                delay(2500)
                Log.d("RecordingService", "Stopping foreground service")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    suspend fun stopRecording() {
        Log.d("RecordingService", "stopRecording() called, isRecording=$isRecording")
        if (!isRecording) {
            Log.d("RecordingService", "Already stopped, ignoring duplicate call")
            return
        }
        withContext(Dispatchers.Main) {
            stopRecordingInternal()
        }
    }

    private fun startTimer() {
        // Cancel any existing timer
        notificationUpdateJob?.cancel()

        // Start new timer job for live updates
        notificationUpdateJob = serviceScope.launch {
            while (isRecording && isActive) {
                delay(1000) // Update every second
                if (!isPaused) {
                    val elapsed = getElapsedTime()
                    val formattedTime = formatElapsedTime(elapsed)

                    // Update state
                    _recordingState.value = RecordingState.Recording(
                        recordingId = currentRecordingId,
                        elapsedTime = elapsed,
                        status = "Recording..."
                    )

                    // Update notification with live timer
                    updateNotification("Recording $formattedTime", true)
                } else {
                    // Update paused notification
                    val elapsed = getElapsedTime()
                    val formattedTime = formatElapsedTime(elapsed)
                    updateNotification("Paused $formattedTime", false)
                }
            }
        }
    }

    private fun formatElapsedTime(elapsed: Long): String {
        val minutes = (elapsed / 1000) / 60
        val seconds = (elapsed / 1000) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun getElapsedTime(): Long {
        return if (isPaused) {
            elapsedTime
        } else {
            elapsedTime + (System.currentTimeMillis() - recordingStartTime)
        }
    }

    private fun createNotification(status: String, isRecording: Boolean): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, RecordingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, VoiceRecorderApplication.RECORDING_CHANNEL_ID)
            .setContentTitle("Voice Recorder")
            .setContentText(status)  // Shows "Recording 01:23" or "Paused 01:23"
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(R.drawable.ic_launcher_foreground, "Stop", stopPendingIntent)

        if (!isRecording && isPaused) {
            val resumeIntent = Intent(this, RecordingService::class.java).apply {
                action = ACTION_RESUME
            }
            val resumePendingIntent = PendingIntent.getService(
                this, 2, resumeIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(R.drawable.ic_launcher_foreground, "Resume", resumePendingIntent)
        }

        return builder.build()
    }

    private fun updateNotification(status: String, isRecording: Boolean) {
        val notification = createNotification(status, isRecording)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()

        // Enqueue transcription worker if recording was interrupted
        if (isRecording) {
            enqueueTranscriptionWorker(currentRecordingId, shouldFinalizeChunk = true)
        }

        // Cancel notification updates
        notificationUpdateJob?.cancel()

        telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
        audioManager?.abandonAudioFocus(audioFocusListener)

        // Unregister headset receiver
        headsetReceiver?.let {
            try {
                unregisterReceiver(it)
                Log.d("RecordingService", "Headset receiver unregistered")
            } catch (e: IllegalArgumentException) {
                Log.w("RecordingService", "Receiver already unregistered")
            }
        }

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        serviceScope.cancel()
    }

    private fun enqueueTranscriptionWorker(recordingId: Long, shouldFinalizeChunk: Boolean) {
        val inputData = Data.Builder()
            .putLong(TranscriptionWorker.KEY_RECORDING_ID, recordingId)
            .putBoolean(TranscriptionWorker.KEY_FINALIZE_CHUNK, shouldFinalizeChunk)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<TranscriptionWorker>()
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(this)
            .enqueueUniqueWork(
                "transcription_$recordingId",
                ExistingWorkPolicy.KEEP, // Keep existing work if already enqueued
                workRequest
            )

        Log.d("RecordingService", "Enqueued transcription worker for recording $recordingId")
    }
}

sealed class RecordingState {
    object Idle : RecordingState()

    data class Recording(
        val recordingId: Long,
        val elapsedTime: Long,
        val status: String
    ) : RecordingState()

    data class Paused(
        val recordingId: Long,
        val elapsedTime: Long,
        val reason: String
    ) : RecordingState()

    data class Stopped(
        val recordingId: Long,
        val duration: Long
    ) : RecordingState()

    data class Error(val message: String) : RecordingState()
}

