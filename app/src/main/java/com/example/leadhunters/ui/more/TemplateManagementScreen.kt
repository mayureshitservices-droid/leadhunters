package com.example.leadhunters.ui.more

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.leadhunters.data.local.entities.WhatsAppTemplate
import com.example.leadhunters.ui.components.AppCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateManagementScreen(
    viewModel: TemplateViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val templates by viewModel.templates.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<WhatsAppTemplate?>(null) }

    LaunchedEffect(Unit) {
        viewModel.seedInitialTemplatesIfEmpty()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WhatsApp Templates", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    FilledIconButton(
                        onClick = { showAddDialog = true },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Template")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(templates) { template ->
                TemplateCard(
                    template = template,
                    onEdit = { editingTemplate = it },
                    onDelete = { viewModel.deleteTemplate(it) }
                )
            }
        }

        if (showAddDialog) {
            TemplateDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { name, msg ->
                    viewModel.addTemplate(name, msg)
                    showAddDialog = false
                }
            )
        }

        editingTemplate?.let { template ->
            TemplateDialog(
                initialName = template.name,
                initialMessage = template.message,
                onDismiss = { editingTemplate = null },
                onConfirm = { name, msg ->
                    viewModel.updateTemplate(template.copy(name = name, message = msg))
                    editingTemplate = null
                }
            )
        }
    }
}

@Composable
fun TemplateCard(
    template: WhatsAppTemplate,
    onEdit: (WhatsAppTemplate) -> Unit,
    onDelete: (WhatsAppTemplate) -> Unit
) {
    AppCard(elevation = 1.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = template.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row {
                    IconButton(onClick = { onEdit(template) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { onDelete(template) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = template.message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun TemplateDialog(
    initialName: String = "",
    initialMessage: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var message by remember { mutableStateOf(initialMessage) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialName.isEmpty()) "Add Template" else "Edit Template", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Template Name (e.g. Follow-up)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Message Content") },
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    shape = MaterialTheme.shapes.medium
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, message) },
                enabled = name.isNotBlank() && message.isNotBlank(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Save Template")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
