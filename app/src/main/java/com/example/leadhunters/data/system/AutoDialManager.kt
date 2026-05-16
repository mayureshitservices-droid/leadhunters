package com.example.leadhunters.data.system

import com.example.leadhunters.data.local.entities.Lead
import com.example.leadhunters.data.local.entities.CallOutcome
import com.example.leadhunters.data.local.entities.SyncItem
import com.example.leadhunters.data.repository.CallRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoDialManager @Inject constructor(
    private val repository: CallRepository
) {
    private val _isAutoDialActive = MutableStateFlow(false)
    val isAutoDialActive = _isAutoDialActive.asStateFlow()

    private var currentQueue: List<Lead> = emptyList()
    private var currentIndex: Int = -1

    private val _autoDialEvents = MutableSharedFlow<Lead>(replay = 0)
    val autoDialEvents = _autoDialEvents.asSharedFlow()

    fun startAutoDial(leads: List<Lead>, startIndex: Int = 0) {
        if (leads.isEmpty()) return
        currentQueue = leads
        currentIndex = startIndex - 1 
        _isAutoDialActive.value = true
    }

    fun stopAutoDial() {
        _isAutoDialActive.value = false
        currentQueue = emptyList()
        currentIndex = -1
    }

    fun onCallEnded(status: String, callLogId: Long) {
        if (!_isAutoDialActive.value) return
        
        // SMART-SKIP: Automatically trigger next lead only if call was NOT ANSWERED
        // This covers MISSED, REJECTED, and carrier messages (if reconciliation logic allows)
        if (status != "ANSWERED") {
            val currentLead = if (currentIndex >= 0 && currentIndex < currentQueue.size) {
                currentQueue[currentIndex]
            } else null

            // Use a background scope to save outcome
            GlobalScope.launch {
                if (currentLead != null) {
                    // 1. Automatically save outcome for unanswered call
                    repository.insertOutcome(
                        CallOutcome(
                            callLogId = callLogId,
                            leadId = currentLead.id,
                            customerName = currentLead.name,
                            outcomeType = status,
                            remarks = "Auto-logged (Unanswered)"
                        )
                    )
                    
                    // 2. Enqueue Sync
                    repository.enqueueSync(
                        SyncItem(
                            type = "CALL_LOG",
                            referenceId = callLogId.toString(),
                            operation = "UPDATE",
                            payload = ""
                        )
                    )
                }
                // Next lead will be triggered by LeadsViewModel observing the UI state change
            }
        }
    }

    fun getNextLead(): Lead? {
        if (!_isAutoDialActive.value) return null
        
        currentIndex++
        return if (currentIndex < currentQueue.size) {
            currentQueue[currentIndex]
        } else {
            stopAutoDial()
            null
        }
    }

    fun getCurrentQueueInfo(): Pair<Int, Int> {
        return Pair(currentIndex + 1, currentQueue.size)
    }

    fun isActive() = _isAutoDialActive.value
}
