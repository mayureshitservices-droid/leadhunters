package com.example.leadhunters.ui.leads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.leadhunters.data.local.entities.Lead
import com.example.leadhunters.data.repository.CallRepository
import com.example.leadhunters.data.repository.WorkRepository
import com.example.leadhunters.util.AnalyticsHelper
import com.example.leadhunters.data.local.dao.TeleCallerDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LeadsUiState(
    val leads: List<LeadWithLog> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedBusinessOwnerId: String? = null,
    val businessOwners: List<BusinessOwnerFilter> = emptyList()
)

data class LeadWithLog(
    val lead: com.example.leadhunters.data.local.entities.Lead,
    val latestLog: com.example.leadhunters.data.local.entities.AppCallLog?,
    val latestOutcome: com.example.leadhunters.data.local.entities.CallOutcome? = null,
    val isSyncing: Boolean = false,
    val hasAnyOutcome: Boolean = false
)

data class BusinessOwnerFilter(
    val id: String,
    val name: String
)

@HiltViewModel
class LeadsViewModel @Inject constructor(
    private val workRepository: WorkRepository,
    private val callRepository: CallRepository,
    private val teleCallerDao: TeleCallerDao,
    private val analyticsHelper: AnalyticsHelper,
    val playbackManager: com.example.leadhunters.ui.logs.CallPlaybackManager,
    private val autoDialManager: com.example.leadhunters.data.system.AutoDialManager
) : ViewModel() {

    private val _selectedBusinessOwnerId = MutableStateFlow<String?>(null)
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
        _selectedBusinessOwnerId,
        _isLoading,
        _error
    ) { args ->
        val leads = args[0] as List<com.example.leadhunters.data.local.entities.Lead>
        val logs = args[1] as List<com.example.leadhunters.data.local.entities.AppCallLog>
        val outcomes = args[2] as List<com.example.leadhunters.data.local.entities.CallOutcome>
        val syncItems = args[3] as List<com.example.leadhunters.data.local.entities.SyncItem>
        val selectedId = args[4] as String?
        val loading = args[5] as Boolean
        val error = args[6] as String?

        val filteredLeads = if (selectedId == null) {
            leads
        } else {
            leads.filter { it.businessOwnerId == selectedId }
        }

        val leadsWithLogs = filteredLeads.map { lead ->
            val leadLogs = logs.filter { it.leadId == lead.id || (it.leadId == "AD_HOC" && it.phoneNumber == lead.phoneNumber) }
            val latestLog = leadLogs.maxByOrNull { it.startTime }
            
            val hasAnyOutcome = leadLogs.any { log -> outcomes.any { it.callLogId == log.id } }
            val latestOutcome = latestLog?.let { log -> outcomes.find { it.callLogId == log.id } }
            
            val isSyncing = latestLog?.let { log ->
                syncItems.any { it.type == "CALL_LOG" && it.referenceId == log.id.toString() && it.status == "PENDING" }
            } ?: false
            
            LeadWithLog(lead, latestLog, latestOutcome, isSyncing, hasAnyOutcome)
        }.filter { !it.hasAnyOutcome } // My Leads: only show leads that have NO outcome submitted yet

        val owners = leads.map { BusinessOwnerFilter(it.businessOwnerId, it.businessOwnerName) }
            .distinctBy { it.id }

        LeadsUiState(
            leads = leadsWithLogs,
            isLoading = loading,
            error = error,
            selectedBusinessOwnerId = selectedId,
            businessOwners = owners
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
                startCall(lead)
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
                        }
                    }
                } else if (!isTransitioning) {
                    // Lead is no longer in the pending list (likely form submitted)
                    // We only trigger next if we were actually tracking this lead
                    lastProcessedLeadId = null
                    lastProcessedLogId = null
                    
                    delay(800) // UI stability delay
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

    fun filterByBusinessOwner(ownerId: String?) {
        _selectedBusinessOwnerId.value = ownerId
        // Safety: Stop auto-dial if filters change
        if (autoDialManager.isActive()) {
            stopAutoDial()
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
            _isLoading.value = true
            _error.value = null
            workRepository.syncLeads()
                .onFailure { _error.value = it.message }
            _isLoading.value = false
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
