package com.example.leadhunters

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import javax.inject.Inject
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.leadhunters.ui.theme.LeadHuntersTheme
import com.example.leadhunters.ui.dashboard.DashboardScreen
import com.example.leadhunters.ui.leads.LeadsScreen
import com.example.leadhunters.ui.completedleads.CompletedLeadsScreen
import com.example.leadhunters.ui.reminders.RemindersScreen
import com.example.leadhunters.ui.more.MoreScreen
import com.example.leadhunters.ui.more.TemplateManagementScreen
import com.example.leadhunters.ui.more.WhatsAppSendFlow
import com.example.leadhunters.ui.campaigns.CampaignsScreen
import com.example.leadhunters.ui.outcome.OutcomeFormScreen
import com.example.leadhunters.ui.init.InitScreen
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var appUpdater: com.example.leadhunters.updater.AppUpdater

    @Inject
    lateinit var authRepository: com.example.leadhunters.data.repository.AuthRepository

    @Inject
    lateinit var workRepository: com.example.leadhunters.data.repository.WorkRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        // Handle the splash screen transition.
        installSplashScreen()
        
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LeadHuntersTheme {
                var updateResult by remember { mutableStateOf<com.example.leadhunters.updater.UpdateResult?>(null) }
                val downloadProgress by appUpdater.downloadProgress.collectAsState()
                val context = androidx.compose.ui.platform.LocalContext.current

                LaunchedEffect(Unit) {
                    delay(1000)
                    // Optional: show a small toast so the user knows the check is happening
                    // android.widget.Toast.makeText(context, "Checking for updates...", android.widget.Toast.LENGTH_SHORT).show()
                    updateResult = appUpdater.checkForUpdate()
                }

                // Periodic Heartbeat (Every 30 seconds while app is open)
                LaunchedEffect(Unit) {
                    while (true) {
                        try {
                            if (authRepository.isRegistered()) {
                                val result = authRepository.sendHeartbeat()
                                result.onSuccess { deletedIds ->
                                    if (deletedIds.isNotEmpty()) {
                                        workRepository.deleteLeadsLocally(deletedIds)
                                    }
                                }
                                workRepository.updateTelecallerStatus("idle")
                            }
                        } catch (e: Exception) {
                            // Silently fail, it's just a heartbeat
                        }
                        kotlinx.coroutines.delay(30000)
                    }
                }

                if (updateResult is com.example.leadhunters.updater.UpdateResult.UpdateAvailable) {
                    val available = updateResult as com.example.leadhunters.updater.UpdateResult.UpdateAvailable
                    AlertDialog(
                        onDismissRequest = {
                            if (!available.mandatory) {
                                updateResult = com.example.leadhunters.updater.UpdateResult.NoUpdate
                            }
                        },
                        properties = DialogProperties(
                            dismissOnBackPress = !available.mandatory,
                            dismissOnClickOutside = !available.mandatory
                        ),
                        title = { Text("Update Available") },
                        text = { 
                            Column {
                                Text("A new version (${available.versionName}) is available.")
                                if (available.mandatory) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "This is a mandatory update. Please install it to continue using the app.",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                
                                if (downloadProgress != null) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = if (downloadProgress == 100) "Preparing installation..." else "Downloading... $downloadProgress%",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = (downloadProgress ?: 0).toFloat() / 100f,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                enabled = downloadProgress == null,
                                onClick = {
                                    appUpdater.downloadAndInstall(this@MainActivity, available.downloadUrl)
                                }
                            ) {
                                Text(if (downloadProgress != null) "Downloading..." else if (available.mandatory) "Update Now" else "Update")
                            }
                        },
                        dismissButton = {
                            if (!available.mandatory && downloadProgress == null) {
                                TextButton(onClick = { updateResult = com.example.leadhunters.updater.UpdateResult.NoUpdate }) {
                                    Text("Later")
                                }
                            }
                        }
                    )
                }

                if (updateResult is com.example.leadhunters.updater.UpdateResult.Error) {
                    val error = updateResult as com.example.leadhunters.updater.UpdateResult.Error
                    android.util.Log.w("MainActivity", "Update check error: ${error.message}")
                    LaunchedEffect(updateResult) {
                        updateResult = com.example.leadhunters.updater.UpdateResult.NoUpdate
                    }
                }

                com.example.leadhunters.ui.permissions.GlobalPermissionHandler {
                    Box(modifier = Modifier.fillMaxSize()) {
                        MainScreen()
                        
                        // Small status indicator during the check
                        if (updateResult == null) {
                            Text(
                                "Checking for updates...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentDestination != null && 
                !currentDestination.startsWith("outcome/") && 
                currentDestination != "initialization"
                ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.background,
                    tonalElevation = 8.dp
                ) {
                    AppDestinations.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = currentDestination == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label, style = MaterialTheme.typography.labelMedium) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "initialization",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("initialization") {
                InitScreen(
                    onSuccess = {
                        navController.navigate(AppDestinations.DASHBOARD.route) {
                            popUpTo("initialization") { inclusive = true }
                        }
                    }
                )
            }
            composable(AppDestinations.DASHBOARD.route) {
                DashboardScreen()
            }
            composable(AppDestinations.LEADS.route) {
                com.example.leadhunters.ui.leads.LeadsScreen(
                    onOutcomeClick = { callId -> navController.navigate("outcome/$callId") },
                    onWhatsAppClick = { number -> navController.navigate("send_template/$number") }
                )
            }
            composable(AppDestinations.LOGS.route) {
                CompletedLeadsScreen(
                    onWhatsAppClick = { number -> navController.navigate("send_template/$number") }
                )
            }
            composable(
                route = "send_template/{phoneNumber}",
                arguments = listOf(navArgument("phoneNumber") { type = NavType.StringType; nullable = true })
            ) { backStackEntry ->
                val phoneNumber = backStackEntry.arguments?.getString("phoneNumber")
                WhatsAppSendFlow(
                    phoneNumber = phoneNumber,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(AppDestinations.REMINDERS.route) {
                RemindersScreen()
            }
            composable(AppDestinations.MORE.route) {
                MoreScreen(navController = navController)
            }
            composable(
                route = "outcome/{callId}",
                arguments = listOf(navArgument("callId") { type = NavType.LongType })
            ) { backStackEntry ->
                val callId = backStackEntry.arguments?.getLong("callId") ?: 0L
                OutcomeFormScreen(
                    callLogId = callId,
                    onSuccess = { navController.popBackStack() }
                )
            }
            composable("manage_templates") {
                TemplateManagementScreen(onBack = { navController.popBackStack() })
            }
            composable("send_template") {
                // We'll implement this as a selection screen
                WhatsAppSendFlow(onBack = { navController.popBackStack() })
            }
            composable("campaigns") {
                CampaignsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
    val route: String
) {
    DASHBOARD("Dashboard", Icons.Default.Dashboard, "dashboard"),
    LEADS("My Leads", Icons.Default.ContactPhone, "leads"),
    LOGS("Logs", Icons.Default.History, "completed_leads"),
    REMINDERS("Reminders", Icons.Default.Notifications, "reminders"),
    MORE("More", Icons.Default.Menu, "more")
}