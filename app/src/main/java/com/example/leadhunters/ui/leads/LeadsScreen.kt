package com.example.leadhunters.ui.leads

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.leadhunters.data.local.entities.Lead
import com.example.leadhunters.service.CallService

@Composable
fun LeadsScreen(
    viewModel: LeadsViewModel = hiltViewModel()
) {
    val leads by viewModel.leads.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            // Handle denial
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Lead")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(leads) { lead ->
                LeadItem(lead = lead, onCallClick = { 
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        if (!Environment.isExternalStorageManager()) {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                            return@LeadItem
                        }
                    }

                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                        makeCall(context, lead)
                    } else {
                        permissionLauncher.launch(Manifest.permission.CALL_PHONE)
                    }
                })
            }
        }
// ...

        if (showDialog) {
            AddLeadDialog(
                onDismiss = { showDialog = false },
                onConfirm = { name, phone ->
                    viewModel.addLead(name, phone)
                    showDialog = false
                }
            )
        }
    }
}

@Composable
fun LeadItem(lead: Lead, onCallClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = lead.name, style = MaterialTheme.typography.titleMedium)
                Text(text = lead.phoneNumber, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onCallClick) {
                Icon(Icons.Default.Call, contentDescription = "Call", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun AddLeadDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Lead") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                TextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") })
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name, phone) }) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun makeCall(context: Context, lead: Lead) {
    // 1. Start Service Defensively
    val serviceIntent = Intent(context, CallService::class.java).apply {
        action = CallService.ACTION_START_TRACKING
        putExtra(CallService.EXTRA_PHONE_NUMBER, lead.phoneNumber)
    }
    try {
        ContextCompat.startForegroundService(context, serviceIntent)
    } catch (e: Exception) {
        android.util.Log.e("Leads", "Tracking service blocked", e)
    }

    // 2. Initiate Intent.ACTION_CALL with Fallback
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
        val callIntent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:${lead.phoneNumber}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(callIntent)
        } catch (e: Exception) {
            val fallback = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${lead.phoneNumber}")).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            context.startActivity(fallback)
        }
    } else {
        val fallback = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${lead.phoneNumber}")).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        context.startActivity(fallback)
    }
}
