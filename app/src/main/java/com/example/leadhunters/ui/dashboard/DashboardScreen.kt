package com.example.leadhunters.ui.dashboard

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.leadhunters.data.local.dao.CallStats
import com.example.leadhunters.ui.components.AppMetricTile
import com.example.leadhunters.ui.components.AppSectionHeader
import com.example.leadhunters.ui.theme.SuccessEmerald
import com.example.leadhunters.ui.theme.WarningAmber
import com.example.leadhunters.ui.theme.ErrorCoral

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val todayStats by viewModel.todayStats.collectAsState()
    val monthStats by viewModel.monthStats.collectAsState()

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Performance", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    val summary = viewModel.getShareSummary()
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        `package` = "com.whatsapp"
                        putExtra(Intent.EXTRA_TEXT, summary)
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        context.startActivity(Intent.createChooser(intent, "Share Stats"))
                    }
                },
                icon = { Icon(Icons.Default.Share, contentDescription = null) },
                text = { Text("Share Stats") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                AppSectionHeader(title = "Today", icon = Icons.Default.Today)
                StatsGrid(stats = todayStats)
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                AppSectionHeader(title = "Monthly Overview", icon = Icons.Default.CalendarMonth)
                StatsGrid(stats = monthStats)
            }
            
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
fun StatsGrid(stats: CallStats?) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AppMetricTile(
                title = "Total Calls",
                value = (stats?.totalCalls ?: 0).toString(),
                icon = Icons.Default.Call,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.primary
            )
            AppMetricTile(
                title = "Answered",
                value = (stats?.answered ?: 0).toString(),
                icon = Icons.Default.CheckCircle,
                modifier = Modifier.weight(1f),
                color = SuccessEmerald
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AppMetricTile(
                title = "Missed",
                value = (stats?.missed ?: 0).toString(),
                icon = Icons.Default.CallMissed,
                modifier = Modifier.weight(1f),
                color = WarningAmber
            )
            AppMetricTile(
                title = "Rejected",
                value = (stats?.rejected ?: 0).toString(),
                icon = Icons.Default.Block,
                modifier = Modifier.weight(1f),
                color = ErrorCoral
            )
        }
        
        AppMetricTile(
            title = "Total Talk Time",
            value = formatDuration(stats?.totalDuration ?: 0L),
            icon = Icons.Default.Timer,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m ${secs}s"
}
