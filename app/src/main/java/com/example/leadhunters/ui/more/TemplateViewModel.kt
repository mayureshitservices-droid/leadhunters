package com.example.leadhunters.ui.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.leadhunters.data.local.dao.TeleCallerDao
import com.example.leadhunters.data.local.entities.WhatsAppTemplate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TemplateViewModel @Inject constructor(
    private val teleCallerDao: TeleCallerDao
) : ViewModel() {

    val templates: StateFlow<List<WhatsAppTemplate>> = teleCallerDao.getAllTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addTemplate(name: String, message: String) {
        viewModelScope.launch {
            teleCallerDao.insertTemplate(WhatsAppTemplate(name = name, message = message))
        }
    }

    fun updateTemplate(template: WhatsAppTemplate) {
        viewModelScope.launch {
            teleCallerDao.insertTemplate(template)
        }
    }

    fun deleteTemplate(template: WhatsAppTemplate) {
        viewModelScope.launch {
            teleCallerDao.deleteTemplate(template)
        }
    }
    
    fun seedInitialTemplatesIfEmpty() {
        viewModelScope.launch {
            if (templates.value.isEmpty()) {
                addTemplate("Follow-up", "Hi, I just called you regarding our recent discussion. Let me know when is a good time to talk again.")
                addTemplate("Meeting Confirmation", "Hello! This is to confirm our meeting scheduled for tomorrow. Looking forward to it.")
                addTemplate("Information Request", "Hi, could you please share the documents we discussed during our call? Thanks!")
            }
        }
    }
}
