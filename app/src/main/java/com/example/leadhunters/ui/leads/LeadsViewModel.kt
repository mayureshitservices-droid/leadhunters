package com.example.leadhunters.ui.leads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.leadhunters.data.local.entities.Lead
import com.example.leadhunters.data.repository.CallRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LeadsViewModel @Inject constructor(
    private val repository: CallRepository
) : ViewModel() {

    val leads: StateFlow<List<Lead>> = repository.getLeads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addLead(name: String, phoneNumber: String) {
        viewModelScope.launch {
            repository.insertLead(
                Lead(
                    id = java.util.UUID.randomUUID().toString(),
                    name = name,
                    phoneNumber = phoneNumber
                )
            )
        }
    }
}
