package com.example.voicerecorderai.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.voicerecorderai.domain.model.Recording
import com.example.voicerecorderai.domain.model.RecordingStatus
import com.example.voicerecorderai.ui.viewmodel.DashboardViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToRecording: () -> Unit,
    onNavigateToSummary: (Long) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val recordings by viewModel.recordings.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voice Recorder AI") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToRecording,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Filled.Mic, contentDescription = "Start Recording")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading && recordings.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (recordings.isEmpty()) {
                EmptyStateView(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                RecordingsList(
                    recordings = recordings,
                    onRecordingClick = onNavigateToSummary,
                    onDeleteRecording = { viewModel.deleteRecording(it) }
                )
            }
        }
    }
}

@Composable
fun EmptyStateView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No recordings yet",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tap the microphone button to start recording",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
    }
}

@Composable
fun RecordingsList(
    recordings: List<Recording>,
    onRecordingClick: (Long) -> Unit,
    onDeleteRecording: (Recording) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(recordings, key = { it.id }) { recording ->
            RecordingCard(
                recording = recording,
                onClick = { onRecordingClick(recording.id) },
                onDelete = { onDeleteRecording(recording) }
            )
        }
    }
}

@Composable
fun RecordingCard(
    recording: Recording,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(getStatusColor(recording.status).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getStatusIcon(recording.status),
                    contentDescription = null,
                    tint = getStatusColor(recording.status)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Recording info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recording.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatDate(recording.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatDuration(recording.duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    StatusChip(status = recording.status)
                }
            }

            // Delete button
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete recording",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Recording") },
            text = { Text("Are you sure you want to delete this recording?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun StatusChip(status: RecordingStatus) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = getStatusColor(status).copy(alpha = 0.2f)
    ) {
        Text(
            text = getStatusText(status),
            style = MaterialTheme.typography.labelSmall,
            color = getStatusColor(status),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun getStatusColor(status: RecordingStatus): androidx.compose.ui.graphics.Color {
    return when (status) {
        RecordingStatus.RECORDING -> MaterialTheme.colorScheme.error
        RecordingStatus.PAUSED -> MaterialTheme.colorScheme.tertiary
        RecordingStatus.STOPPED -> MaterialTheme.colorScheme.secondary
        RecordingStatus.TRANSCRIBING -> MaterialTheme.colorScheme.primary
        RecordingStatus.TRANSCRIBED -> MaterialTheme.colorScheme.primary
        RecordingStatus.SUMMARIZING -> MaterialTheme.colorScheme.primary
        RecordingStatus.COMPLETED -> MaterialTheme.colorScheme.primary
        RecordingStatus.ERROR -> MaterialTheme.colorScheme.error
    }
}

@Composable
fun getStatusIcon(status: RecordingStatus): androidx.compose.ui.graphics.vector.ImageVector {
    return when (status) {
        RecordingStatus.RECORDING -> Icons.Filled.RadioButtonChecked
        RecordingStatus.PAUSED -> Icons.Filled.Pause
        RecordingStatus.STOPPED -> Icons.Filled.Stop
        RecordingStatus.TRANSCRIBING -> Icons.Filled.Mic
        RecordingStatus.TRANSCRIBED -> Icons.Filled.Mic
        RecordingStatus.SUMMARIZING -> Icons.Filled.Mic
        RecordingStatus.COMPLETED -> Icons.Default.CheckCircle
        RecordingStatus.ERROR -> Icons.Filled.RadioButtonChecked
    }
}

fun getStatusText(status: RecordingStatus): String {
    return when (status) {
        RecordingStatus.RECORDING -> "Recording"
        RecordingStatus.PAUSED -> "Paused"
        RecordingStatus.STOPPED -> "Stopped"
        RecordingStatus.TRANSCRIBING -> "Transcribing"
        RecordingStatus.TRANSCRIBED -> "Transcribed"
        RecordingStatus.SUMMARIZING -> "Summarizing"
        RecordingStatus.COMPLETED -> "Completed"
        RecordingStatus.ERROR -> "Error"
    }
}

fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

fun formatDuration(durationMs: Long): String {
    val seconds = durationMs / 1000
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format("%02d:%02d", minutes, remainingSeconds)
}
