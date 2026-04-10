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
        viewModelScope.launch {
            val log = repository.getLogById(callLogId)
            if (log != null) {
                _uiState.value = OutcomeUiState.Form(
                    callId = log.id,
                    leadId = log.leadId,
                    phoneNumber = log.phoneNumber,
                    customerName = "" // Start with empty to avoid annoying placeholder text
                )
            }
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
        if (customerName.isBlank()) {
            // Handle error (could add a generic error state or use validation in UI)
            return
        }

        if (type == "Remind later" && reminderTimestamp == null) {
            return
        }

        if (type != "Remind later" && remarks.isNullOrBlank()) {
            return
        }

        viewModelScope.launch {
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
    object Success : OutcomeUiState()
}
