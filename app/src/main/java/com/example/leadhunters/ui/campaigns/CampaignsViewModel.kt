package com.example.leadhunters.ui.campaigns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.leadhunters.data.remote.model.CampaignDto
import com.example.leadhunters.data.repository.WorkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CampaignsUiState(
    val campaigns: List<CampaignDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isClaiming: Boolean = false
)

@HiltViewModel
class CampaignsViewModel @Inject constructor(
    private val workRepository: WorkRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CampaignsUiState())
    val uiState: StateFlow<CampaignsUiState> = _uiState.asStateFlow()

    private val _claimEvent = Channel<Result<Int>>()
    val claimEvent = _claimEvent.receiveAsFlow()

    init {
        fetchCampaigns()
    }

    fun fetchCampaigns() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            workRepository.getCampaigns().collect { result ->
                result.onSuccess { campaigns ->
                    _uiState.update { it.copy(campaigns = campaigns, isLoading = false) }
                }.onFailure { error ->
                    _uiState.update { it.copy(error = error.message, isLoading = false) }
                }
            }
        }
    }

    fun claimCampaign(campaignName: String) {
        if (_uiState.value.isClaiming) return
        viewModelScope.launch {
            _uiState.update { it.copy(isClaiming = true) }
            val result = workRepository.claimCampaign(campaignName)
            if (result.isSuccess) {
                _claimEvent.send(Result.success(result.getOrDefault(0)))
            } else {
                _claimEvent.send(Result.failure(result.exceptionOrNull() ?: Exception("Unknown error")))
            }
            _uiState.update { it.copy(isClaiming = false) }
        }
    }
}
