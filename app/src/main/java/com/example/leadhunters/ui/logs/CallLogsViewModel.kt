package com.example.leadhunters.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.leadhunters.data.local.entities.AppCallLog
import com.example.leadhunters.data.repository.CallRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class CallLogsViewModel @Inject constructor(
    private val repository: CallRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _allLogs = repository.getCallLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val callLogs: StateFlow<List<AppCallLog>> = combine(_allLogs, _searchQuery) { logs, query ->
        if (query.isBlank()) {
            logs
        } else {
            logs.filter { it.phoneNumber.contains(query, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
    }

    suspend fun startCall(phoneNumber: String): Long {
        return repository.startCall(leadId = null, phoneNumber = phoneNumber)
    }
}
