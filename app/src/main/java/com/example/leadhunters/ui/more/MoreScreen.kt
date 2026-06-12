package com.example.leadhunters.ui.more

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.leadhunters.BuildConfig
import com.example.leadhunters.ui.components.AppCard
import com.example.leadhunters.ui.theme.PrimaryRed

@Composable
fun MoreScreen(navController: NavController) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
            color = PrimaryRed
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                MoreItem(
                    icon = Icons.Default.List,
                    title = "Campaigns",
                    subtitle = "Claim new leads from available campaigns",
                    onClick = { navController.navigate("campaigns") }
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
                    icon = Icons.Default.SupportAgent,
                    title = "Need Help? Call 7447328144",
                    subtitle = "Tap to open the dialer",
                    onClick = {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:7447328144"))
                        context.startActivity(intent)
                    }
                )
            }
            item {
                AppVersionInfo()
            }
        }
    }
}

@Composable
fun AppVersionInfo() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Version ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
