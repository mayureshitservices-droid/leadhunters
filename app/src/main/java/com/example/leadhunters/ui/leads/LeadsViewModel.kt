package com.example.leadhunters.ui.leads

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.leadhunters.data.local.entities.Lead
import com.example.leadhunters.data.repository.CallRepository
import com.example.leadhunters.data.repository.WorkRepository
import com.example.leadhunters.util.AnalyticsHelper
import com.example.leadhunters.data.local.dao.TeleCallerDao
import com.example.leadhunters.data.system.CallReconciler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LeadsUiState(
    val leads: List<LeadWithLog> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

data class LeadWithLog(
    val lead: com.example.leadhunters.data.local.entities.Lead,
    val latestLog: com.example.leadhunters.data.local.entities.AppCallLog?,
    val latestOutcome: com.example.leadhunters.data.local.entities.CallOutcome? = null,
    val isSyncing: Boolean = false,
    val hasAnyOutcome: Boolean = false
)

@HiltViewModel
class LeadsViewModel @Inject constructor(
    private val workRepository: WorkRepository,
    private val callRepository: CallRepository,
    private val teleCallerDao: TeleCallerDao,
    private val analyticsHelper: AnalyticsHelper,
    private val reconciler: CallReconciler,
    val playbackManager: com.example.leadhunters.ui.logs.CallPlaybackManager,
    private val autoDialManager: com.example.leadhunters.data.system.AutoDialManager
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val playbackState = playbackManager.state
    
    val isAutoDialActive = autoDialManager.isAutoDialActive
    
    private val _autoDialEvent = Channel<com.example.leadhunters.data.local.entities.Lead>(Channel.BUFFERED)
    val autoDialEvent = _autoDialEvent.receiveAsFlow()
    
    private val _autoNavigateEvent = Channel<Long>(Channel.BUFFERED)
    val autoNavigateEvent = _autoNavigateEvent.receiveAsFlow()

    private var lastProcessedLeadId: String? = null
    private var lastProcessedLogId: Long? = null
    private var isTransitioning = false

    val uiState: StateFlow<LeadsUiState> = combine(
        workRepository.getLeads(),
        callRepository.getCallLogs(System.currentTimeMillis() - (14L * 24 * 60 * 60 * 1000)),
        callRepository.getAllOutcomes(),
        teleCallerDao.getPendingSyncItems(),
        _isLoading,
        _error
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val leads = (args.getOrNull(0) as? List<com.example.leadhunters.data.local.entities.Lead>) ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val logs = (args.getOrNull(1) as? List<com.example.leadhunters.data.local.entities.AppCallLog>) ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val outcomes = (args.getOrNull(2) as? List<com.example.leadhunters.data.local.entities.CallOutcome>) ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val syncItems = (args.getOrNull(3) as? List<com.example.leadhunters.data.local.entities.SyncItem>) ?: emptyList()
        val loading = args.getOrNull(4) as? Boolean ?: false
        val error = args.getOrNull(5) as? String

        val leadsWithLogs = leads.map { lead ->
            val leadLogs = logs.filter { it.leadId == lead.id || (it.leadId == "AD_HOC" && it.phoneNumber == lead.phoneNumber) }
            val latestLog = leadLogs.maxByOrNull { it.startTime }
            
            val hasAnyOutcome = leadLogs.any { log -> outcomes.any { it.callLogId == log.id } }
            val latestOutcome = latestLog?.let { log -> outcomes.find { it.callLogId == log.id } }
            
            val isSyncing = latestLog?.let { log ->
                syncItems.any { it.type == "CALL_LOG" && it.referenceId == log.id.toString() && it.status == "PENDING" }
            } ?: false
            
            LeadWithLog(lead, latestLog, latestOutcome, isSyncing, hasAnyOutcome)
        }.filter { !it.hasAnyOutcome }

        LeadsUiState(
            leads = leadsWithLogs,
            isLoading = loading,
            error = error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LeadsUiState())

    init {
        refreshLeads()
        observeAutoDialEvents()
        observeAutoDialProgress()
    }

    private fun observeAutoDialEvents() {
        viewModelScope.launch {
            autoDialManager.autoDialEvents.collect { lead ->
                isTransitioning = true
                lastProcessedLeadId = lead.id
                lastProcessedLogId = null
                delay(800)
                _autoDialEvent.send(lead)
                isTransitioning = false
            }
        }
    }

    private fun observeAutoDialProgress() {
        viewModelScope.launch {
            uiState.collect { state ->
                if (!autoDialManager.isActive()) return@collect

                val currentLeadId = lastProcessedLeadId ?: return@collect
                val currentLeadWithLog = state.leads.find { it.lead.id == currentLeadId }
                
                if (currentLeadWithLog != null) {
                    val latestLog = currentLeadWithLog.latestLog ?: return@collect

                    if (latestLog.id != lastProcessedLogId && !isTransitioning) {
                        if (latestLog.status == "ANSWERED") {
                            lastProcessedLogId = latestLog.id
                            _autoNavigateEvent.send(latestLog.id)
                        } else {
                            lastProcessedLogId = latestLog.id
                            delay(800)
                            triggerNextAutoDial()
                        }
                    }
                } else if (!isTransitioning && lastProcessedLeadId != null) {
                    lastProcessedLeadId = null
                    lastProcessedLogId = null
                    
                    delay(800)
                    triggerNextAutoDial()
                }
            }
        }
    }

    fun stopAutoDial() {
        autoDialManager.stopAutoDial()
        lastProcessedLeadId = null
        lastProcessedLogId = null
        isTransitioning = false
    }

    fun toggleAutoDial() {
        if (autoDialManager.isActive()) {
            stopAutoDial()
        } else {
            val currentLeads = uiState.value.leads.map { it.lead }
            if (currentLeads.isNotEmpty()) {
                autoDialManager.startAutoDial(currentLeads)
                triggerNextAutoDial()
            }
        }
    }

    private fun triggerNextAutoDial() {
        if (isTransitioning) return
        
        viewModelScope.launch {
            isTransitioning = true
            val nextLead = autoDialManager.getNextLead()
            if (nextLead != null) {
                // IMPORTANT: Create the database record first
                startCall(nextLead)
                
                lastProcessedLeadId = nextLead.id
                lastProcessedLogId = null
                delay(800) // Small delay for UI stability
                _autoDialEvent.send(nextLead)
            } else {
                stopAutoDial()
            }
            isTransitioning = false
        }
    }

    fun refreshLeads() {
        viewModelScope.launch {
            performReconciliationSweep()
        }
    }

    private suspend fun performReconciliationSweep() {
        try {
            val pendingLogs = callRepository.getAllUnreconciledLogs()
            if (pendingLogs.isNotEmpty()) {
                pendingLogs.forEach { log ->
                    // Reconcile each stuck log in the background
                    reconciler.reconcile(log.phoneNumber, log.leadId)
                }
            }
        } catch (e: Exception) {
            Log.e("LeadsViewModel", "ReconciliationSweep failed", e)
        }
    }

    suspend fun startCall(lead: Lead): Long {
        analyticsHelper.logCallStarted(lead.id, lead.phoneNumber)
        return callRepository.startCall(lead.id, lead.phoneNumber)
    }

    fun togglePlayback(logId: Long, filePath: String) {
        playbackManager.togglePlayback(logId, filePath)
    }

    override fun onCleared() {
        super.onCleared()
        playbackManager.stop()
    }

    companion object {
        private const val AUTO_DIAL_DELAY_MS = 5000L
    }
}
