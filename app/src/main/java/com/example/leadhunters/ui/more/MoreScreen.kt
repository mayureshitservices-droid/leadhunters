package com.example.leadhunters.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.leadhunters.ui.components.AppCard

@Composable
fun MoreScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
            color = MaterialTheme.colorScheme.onBackground
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                MoreItem(
                    icon = Icons.Default.Campaign,
                    title = "Campaigns",
                    subtitle = "Manage your calling campaigns",
                    onClick = { /* TODO */ }
                )
            }
            item {
                MoreItem(
                    icon = Icons.AutoMirrored.Filled.Message,
                    title = "Send WhatsApp Template",
                    subtitle = "Pick template and send to contact",
                    onClick = { navController.navigate("send_template") }
                )
            }
            item {
                MoreItem(
                    icon = Icons.Default.Edit,
                    title = "Manage Templates",
                    subtitle = "Add, edit or delete WhatsApp messages",
                    onClick = { navController.navigate("manage_templates") }
                )
            }
            item {
                MoreItem(
                    icon = Icons.Default.TipsAndUpdates,
                    title = "Optimization Guide",
                    subtitle = "Ensure calls are recorded correctly",
                    onClick = { /* TODO */ }
                )
            }
        }
    }
}

@Composable
fun MoreItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    AppCard(
        modifier = Modifier.clickable { onClick() },
        elevation = 0.5.dp
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                shape = MaterialTheme.shapes.medium
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = title,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Open",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}
