package com.example.leadhunters.data.system

import com.example.leadhunters.data.local.entities.Lead
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoDialManager @Inject constructor() {
    private val _isAutoDialActive = MutableStateFlow(false)
    val isAutoDialActive = _isAutoDialActive.asStateFlow()

    private var currentQueue: List<Lead> = emptyList()
    private var currentIndex: Int = -1

    private val _autoDialEvents = kotlinx.coroutines.flow.MutableSharedFlow<Lead>(replay = 0)
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

    fun onCallEnded(status: String) {
        if (!_isAutoDialActive.value) return
        
        // SMART-SKIP: Automatically trigger next lead only if call was MISSED or REJECTED
        // If it was ANSWERED, we wait for the user to fill the outcome form manually.
        if (status == "MISSED" || status == "REJECTED") {
            val nextLead = getNextLead()
            if (nextLead != null) {
                // Use a background scope to emit since this is called from Reconciler
                kotlinx.coroutines.GlobalScope.launch {
                    _autoDialEvents.emit(nextLead)
                }
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
