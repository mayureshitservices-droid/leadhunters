package com.example.leadhunters.ui.leads

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.leadhunters.data.local.entities.Lead
import com.example.leadhunters.data.local.entities.AppCallLog
import com.example.leadhunters.service.CallService
import com.example.leadhunters.ui.components.AppBadge
import com.example.leadhunters.ui.theme.SuccessEmerald
import com.example.leadhunters.ui.logs.PlaybackState
import com.example.leadhunters.ui.components.CallStatusBadge
import com.example.leadhunters.ui.components.formatDuration
import com.example.leadhunters.ui.components.formatDurationMs
import com.example.leadhunters.ui.components.LeadInsightsSection
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeadsScreen(
    onOutcomeClick: (Long) -> Unit,
    onWhatsAppClick: (String) -> Unit,
    viewModel: LeadsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isAutoDialActive by viewModel.isAutoDialActive.collectAsState()

    // Handle Auto-Dial Events
    LaunchedEffect(Unit) {
        viewModel.autoDialEvent.collect { lead ->
            makeCall(context, lead)
        }
    }

    // Handle Auto-Navigation to Outcome
    LaunchedEffect(Unit) {
        viewModel.autoNavigateEvent.collect { callLogId ->
            onOutcomeClick(callLogId)
        }
    }

    // Presence Check: Stop Auto-Dial if user leaves the screen
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            // Removed stopAutoDial from ON_PAUSE to allow persistence during calls
            if (event == androidx.lifecycle.Lifecycle.Event.ON_DESTROY) {
                if (isAutoDialActive) viewModel.stopAutoDial()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Leads", fontWeight = FontWeight.Bold) },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = "Auto-Dial",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isAutoDialActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Switch(
                            checked = isAutoDialActive,
                            onCheckedChange = { viewModel.toggleAutoDial() },
                            modifier = Modifier.scale(0.8f),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = SuccessEmerald,
                                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Business Owner Filter Bar
            BusinessOwnerFilterBar(
                owners = uiState.businessOwners,
                selectedId = uiState.selectedBusinessOwnerId,
                onSelect = { viewModel.filterByBusinessOwner(it) }
            )

            AnimatedVisibility(visible = isAutoDialActive) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Autorenew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "AUTO-DIALING MODE ACTIVE",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.weight(1f)
                        )
                        
                        Surface(
                            onClick = { viewModel.stopAutoDial() },
                            color = Color.White.copy(alpha = 0.2f),
                            shape = CircleShape
                        ) {
                            Text(
                                "STOP", 
                                color = Color.White, 
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            if (uiState.isLoading && uiState.leads.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.leads.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.FilterList, 
                            contentDescription = null, 
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No leads found for this filter",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { viewModel.refreshLeads() }) {
                            Text("Refresh List")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(uiState.leads, key = { it.lead.id }) { leadWithLog ->
                        LeadItemCard(
                            item = leadWithLog,
                            playbackState = playbackState,
                            onCallClick = { 
                                coroutineScope.launch {
                                    viewModel.startCall(leadWithLog.lead)
                                    makeCall(context, leadWithLog.lead) 
                                }
                            },
                            onPlaybackToggle = { id, path -> viewModel.togglePlayback(id, path) },
                            onOutcomeClick = { leadWithLog.latestLog?.id?.let { onOutcomeClick(it) } },
                            onWhatsAppClick = { onWhatsAppClick(leadWithLog.lead.phoneNumber) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BusinessOwnerFilterBar(
    owners: List<BusinessOwnerFilter>,
    selectedId: String?,
    onSelect: (String?) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selectedId == null,
                onClick = { onSelect(null) },
                label = { Text("All Owners") }
            )
        }
        items(owners) { owner ->
            FilterChip(
                selected = selectedId == owner.id,
                onClick = { onSelect(owner.id) },
                label = { Text(owner.name) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Business,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }
    }
}

@Composable
fun LeadItemCard(
    item: LeadWithLog,
    playbackState: PlaybackState,
    onCallClick: () -> Unit,
    onPlaybackToggle: (Long, String) -> Unit,
    onOutcomeClick: () -> Unit,
    onWhatsAppClick: () -> Unit
) {
    val lead = item.lead
    val latestLog = item.latestLog
    val hasLog = latestLog != null
    val hasRecording = latestLog?.recordingPath != null && File(latestLog.recordingPath!!).exists()
    val isCurrentlyPlaying = playbackState.currentLogId == latestLog?.id && playbackState.isPlaying

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp), // More premium rounded corners
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = lead.name,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    if (item.isSyncing) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.dp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Syncing...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Business, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = lead.businessOwnerName, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                FilledIconButton(
                    onClick = onCallClick,
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = SuccessEmerald, contentColor = Color.White)
                ) {
                    Icon(Icons.Default.Call, contentDescription = "Call Now")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = lead.phoneNumber, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
            }

            // New Dynamic Insights Section
            LeadInsightsSection(
                additionalData = lead.additionalData,
                modifier = Modifier.padding(top = 8.dp)
            )

            // Enhanced Call Log Section - Only visible if a call has happened
            AnimatedVisibility(
                visible = hasLog,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = formatDuration(latestLog?.duration?.toLong() ?: 0L), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        
                        latestLog?.status?.let { status ->
                            CallStatusBadge(status = item.latestOutcome?.outcomeType ?: status)
                        }
                    }

                    item.latestOutcome?.let { outcome ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = outcome.customerName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                
                                if (!outcome.remarks.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.Top) {
                                        Icon(Icons.Default.Notes, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = outcome.remarks,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Playback Button
                        Button(
                            onClick = { latestLog?.id?.let { onPlaybackToggle(it, latestLog.recordingPath!!) } },
                            modifier = Modifier.weight(1f),
                            enabled = hasRecording,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = when {
                                    !hasRecording -> MaterialTheme.colorScheme.surfaceVariant
                                    isCurrentlyPlaying -> MaterialTheme.colorScheme.errorContainer
                                    else -> MaterialTheme.colorScheme.tertiaryContainer
                                },
                                contentColor = when {
                                    !hasRecording -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    isCurrentlyPlaying -> MaterialTheme.colorScheme.onErrorContainer
                                    else -> MaterialTheme.colorScheme.onTertiaryContainer
                                }
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(
                                imageVector = when {
                                    !hasRecording -> Icons.Default.MicOff
                                    isCurrentlyPlaying -> Icons.Default.Pause
                                    else -> Icons.Default.PlayArrow
                                },
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Outcome Button
                        Button(
                            onClick = onOutcomeClick,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.Assignment, contentDescription = null, modifier = Modifier.size(20.dp))
                        }

                        // WhatsApp Button
                        Button(
                            onClick = onWhatsAppClick,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    }

                    // Playback Progress
                    AnimatedVisibility(visible = isCurrentlyPlaying) {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                            val progress = if (playbackState.duration > 0) {
                                playbackState.currentPosition.toFloat() / playbackState.duration
                            } else 0f
                            
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = formatDurationMs(playbackState.currentPosition), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(text = formatDurationMs(playbackState.duration), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Removed local formatDuration and formatDurationMs as they are now in CallLogComponents.kt

private fun makeCall(context: Context, lead: Lead) {
    // Defensively start the tracking service
    val serviceIntent = Intent(context, CallService::class.java).apply {
        action = CallService.ACTION_START_TRACKING
        putExtra(CallService.EXTRA_PHONE_NUMBER, lead.phoneNumber)
        putExtra(CallService.EXTRA_LEAD_ID, lead.id) // Pass lead ID for reconciliation
    }
    
    try {
        val phoneToLog = lead.phoneNumber
        Log.d("LeadsScreen", "Starting CallService for: $phoneToLog")
        ContextCompat.startForegroundService(context, serviceIntent)
    } catch (e: Exception) {
        Log.e("LeadsScreen", "Error starting CallService", e)
    }

    val phoneNumber = lead.phoneNumber
    val intent = if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
        Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber"))
    } else {
        Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
    }
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    context.startActivity(intent)
}
