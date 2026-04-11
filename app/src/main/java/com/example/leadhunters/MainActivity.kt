package com.example.leadhunters

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.leadhunters.ui.logs.CallLogsScreen
import com.example.leadhunters.ui.reminders.RemindersScreen
import com.example.leadhunters.ui.more.MoreScreen
import com.example.leadhunters.ui.more.TemplateManagementScreen
import com.example.leadhunters.ui.more.WhatsAppSendFlow
import com.example.leadhunters.ui.outcome.OutcomeFormScreen
import com.example.leadhunters.ui.init.InitScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Handle the splash screen transition.
        installSplashScreen()
        
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LeadHuntersTheme {
                com.example.leadhunters.ui.permissions.GlobalPermissionHandler {
                    MainScreen()
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
                currentDestination != "initialization") {
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
            composable(AppDestinations.LOGS.route) {
                CallLogsScreen(
                    onOutcomeClick = { callId -> navController.navigate("outcome/$callId") },
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
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
    val route: String
) {
    DASHBOARD("Dashboard", Icons.Default.Dashboard, "dashboard"),
    LOGS("Logs", Icons.Default.List, "logs"),
    REMINDERS("Reminders", Icons.Default.Notifications, "reminders"),
    MORE("More", Icons.Default.Menu, "more")
}