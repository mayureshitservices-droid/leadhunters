package com.example.leadhunters.ui.completedleads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.leadhunters.data.repository.CallRepository
import com.example.leadhunters.data.repository.WorkRepository
import com.example.leadhunters.data.local.dao.TeleCallerDao
import com.example.leadhunters.data.local.entities.AppCallLog
import com.example.leadhunters.data.local.entities.Lead
import com.example.leadhunters.data.local.entities.ProcessedLead
import com.example.leadhunters.ui.components.CallStatusBadge
import com.example.leadhunters.ui.components.LeadInsightsSection
import com.example.leadhunters.ui.components.CallStatusBadge
import com.example.leadhunters.ui.components.formatDuration
import com.example.leadhunters.ui.components.formatDurationMs
import com.example.leadhunters.ui.components.formatTimestamp
import com.example.leadhunters.ui.leads.LeadWithLog
import com.example.leadhunters.ui.logs.CallPlaybackManager
import com.example.leadhunters.ui.logs.PlaybackState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

// ─── ViewModel ───────────────────────────────────────────────────────────────

data class CompletedLeadsUiState(
    val leads: List<LeadWithLog> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class CompletedLeadsViewModel @Inject constructor(
    private val workRepository: WorkRepository,
    private val callRepository: CallRepository,
    private val teleCallerDao: TeleCallerDao,
    val playbackManager: CallPlaybackManager
) : ViewModel() {

    val playbackState = playbackManager.state

    val uiState: StateFlow<CompletedLeadsUiState> = combine(
        callRepository.getProcessedLeads(),
        callRepository.getCallLogs(System.currentTimeMillis() - (14L * 24 * 60 * 60 * 1000)),
        callRepository.getAllOutcomes(),
        teleCallerDao.getPendingSyncItems()
    ) { processedLeads, logs, outcomes, syncItems ->
        val outcomeMap = outcomes.associateBy { it.callLogId }

        val entries = processedLeads.map { pl ->
            val outcome = outcomeMap[pl.callLogId]
            val log = logs.find { it.id == pl.callLogId } ?: AppCallLog(
                id = pl.callLogId,
                leadId = pl.leadId,
                phoneNumber = pl.phoneNumber,
                startTime = pl.callTimestamp,
                duration = pl.duration,
                type = "OUTGOING",
                status = pl.callStatus,
                recordingPath = pl.recordingPath
            )

            val lead = Lead(
                id = pl.leadId,
                name = pl.name,
                phoneNumber = pl.phoneNumber,
                businessOwnerId = "",
                campaignName = pl.campaignName,
                additionalData = pl.additionalData
            )

            val isSyncing = syncItems.any {
                it.type == "CALL_LOG" && it.referenceId == pl.callLogId.toString() && it.status == "PENDING"
            }

            LeadWithLog(lead, log, outcome, isSyncing, hasAnyOutcome = outcome != null)
        }.sortedByDescending { it.latestLog?.startTime ?: 0L }

        CompletedLeadsUiState(leads = entries)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CompletedLeadsUiState())

    fun togglePlayback(logId: Long, filePath: String) {
        playbackManager.togglePlayback(logId, filePath)
    }

    override fun onCleared() {
        super.onCleared()
        playbackManager.stop()
    }
}

// ─── Screen ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompletedLeadsScreen(
    onWhatsAppClick: (String) -> Unit,
    viewModel: CompletedLeadsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Logs", fontWeight = FontWeight.Bold) },
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
            if (uiState.leads.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No completed calls yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Leads will appear here after the outcome form is filled",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(uiState.leads, key = { it.latestLog?.id ?: it.lead.id }) { leadWithLog ->
                        CompletedLeadCard(
                            item = leadWithLog,
                            playbackState = playbackState,
                            onPlaybackToggle = { id, path -> viewModel.togglePlayback(id, path) },
                            onWhatsAppClick = { onWhatsAppClick(leadWithLog.lead.phoneNumber) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CompletedLeadCard(
    item: LeadWithLog,
    playbackState: PlaybackState,
    onPlaybackToggle: (Long, String) -> Unit,
    onWhatsAppClick: () -> Unit
) {
    val lead = item.lead
    val latestLog = item.latestLog
    val outcome = item.latestOutcome
    val hasRecording = latestLog?.recordingPath != null && File(latestLog.recordingPath).exists()
    val isCurrentlyPlaying = playbackState.currentLogId == latestLog?.id && playbackState.isPlaying

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            // Header row: name + outcome badge
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
                            Text(text = lead.campaignName, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Outcome badge top-right (or call status if no outcome yet)
                CallStatusBadge(status = outcome?.outcomeType ?: item.latestLog?.status ?: "PENDING")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Phone number row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = lead.phoneNumber, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
            }

            LeadInsightsSection(
                additionalData = lead.additionalData,
                modifier = Modifier.padding(top = 8.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Call timestamp
            latestLog?.let { log ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = formatTimestamp(log.startTime), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Call duration row
            latestLog?.let { log ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = formatDuration(log.duration?.toLong() ?: 0L), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Outcome details card (only if outcome exists)
            if (outcome != null) {
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

            Spacer(modifier = Modifier.height(14.dp))

            // Action buttons: playback + WhatsApp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { latestLog?.let { log -> log.recordingPath?.let { path -> onPlaybackToggle(log.id, path) } } },
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

                Button(
                    onClick = onWhatsAppClick,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }

            // Playback progress
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
