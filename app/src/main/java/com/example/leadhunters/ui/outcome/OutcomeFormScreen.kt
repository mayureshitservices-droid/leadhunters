package com.example.leadhunters.ui.outcome

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Call Outcome", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is OutcomeUiState.Form -> {
                OutcomeForm(
                    initialName = state.customerName,
                    phoneNumber = state.phoneNumber,
                    modifier = Modifier.padding(padding),
                    onSubmit = { name, type, remarks, reminderTime ->
                        viewModel.submitOutcome(
                            state.callId,
                            state.leadId,
                            name,
                            state.phoneNumber,
                            type,
                            remarks,
                            reminderTime
                        )
                    }
                )
            }
            is OutcomeUiState.Success -> {
                LaunchedEffect(Unit) { onSuccess() }
            }
            else -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutcomeForm(
    initialName: String,
    phoneNumber: String,
    modifier: Modifier = Modifier,
    onSubmit: (String, String, String?, Long?) -> Unit
) {
    var customerName by remember { mutableStateOf(initialName) }
    var selectedType by remember { mutableStateOf("Interested") }
    var remarks by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    
    // Reminder States
    var selectedDate by remember { mutableStateOf<Long?>(null) }
    var selectedTime by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val types = listOf("Interested", "Ordered", "Booked", "Remind later", "Lost")
    val isReminder = selectedType == "Remind later"

    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        OutlinedTextField(
            value = customerName,
            onValueChange = { customerName = it },
            label = { Text("Customer Name") },
            placeholder = { Text("Enter full name") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            singleLine = true,
            isError = customerName.isBlank()
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedType,
                onValueChange = {},
                readOnly = true,
                label = { Text("Outcome") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                types.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type) },
                        onClick = {
                            selectedType = type
                            expanded = false
                        }
                    )
                }
            }
        }

        AnimatedVisibility(visible = !isReminder) {
            OutlinedTextField(
                value = remarks,
                onValueChange = { remarks = it },
                label = { Text("Remarks") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                isError = !isReminder && remarks.isBlank(),
                placeholder = { Text("Enter call details...") }
            )
        }

        AnimatedVisibility(visible = isReminder) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Schedule Reminder", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(selectedDate?.let { dateFormatter.format(Date(it)) } ?: "Select Date")
                }

                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(selectedTime?.let { "${it.first}:${it.second.toString().padStart(2, '0')}" } ?: "Select Time")
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                val reminderTime = if (isReminder && selectedDate != null && selectedTime != null) {
                    val calendar = Calendar.getInstance().apply {
                        timeInMillis = selectedDate!!
                        set(Calendar.HOUR_OF_DAY, selectedTime!!.first)
                        set(Calendar.MINUTE, selectedTime!!.second)
                    }
                    calendar.timeInMillis
                } else null

                onSubmit(
                    customerName,
                    selectedType,
                    remarks.ifBlank { null },
                    reminderTime
                )
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = MaterialTheme.shapes.medium,
            enabled = customerName.isNotBlank() && (if (isReminder) (selectedDate != null && selectedTime != null) else remarks.isNotBlank())
        ) {
            Text("Submit Result", style = MaterialTheme.typography.titleMedium)
        }
    }

    // Material 3 Pickers (Simplified for this context, using DatePicker/TimePicker state)
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    selectedDate = datePickerState.selectedDateMillis
                    showDatePicker = false
                }) { Text("OK") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState()
        TimePickerDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    selectedTime = Pair(timePickerState.hour, timePickerState.minute)
                    showTimePicker = false
                }) { Text("OK") }
            }
        ) {
            TimePicker(state = timePickerState)
        }
    }
}

@Composable
fun TimePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        text = content
    )
}
