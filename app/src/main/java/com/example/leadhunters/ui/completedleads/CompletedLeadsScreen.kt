package com.example.leadhunters.ui.completedleads

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.leadhunters.data.local.entities.AppCallLog
import com.example.leadhunters.data.local.entities.CallOutcome
import com.example.leadhunters.data.local.entities.Lead
import com.example.leadhunters.data.local.entities.SyncItem
import com.example.leadhunters.data.repository.CallRepository
import com.example.leadhunters.data.repository.WorkRepository
import com.example.leadhunters.data.local.dao.TeleCallerDao
import com.example.leadhunters.ui.components.CallStatusBadge
import com.example.leadhunters.ui.components.formatDuration
import com.example.leadhunters.ui.components.formatDurationMs
import com.example.leadhunters.ui.leads.BusinessOwnerFilter
import com.example.leadhunters.ui.leads.LeadWithLog
import com.example.leadhunters.ui.logs.CallPlaybackManager
import com.example.leadhunters.ui.logs.PlaybackState
import com.example.leadhunters.ui.theme.PrimaryRed
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

// ─── ViewModel ───────────────────────────────────────────────────────────────

data class CompletedLeadsUiState(
    val leads: List<LeadWithLog> = emptyList(),
    val isLoading: Boolean = false,
    val selectedBusinessOwnerId: String? = null,
    val businessOwners: List<BusinessOwnerFilter> = emptyList()
)

@HiltViewModel
class CompletedLeadsViewModel @Inject constructor(
    private val workRepository: WorkRepository,
    private val callRepository: CallRepository,
    private val teleCallerDao: TeleCallerDao,
    val playbackManager: CallPlaybackManager
) : ViewModel() {

    private val _selectedBusinessOwnerId = MutableStateFlow<String?>(null)

    val playbackState = playbackManager.state

    val uiState: StateFlow<CompletedLeadsUiState> = combine(
        workRepository.getLeads(),
        callRepository.getCallLogs(),
        callRepository.getAllOutcomes(),
        teleCallerDao.getPendingSyncItems(),
        _selectedBusinessOwnerId
    ) { leads, logs, outcomes, syncItems, selectedId ->
        val filteredLeads = if (selectedId == null) leads
        else leads.filter { it.businessOwnerId == selectedId }

        val leadsWithLogs = filteredLeads.map { lead ->
            // Find all logs for this lead
            val leadLogs = logs.filter { it.leadId == lead.id || (it.leadId == "AD_HOC" && it.phoneNumber == lead.phoneNumber) }
            
            // Find the latest log that HAS an outcome
            val latestLogWithOutcome = leadLogs
                .filter { log -> outcomes.any { it.callLogId == log.id } }
                .maxByOrNull { it.startTime }
            
            // For the Logs tab, we only care about leads with at least one outcome
            val outcome = latestLogWithOutcome?.let { log -> outcomes.find { it.callLogId == log.id } }

            val isSyncing = latestLogWithOutcome?.let { log ->
                syncItems.any { it.type == "CALL_LOG" && it.referenceId == log.id.toString() && it.status == "PENDING" }
            } ?: false

            LeadWithLog(lead, latestLogWithOutcome, outcome, isSyncing)
        }.filter { it.latestOutcome != null } 

        val owners = leads.map { BusinessOwnerFilter(it.businessOwnerId, it.businessOwnerName) }
            .distinctBy { it.id }

        CompletedLeadsUiState(
            leads = leadsWithLogs,
            selectedBusinessOwnerId = selectedId,
            businessOwners = owners
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CompletedLeadsUiState())

    fun filterByBusinessOwner(ownerId: String?) {
        _selectedBusinessOwnerId.value = ownerId
    }

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
            // Business Owner Filter Bar
            CompletedLeadsFilterBar(
                owners = uiState.businessOwners,
                selectedId = uiState.selectedBusinessOwnerId,
                onSelect = { viewModel.filterByBusinessOwner(it) }
            )

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
                    items(uiState.leads, key = { it.lead.id }) { leadWithLog ->
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
fun CompletedLeadsFilterBar(
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
fun CompletedLeadCard(
    item: LeadWithLog,
    playbackState: PlaybackState,
    onPlaybackToggle: (Long, String) -> Unit,
    onWhatsAppClick: () -> Unit
) {
    val lead = item.lead
    val latestLog = item.latestLog
    val outcome = item.latestOutcome!!  // guaranteed non-null in this screen
    val hasRecording = latestLog?.recordingPath != null && File(latestLog.recordingPath!!).exists()
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
                            Text(text = lead.businessOwnerName, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Outcome badge top-right
                CallStatusBadge(status = outcome.outcomeType)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Phone number row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = lead.phoneNumber, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Call duration row
            latestLog?.let { log ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = formatDuration(log.duration?.toLong() ?: 0L), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Outcome details card
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

            Spacer(modifier = Modifier.height(14.dp))

            // Action buttons: playback + WhatsApp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
