package com.example.voicerecorderai.ui.screen

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.voicerecorderai.service.RecordingState
import com.example.voicerecorderai.ui.viewmodel.RecordingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSummary: (Long) -> Unit, // New callback for summary navigation
    viewModel: RecordingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val recordingState by viewModel.recordingState.collectAsState()
    val permissionsGranted by viewModel.permissionsGranted.collectAsState()

    val permissions = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.READ_PHONE_STATE)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val allGranted = permissionsMap.values.all { it }
        if (allGranted) {
            viewModel.checkPermissions(context)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.bindService(context)
        viewModel.checkPermissions(context)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.unbindService(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recording") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (!permissionsGranted) {
                PermissionRequiredView(
                    onRequestPermissions = {
                        permissionLauncher.launch(permissions.toTypedArray())
                    },
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                RecordingControlView(
                    recordingState = recordingState,
                    onStartRecording = { viewModel.startRecording(context) },
                    onPauseRecording = { viewModel.pauseRecording() },
                    onResumeRecording = { viewModel.resumeRecording() },
                    onStopRecording = {
                        viewModel.stopRecording()
                    },
                    onNavigateToSummary = onNavigateToSummary // Pass the callback down
                )
            }
        }
    }
}


@Composable
fun PermissionRequiredView(
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Permissions Required",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "This app needs microphone and notification permissions to record audio.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRequestPermissions) {
            Text("Grant Permissions")
        }
    }
}

@Composable
fun RecordingControlView(
    recordingState: RecordingState,
    onStartRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onNavigateToSummary: (Long) -> Unit // Updated callback
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (recordingState) {
            is RecordingState.Idle -> {
                IdleView(onStartRecording = onStartRecording)
            }
            is RecordingState.Recording -> {
                RecordingView(
                    elapsedTime = recordingState.elapsedTime,
                    status = recordingState.status,
                    onPause = onPauseRecording,
                    onStop = onStopRecording
                )
            }
            is RecordingState.Paused -> {
                PausedView(
                    elapsedTime = recordingState.elapsedTime,
                    reason = recordingState.reason,
                    onResume = onResumeRecording,
                    onStop = onStopRecording
                )
            }
            is RecordingState.Stopped -> {
                StoppedView(
                    recordingId = recordingState.recordingId,
                    duration = recordingState.duration,
                    onNavigateToSummary = onNavigateToSummary // Use the new callback
                )
            }
            is RecordingState.Error -> {
                ErrorView(
                    message = recordingState.message,
                    onRetry = onStartRecording
                )
            }
        }
    }
}


@Composable
fun IdleView(onStartRecording: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "Ready to Record",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Tap the button to start recording",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(32.dp))

        RecordButton(onClick = onStartRecording)
    }
}

@Composable
fun RecordingView(
    elapsedTime: Long,
    status: String,
    onPause: () -> Unit,
    onStop: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Animated recording indicator
        AnimatedRecordingIndicator()

        Spacer(modifier = Modifier.height(16.dp))

        // Timer
        Text(
            text = formatElapsedTime(elapsedTime),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        // Status
        StatusCard(status = status)

        Spacer(modifier = Modifier.height(32.dp))

        // Control buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            FloatingActionButton(
                onClick = onPause,
                containerColor = MaterialTheme.colorScheme.tertiary
            ) {
                Icon(Icons.Filled.Pause, contentDescription = "Pause")
            }

            AnimatedStopButton(onClick = onStop)
        }
    }
}

@Composable
fun PausedView(
    elapsedTime: Long,
    reason: String,
    onResume: () -> Unit,
    onStop: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Pause,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.tertiary
        )

        Text(
            text = formatElapsedTime(elapsedTime),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold
        )

        StatusCard(status = reason)

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            FloatingActionButton(
                onClick = onResume,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
            }

            FloatingActionButton(
                onClick = onStop,
                containerColor = MaterialTheme.colorScheme.error
            ) {
                Icon(Icons.Filled.Stop, contentDescription = "Stop")
            }
        }
    }
}

@Composable
fun StoppedView(
    recordingId: Long,
    duration: Long,
    onNavigateToSummary: (Long) -> Unit // Updated callback
) {
    // Navigate to summary view immediately when recording is stopped
    LaunchedEffect(recordingId) {
        if (recordingId > 0) { // Ensure we have a valid recording ID
            onNavigateToSummary(recordingId)
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Saving and Processing...", // Updated text
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Duration: ${formatElapsedTime(duration)}",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "Please wait while we process your recording.", // Updated text
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}


@Composable
fun ErrorView(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.RadioButtonChecked,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.error
        )

        Text(
            text = "Error",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )

        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
fun AnimatedRecordingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "recording")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .size(80.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.error),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.RadioButtonChecked,
            contentDescription = "Recording",
            tint = MaterialTheme.colorScheme.onError,
            modifier = Modifier.size(40.dp)
        )
    }
}

@Composable
fun AnimatedStopButton(onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "stop_button")

    // Subtle pulsing effect - much less bouncy
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.03f,  // Changed from 1.1f to 1.03f for subtle effect
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOut),  // Slower: 1200ms instead of 800ms
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.85f,  // Changed from 0.7f to 0.85f for subtler fade
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOut),  // Slower to match scale
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    FloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.error.copy(alpha = alpha),
        modifier = Modifier
            .size(72.dp)
            .scale(scale)
    ) {
        Icon(
            Icons.Filled.Stop,
            contentDescription = "Stop",
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
fun RecordButton(onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier.size(80.dp),
        containerColor = MaterialTheme.colorScheme.primary
    ) {
        Icon(
            Icons.Filled.Mic,
            contentDescription = "Start Recording",
            modifier = Modifier.size(40.dp)
        )
    }
}

@Composable
fun StatusCard(status: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Text(
            text = status,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

fun formatElapsedTime(timeMs: Long): String {
    val seconds = timeMs / 1000
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format("%02d:%02d", minutes, remainingSeconds)
}
