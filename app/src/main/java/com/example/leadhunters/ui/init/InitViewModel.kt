package com.example.leadhunters.ui.init

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.leadhunters.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class InitState {
    object Idle : InitState()
    object Initializing : InitState()
    object Success : InitState()
    data class Error(val message: String) : InitState()
}

@HiltViewModel
class InitViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<InitState>(InitState.Idle)
    val uiState = _uiState.asStateFlow()

    init {
        checkRegistration()
    }

    fun checkRegistration() {
        viewModelScope.launch {
            _uiState.value = InitState.Initializing
            
            if (authRepository.isRegistered()) {
                _uiState.value = InitState.Success
            } else {
                val result = authRepository.registerDevice()
                if (result.isSuccess) {
                    _uiState.value = InitState.Success
                } else {
                    _uiState.value = InitState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
                }
            }
        }
    }
}
