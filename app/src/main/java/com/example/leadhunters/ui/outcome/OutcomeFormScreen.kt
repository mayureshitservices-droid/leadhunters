package com.example.leadhunters.ui.outcome

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun OutcomeFormScreen(
    callLogId: Long,
    onSuccess: () -> Unit,
    viewModel: OutcomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(callLogId) {
        viewModel.loadCall(callLogId)
    }

    when (val state = uiState) {
        is OutcomeUiState.Form -> {
            OutcomeForm(
                phoneNumber = state.phoneNumber,
                onSubmit = { type, notes ->
                    viewModel.submitOutcome(state.callId, state.leadId, type, notes)
                }
            )
        }
        is OutcomeUiState.Success -> {
            LaunchedEffect(Unit) { onSuccess() }
        }
        else -> {
            Box(modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(modifier = Modifier.align(androidx.compose.ui.Alignment.Center))
            }
        }
    }
}

@Composable
fun OutcomeForm(
    phoneNumber: String,
    onSubmit: (String, String?) -> Unit
) {
    var selectedType by remember { mutableStateOf("Interested") }
    var notes by remember { mutableStateOf("") }
    val types = listOf("Interested", "Not Interested", "Callback", "Wrong Number", "Busy")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Call Outcome for $phoneNumber", style = MaterialTheme.typography.headlineSmall)
        
        Text(text = "Select Outcome:", style = MaterialTheme.typography.titleMedium)
        
        types.forEach { type ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedType == type,
                    onClick = { selectedType = type }
                )
                Text(text = type, modifier = Modifier.padding(start = 8.dp))
            }
        }
        
        TextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes (Optional)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )
        
        Button(
            onClick = { onSubmit(selectedType, notes.ifBlank { null }) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Submit Outcome")
        }
    }
}
