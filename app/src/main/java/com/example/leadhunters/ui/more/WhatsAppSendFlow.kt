package com.example.leadhunters.ui.more

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.leadhunters.data.local.entities.WhatsAppTemplate
import com.example.leadhunters.ui.components.AppCard
import com.example.leadhunters.ui.logs.CallLogsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsAppSendFlow(
    phoneNumber: String? = null,
    onBack: () -> Unit,
    templateViewModel: TemplateViewModel = hiltViewModel(),
    logsViewModel: CallLogsViewModel = hiltViewModel()
) {
    var selectedTemplate by remember { mutableStateOf<WhatsAppTemplate?>(null) }
    val templates by templateViewModel.templates.collectAsState()
    val logs by logsViewModel.callLogs.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = if (selectedTemplate == null) "Select Template" else "Select Recipient",
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { if (selectedTemplate == null) onBack() else selectedTemplate = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (selectedTemplate == null) {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(templates) { template ->
                    AppCard(
                        modifier = Modifier.clickable { 
                            if (phoneNumber != null) {
                                sendWhatsApp(context, phoneNumber, template.message)
                                onBack()
                            } else {
                                selectedTemplate = template 
                            }
                        },
                        elevation = 1.dp
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = template.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = template.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val uniqueContacts = logs.distinctBy { it.phoneNumber }
                items(uniqueContacts) { log ->
                    AppCard(
                        modifier = Modifier.clickable {
                            sendWhatsApp(context, log.phoneNumber, selectedTemplate!!.message)
                            onBack()
                        },
                        elevation = 0.5.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = androidx.compose.foundation.shape.CircleShape
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = log.phoneNumber,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun sendWhatsApp(context: android.content.Context, phoneNumber: String, message: String) {
    val url = "https://api.whatsapp.com/send?phone=$phoneNumber&text=${Uri.encode(message)}"
    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse(url)
        `package` = "com.whatsapp"
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(browserIntent)
    }
}
