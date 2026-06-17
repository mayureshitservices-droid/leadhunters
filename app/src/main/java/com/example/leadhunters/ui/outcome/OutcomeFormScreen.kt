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
import androidx.compose.runtime.saveable.rememberSaveable
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

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is OutcomeUiState.Success -> onSuccess()
            is OutcomeUiState.Error -> {
                android.widget.Toast.makeText(context, state.message, android.widget.Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
            else -> {}
        }
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
                    onSubmit = { name, type, remarks, reminderTime, closingFormat, ptpAmount ->
                        viewModel.submitOutcome(
                            state.callId,
                            state.leadId,
                            name,
                            state.phoneNumber,
                            type,
                            remarks,
                            reminderTime,
                            closingFormat,
                            ptpAmount
                        )
                    }
                )
            }
            is OutcomeUiState.Success -> {
                // Already handled in LaunchedEffect
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
    onSubmit: (String, String, String?, Long?, String?, Double?) -> Unit
) {
    var customerName by rememberSaveable { mutableStateOf(initialName) }
    var selectedType by rememberSaveable { mutableStateOf("Lost") }
    var remarks by rememberSaveable { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) } // This one is fine to reset
    
    // Reminder States
    var selectedDate by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedTime by rememberSaveable {
        mutableStateOf<Pair<Int, Int>?>(null)
    }
    
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    
    // PTP States
    var selectedClosingFormat by rememberSaveable { mutableStateOf<String?>(null) }
    var ptpAmountText by rememberSaveable { mutableStateOf("") }
    var closingFormatExpanded by remember { mutableStateOf(false) }
    
    val closingFormats = listOf("FORECLOSURE", "EMI", "SETTLEMENT", "Partial Paid")

    val types = listOf(
        "Remind later",
        "Lost",
        "CB Request",
        "Left Msg",
        "Call Disconnect",
        "Bank PTP",
        "FPTP",
        "PTP",
        "Busy",
        "Not Reachable",
        "RNR",
        "Out of service",
        "Switch OFF",
        "Incoming not avaiable",
        "Partial Paid",
        "Already Paid",
        "Death",
        "CSWN",
        "RTP",
        "Interested",
        "Hot Lead",
        "Walk-In",
        "Callback",
        "Follow-Up",
        "Warm Lead",
        "Budget Issue",
        "Pending Decision",
        "Online",
        "Existing Student",
        "Not Interested",
        "Language Issue",
        "Sale Done"
    )
    val isReminder = selectedType == "Remind later"
    val isPtp = selectedType == "Bank PTP" || selectedType == "FPTP" || selectedType == "PTP" || selectedType == "RTP"

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
            isError = customerName.isBlank(),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = com.example.leadhunters.ui.theme.PrimaryRed,
                focusedBorderColor = com.example.leadhunters.ui.theme.PrimaryRed,
                errorBorderColor = com.example.leadhunters.ui.theme.PrimaryRed
            )
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
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                isError = false, // Always valid as it has a default
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = com.example.leadhunters.ui.theme.PrimaryRed,
                    focusedBorderColor = com.example.leadhunters.ui.theme.PrimaryRed
                )
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

        AnimatedVisibility(visible = !isReminder && !isPtp) {
            OutlinedTextField(
                value = remarks,
                onValueChange = { remarks = it },
                label = { Text("Remarks") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                isError = !isReminder && !isPtp && remarks.isBlank(),
                placeholder = { Text("Enter call details...") },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = com.example.leadhunters.ui.theme.PrimaryRed,
                    focusedBorderColor = com.example.leadhunters.ui.theme.PrimaryRed,
                    errorBorderColor = com.example.leadhunters.ui.theme.PrimaryRed
                )
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

        AnimatedVisibility(visible = isPtp) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("PTP Details", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

                // Closing Format Dropdown
                ExposedDropdownMenuBox(
                    expanded = closingFormatExpanded,
                    onExpandedChange = { closingFormatExpanded = !closingFormatExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedClosingFormat ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Closing Format") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = closingFormatExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        isError = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = com.example.leadhunters.ui.theme.PrimaryRed,
                            focusedBorderColor = com.example.leadhunters.ui.theme.PrimaryRed
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = closingFormatExpanded,
                        onDismissRequest = { closingFormatExpanded = false }
                    ) {
                        closingFormats.forEach { format ->
                            DropdownMenuItem(
                                text = { Text(format) },
                                onClick = {
                                    selectedClosingFormat = format
                                    closingFormatExpanded = false
                                }
                            )
                        }
                    }
                }

                // PTP Amount
                OutlinedTextField(
                    value = ptpAmountText,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                            ptpAmountText = newValue
                        }
                    },
                    label = { Text("PTP Amount") },
                    placeholder = { Text("Enter amount") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = ptpAmountText.isNotBlank() && (ptpAmountText.toDoubleOrNull() == null || ptpAmountText.toDoubleOrNull()!! <= 0),
                    supportingText = if (ptpAmountText.isNotBlank() && (ptpAmountText.toDoubleOrNull() == null || ptpAmountText.toDoubleOrNull()!! <= 0)) {
                        { Text("Amount must be greater than 0", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = com.example.leadhunters.ui.theme.PrimaryRed,
                        focusedBorderColor = com.example.leadhunters.ui.theme.PrimaryRed,
                        errorBorderColor = com.example.leadhunters.ui.theme.PrimaryRed
                    )
                )

                // Date Picker
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(selectedDate?.let { dateFormatter.format(Date(it)) } ?: "Select Date")
                }

                // Time Picker
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(selectedTime?.let { "${it.first}:${it.second.toString().padStart(2, '0')}" } ?: "Select Time")
                }

                // Remarks (optional)
                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("Remarks (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    placeholder = { Text("Enter remarks...") },
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = com.example.leadhunters.ui.theme.PrimaryRed,
                        focusedBorderColor = com.example.leadhunters.ui.theme.PrimaryRed
                    )
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                val reminderTime = if ((isReminder || isPtp) && selectedDate != null && selectedTime != null) {
                    val calendar = Calendar.getInstance().apply {
                        timeInMillis = selectedDate!!
                        set(Calendar.HOUR_OF_DAY, selectedTime!!.first)
                        set(Calendar.MINUTE, selectedTime!!.second)
                    }
                    calendar.timeInMillis
                } else null

                val ptpAmount = ptpAmountText.toDoubleOrNull()

                onSubmit(
                    customerName,
                    selectedType,
                    remarks.ifBlank { null },
                    reminderTime,
                    selectedClosingFormat,
                    ptpAmount
                )
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = MaterialTheme.shapes.medium,
            enabled = customerName.isNotBlank() && when {
                isPtp -> selectedClosingFormat != null && ptpAmountText.isNotBlank() && ptpAmountText.toDoubleOrNull() != null && ptpAmountText.toDoubleOrNull()!! > 0 && selectedDate != null && selectedTime != null
                isReminder -> selectedDate != null && selectedTime != null
                else -> remarks.isNotBlank()
            }
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
