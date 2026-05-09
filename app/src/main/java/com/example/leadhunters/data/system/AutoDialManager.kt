package com.example.leadhunters.data.system

import com.example.leadhunters.data.local.entities.Lead
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoDialManager @Inject constructor() {
    private val _isAutoDialActive = MutableStateFlow(false)
    val isAutoDialActive = _isAutoDialActive.asStateFlow()

    private var currentQueue: List<Lead> = emptyList()
    private var currentIndex: Int = -1

    fun startAutoDial(leads: List<Lead>, startIndex: Int = 0) {
        if (leads.isEmpty()) return
        currentQueue = leads
        currentIndex = startIndex - 1 // Will be incremented on first getNextLead
        _isAutoDialActive.value = true
    }

    fun stopAutoDial() {
        _isAutoDialActive.value = false
        currentQueue = emptyList()
        currentIndex = -1
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
