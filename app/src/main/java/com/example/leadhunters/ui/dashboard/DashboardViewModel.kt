package com.example.leadhunters.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.leadhunters.data.local.dao.CallStats
import com.example.leadhunters.data.local.dao.TeleCallerDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.util.*
import javax.inject.Inject
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val teleCallerDao: TeleCallerDao
) : ViewModel() {

    private val calendar = Calendar.getInstance()

    val todayStats: StateFlow<CallStats?> = flow {
        val startOfDay = getStartOfDay()
        emitAll(teleCallerDao.getTodayStats(startOfDay))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val monthStats: StateFlow<CallStats?> = flow {
        val startOfMonth = getStartOfMonth()
        emitAll(teleCallerDao.getMonthStats(startOfMonth))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private fun getStartOfDay(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun getStartOfMonth(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun getShareSummary(): String {
        val today = todayStats.value
        val month = monthStats.value
        
        return buildString {
            append("*LeadHunters Performance Summary*\n\n")
            append("*Today's Stats:*\n")
            append("• Total Calls: ${today?.totalCalls ?: 0}\n")
            append("• Answered: ${today?.answered ?: 0}\n")
            append("• Missed: ${today?.missed ?: 0}\n")
            append("• Duration: ${formatDuration(today?.totalDuration ?: 0L)}\n\n")
            
            append("*Monthly Stats:*\n")
            append("• Total Calls: ${month?.totalCalls ?: 0}\n")
            append("• Answered: ${month?.answered ?: 0}\n")
            append("• Duration: ${formatDuration(month?.totalDuration ?: 0L)}\n\n")
            
            append("Generated via LeadHunters App")
        }
    }

    private fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            String.format("%dh %dm %ds", hours, minutes, secs)
        } else {
            String.format("%dm %ds", minutes, secs)
        }
    }
}
