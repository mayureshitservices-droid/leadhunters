package com.example.leadhunters.ui.leads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.leadhunters.data.local.entities.Lead
import com.example.leadhunters.data.repository.CallRepository
import com.example.leadhunters.data.repository.WorkRepository
import com.example.leadhunters.util.AnalyticsHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LeadsUiState(
    val leads: List<Lead> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedBusinessOwnerId: String? = null,
    val businessOwners: List<BusinessOwnerFilter> = emptyList()
)

data class BusinessOwnerFilter(
    val id: String,
    val name: String
)

@HiltViewModel
class LeadsViewModel @Inject constructor(
    private val workRepository: WorkRepository,
    private val callRepository: CallRepository,
    private val analyticsHelper: AnalyticsHelper
) : ViewModel() {

    private val _selectedBusinessOwnerId = MutableStateFlow<String?>(null)
    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<LeadsUiState> = combine(
        workRepository.getLeads(),
        _selectedBusinessOwnerId,
        _isLoading,
        _error
    ) { leads, selectedId, loading, error ->
        val filteredLeads = if (selectedId == null) {
            leads
        } else {
            leads.filter { it.businessOwnerId == selectedId }
        }

        val owners = leads.map { BusinessOwnerFilter(it.businessOwnerId, it.businessOwnerName) }
            .distinctBy { it.id }

        LeadsUiState(
            leads = filteredLeads,
            isLoading = loading,
            error = error,
            selectedBusinessOwnerId = selectedId,
            businessOwners = owners
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LeadsUiState())

    init {
        refreshLeads()
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

    fun filterByBusinessOwner(ownerId: String?) {
        _selectedBusinessOwnerId.value = ownerId
    }

    suspend fun startCall(lead: Lead): Long {
        analyticsHelper.logCallStarted(lead.id, lead.phoneNumber)
        return callRepository.startCall(lead.id, lead.phoneNumber)
    }

    fun syncCallLog(leadId: String, duration: Int, status: String, notes: String?) {
        viewModelScope.launch {
            workRepository.syncCallLog(leadId, duration, status, notes)
        }
    }
}
