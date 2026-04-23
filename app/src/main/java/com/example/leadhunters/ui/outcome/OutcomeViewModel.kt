package com.example.leadhunters.ui.outcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.leadhunters.data.local.entities.CallOutcome
import com.example.leadhunters.data.local.entities.Reminder
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
        val currentState = _uiState.value
        if (currentState is OutcomeUiState.Form && currentState.callId == callLogId) {
            // Already loaded for this call, don't reset
            return
        }

        viewModelScope.launch {
            val log = repository.getLogById(callLogId)
            if (log != null) {
                val lead = repository.getLeadById(log.leadId)
                _uiState.value = OutcomeUiState.Form(
                    callId = log.id,
                    leadId = log.leadId,
                    phoneNumber = log.phoneNumber,
                    customerName = lead?.name ?: ""
                )
            }
        }
    }

    fun clearError() {
        val state = _uiState.value
        if (state is OutcomeUiState.Error) {
            _uiState.value = state.previousFormState
        }
    }

    fun submitOutcome(
        callId: Long,
        leadId: String,
        customerName: String,
        phoneNumber: String,
        type: String,
        remarks: String?,
        reminderTimestamp: Long?
    ) {
        val currentFormState = _uiState.value as? OutcomeUiState.Form

        if (customerName.isBlank()) {
            _uiState.value = OutcomeUiState.Error("Please enter customer name", currentFormState ?: OutcomeUiState.Idle)
            return
        }

        if (type == "Remind later" && reminderTimestamp == null) {
            _uiState.value = OutcomeUiState.Error("Please select reminder date and time", currentFormState ?: OutcomeUiState.Idle)
            return
        }

        if (type != "Remind later" && remarks.isNullOrBlank()) {
            _uiState.value = OutcomeUiState.Error("Please enter remarks", currentFormState ?: OutcomeUiState.Idle)
            return
        }

        viewModelScope.launch {
            try {
                // 1. Save Outcome
                repository.insertOutcome(
                    CallOutcome(
                        callLogId = callId,
                        leadId = leadId,
                        customerName = customerName,
                        outcomeType = type,
                        remarks = remarks,
                        nextReminderTime = reminderTimestamp
                    )
                )

                // 2. If it's a reminder, save it to the reminders table too
                if (type == "Remind later" && reminderTimestamp != null) {
                    repository.insertReminder(
                        Reminder(
                            customerName = customerName,
                            phoneNumber = phoneNumber,
                            reminderTime = reminderTimestamp
                        )
                    )
                }

                _uiState.value = OutcomeUiState.Success
                
                // 3. Enqueue Sync for the call log (SyncWorker will now fetch the outcome)
                repository.enqueueSync(
                    com.example.leadhunters.data.local.entities.SyncItem(
                        type = "CALL_LOG",
                        referenceId = callId.toString(),
                        operation = "UPDATE",
                        payload = ""
                    )
                )
            } catch (e: Exception) {
                _uiState.value = OutcomeUiState.Error("Failed to save outcome: ${e.message}", currentFormState ?: OutcomeUiState.Idle)
            }
        }
    }
}

sealed class OutcomeUiState {
    object Idle : OutcomeUiState()
    data class Form(
        val callId: Long,
        val leadId: String,
        val phoneNumber: String,
        val customerName: String
    ) : OutcomeUiState()
    data class Error(val message: String, val previousFormState: OutcomeUiState) : OutcomeUiState()
    object Success : OutcomeUiState()
}
