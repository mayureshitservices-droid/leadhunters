package com.example.leadhunters.ui.outcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.leadhunters.data.local.entities.CallOutcome
import com.example.leadhunters.data.repository.CallRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OutcomeViewModel @Inject constructor(
    private val repository: CallRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<OutcomeUiState>(OutcomeUiState.Idle)
    val uiState = _uiState.asStateFlow()

    fun loadCall(callLogId: Long) {
        viewModelScope.launch {
            val log = repository.getLogById(callLogId)
            if (log != null) {
                _uiState.value = OutcomeUiState.Form(log.id, log.leadId, log.phoneNumber)
            }
        }
    }

    fun submitOutcome(callLogId: Long, leadId: String, type: String, notes: String?) {
        viewModelScope.launch {
            repository.insertOutcome(
                CallOutcome(
                    callLogId = callLogId,
                    leadId = leadId,
                    outcomeType = type,
                    notes = notes
                )
            )
            _uiState.value = OutcomeUiState.Success
        }
    }
}

sealed class OutcomeUiState {
    object Idle : OutcomeUiState()
    data class Form(val callId: Long, val leadId: String, val phoneNumber: String) : OutcomeUiState()
    object Success : OutcomeUiState()
}
