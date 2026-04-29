package com.example.leadhunters.ui.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
fun GlobalPermissionHandler(content: @Composable () -> Unit) {
    val context = LocalContext.current
    
    // Standard permissions list
    val standardPermissions = mutableListOf(
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_PHONE_STATE
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_MEDIA_AUDIO)
            add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }.toTypedArray()

    // State to track if standard permissions are granted
    var standardPermissionsGranted by remember {
        mutableStateOf(standardPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        })
    }

    // State to track if MANAGE_EXTERNAL_STORAGE is granted (Android 11+)
    var manageStorageGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                true // Not needed below Android 11
            }
        )
    }

    val permissionsGranted = standardPermissionsGranted && manageStorageGranted
    var showExplanationDialog by remember { mutableStateOf(false) }

    // Re-check permissions when returning from settings
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                standardPermissionsGranted = standardPermissions.all {
                    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                }
                manageStorageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Environment.isExternalStorageManager()
                } else {
                    true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val allGranted = permissionsMap.values.all { it }
        standardPermissionsGranted = allGranted
        if (!allGranted) {
            showExplanationDialog = true
        }
    }

    LaunchedEffect(Unit) {
        if (!standardPermissionsGranted) {
            launcher.launch(standardPermissions)
        }
    }

    if (showExplanationDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Permissions Required") },
            text = { 
                Text("LeadHunters needs Call, Phone State, and Audio permissions to track business outcomes and recordings. On newer devices, 'All Files Access' is also required to find call recordings.") 
            },
            confirmButton = {
                Button(onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                    showExplanationDialog = false
                }) {
                    Text("Open App Info")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showExplanationDialog = false
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (permissionsGranted) {
        content()
    } else {
        // Show a blocking state if permissions are missing
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Setup Required",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                val statusMessage = when {
                    !standardPermissionsGranted -> "Please grant Call and Audio permissions to continue."
                    !manageStorageGranted -> "Samsung/Android 11+ requires 'All Files Access' to locate call recordings. Please enable it in the next screen."
                    else -> "Permission setup incomplete."
                }
                
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(32.dp))
                
                if (!standardPermissionsGranted) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { launcher.launch(standardPermissions) }
                    ) {
                        Text("Grant Standard Permissions")
                    }
                } else if (!manageStorageGranted) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                try {
                                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                    context.startActivity(intent)
                                }
                            }
                        }
                    ) {
                        Text("Grant All Files Access")
                    }
                }
            }
        }
    }
}

