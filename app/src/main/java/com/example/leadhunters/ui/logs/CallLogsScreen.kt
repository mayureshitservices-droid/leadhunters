package com.example.leadhunters.ui.logs

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.leadhunters.data.local.entities.AppCallLog
import com.example.leadhunters.service.CallService
import com.example.leadhunters.ui.components.AppBadge
import com.example.leadhunters.ui.components.AppCard
import com.example.leadhunters.ui.theme.SuccessEmerald
import com.example.leadhunters.ui.theme.WarningAmber
import com.example.leadhunters.ui.theme.ErrorCoral
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallLogsScreen(
    viewModel: CallLogsViewModel = hiltViewModel()
) {
    val logs by viewModel.callLogs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var showDialer by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showDialer = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape // Circular FAB for premium feel
            ) {
                Icon(Icons.Default.Dialpad, contentDescription = "Dialer")
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            Text(
                text = "History",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onBackground
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search logs...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = MaterialTheme.shapes.extraLarge,
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    unfocusedBorderColor = Color.Transparent
                )
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(logs) { log ->
                    EnhancedCallLogItem(log = log)
                }
            }
        }

        if (showDialer) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showDialer = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    DialerOverlayContent(
                        onCallInitiated = { number ->
                            scope.launch {
                                viewModel.startCall(number)
                                showDialer = false
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DialerOverlayContent(onCallInitiated: (String) -> Unit) {
    var phoneNumber by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = phoneNumber.ifEmpty { "Enter Number" },
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 16.dp),
            color = if (phoneNumber.isEmpty()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
        )

        DialPadGrid(
            onNumberClick = { if (phoneNumber.length < 15) phoneNumber += it },
            onBackspaceClick = { if (phoneNumber.isNotEmpty()) phoneNumber = phoneNumber.dropLast(1) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (phoneNumber.isNotEmpty()) {
                    onCallInitiated(phoneNumber)
                    
                    val serviceIntent = Intent(context, CallService::class.java).apply {
                        action = CallService.ACTION_START_TRACKING
                        putExtra(CallService.EXTRA_PHONE_NUMBER, phoneNumber)
                    }
                    
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                    } catch (e: Exception) {
                        // Log locally without crashing if OEM restricts foreground services
                        android.util.Log.e("Dialer", "Foreground service blocked", e)
                    }

                    if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        val dialIntent = Intent(Intent.ACTION_CALL).apply {
                            data = Uri.parse("tel:$phoneNumber")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        try {
                            context.startActivity(dialIntent)
                        } catch (e: Exception) {
                            // Defensive fallback
                            val fallback = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                            context.startActivity(fallback)
                        }
                    } else {
                        // Graceful fallback to user input when permission lacks
                        val fallback = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                        context.startActivity(fallback)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = MaterialTheme.shapes.extraLarge,
            enabled = phoneNumber.isNotEmpty()
        ) {
            Icon(Icons.Default.Call, contentDescription = null)
            Spacer(modifier = Modifier.width(12.dp))
            Text("Start Call", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun DialPadGrid(onNumberClick: (String) -> Unit, onBackspaceClick: () -> Unit) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("*", "0", "#")
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { key ->
                    DialButton(key) { onNumberClick(key) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().padding(end = 6.dp)) {
            IconButton(onClick = onBackspaceClick, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.Backspace, contentDescription = "Backspace", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun DialButton(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(56.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = text, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
fun EnhancedCallLogItem(log: AppCallLog) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    var isPlaying by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    
    DisposableEffect(log.id) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    AppCard(elevation = 1.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = log.phoneNumber,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = dateFormat.format(Date(log.startTime)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                CallStatusBadge(status = log.status)
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = formatDuration(log.duration?.toLong() ?: 0L), style = MaterialTheme.typography.bodyMedium)
                }
                
                if (log.recordingPath != null && File(log.recordingPath).exists()) {
                    IconButton(
                        onClick = {
                            if (isPlaying) {
                                mediaPlayer?.pause()
                                isPlaying = false
                            } else {
                                if (mediaPlayer == null) {
                                    mediaPlayer = MediaPlayer.create(context, Uri.fromFile(File(log.recordingPath))).apply {
                                        setOnCompletionListener { isPlaying = false }
                                    }
                                }
                                mediaPlayer?.start()
                                isPlaying = true
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (isPlaying) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
                    }
                }
            }

            AnimatedVisibility(visible = isPlaying) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp), color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun CallStatusBadge(status: String) {
    val (label, color, icon) = when (status.uppercase()) {
        "ANSWERED", "CONNECTED" -> Triple("Answered", com.example.leadhunters.ui.theme.SuccessEmerald, Icons.AutoMirrored.Filled.CallMade)
        "MISSED" -> Triple("Missed", com.example.leadhunters.ui.theme.ErrorCoral, Icons.AutoMirrored.Filled.CallMissed)
        "REJECTED", "UNANSWERED" -> Triple("Rejected", androidx.compose.ui.graphics.Color.Gray, Icons.Default.Block)
        else -> Triple(status, androidx.compose.ui.graphics.Color.Gray, Icons.AutoMirrored.Filled.HelpOutline)
    }
    
    AppBadge(text = label, icon = icon, backgroundColor = color, contentColor = color)
}

fun formatDuration(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", mins, secs)
}
