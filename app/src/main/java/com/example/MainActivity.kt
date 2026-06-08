package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.ui.screens.*
import com.example.ui.theme.FinTraceTheme
import com.example.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.example.service.AlertSoundPlayer.initTts(applicationContext)

        setContent {
            // Collect theme mode reactively
            val currentTheme by viewModel.themeMode.collectAsState()

            FinTraceTheme(mode = currentTheme) {
                // Persistent State Management
                var currentRoute by remember { mutableStateOf("wizard_check") }
                var currentTab by remember { mutableStateOf("prices") }
                var selectedSymbolForDetail by remember { mutableStateOf("") }

                // Check completed wizard setting
                val states by viewModel.symbolStates.collectAsState()
                val alerts by viewModel.alertList.collectAsState()

                LaunchedEffect(states) {
                    if (currentRoute == "wizard_check") {
                        val monitor = com.example.data.repository.PriceMonitorManager.getInstance(applicationContext)
                        val completedSetting = monitor.getSetting("setup_completed")
                        currentRoute = if (completedSetting == "true") "home" else "wizard"
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (currentRoute) {
                        "wizard_check" -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background),
                                contentAlignment = androidx.compose.ui.Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        "wizard" -> {
                            SetupWizardScreen(
                                viewModel = viewModel,
                                onSetupComplete = {
                                    viewModel.saveSettingGeneric("setup_completed", "true")
                                    currentRoute = "home"
                                }
                            )
                        }

                        "home" -> {
                            Scaffold(
                                bottomBar = {
                                    NavigationBar(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        windowInsets = WindowInsets.navigationBars
                                    ) {
                                        NavigationBarItem(
                                            selected = currentTab == "prices",
                                            onClick = { currentTab = "prices" },
                                            icon = { Icon(Icons.Default.TrendingUp, contentDescription = null) },
                                            label = { Text("Prices") }
                                        )
                                        NavigationBarItem(
                                            selected = currentTab == "alerts",
                                            onClick = { currentTab = "alerts" },
                                            icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                                            label = { Text("Alerts") }
                                        )
                                        NavigationBarItem(
                                            selected = currentTab == "logs",
                                            onClick = { currentTab = "logs" },
                                            icon = { Icon(Icons.Default.History, contentDescription = null) },
                                            label = { Text("Logs") }
                                        )
                                        NavigationBarItem(
                                            selected = currentTab == "settings",
                                            onClick = { currentTab = "settings" },
                                            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                            label = { Text("Settings") }
                                        )
                                    }
                                }
                            ) { innerPadding ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(innerPadding)
                                ) {
                                    when (currentTab) {
                                        "prices" -> {
                                            DashboardScreen(
                                                viewModel = viewModel,
                                                onSymbolSelected = { sym ->
                                                    selectedSymbolForDetail = sym
                                                    currentRoute = "detail"
                                                },
                                                onQuickAlertRequest = { sym ->
                                                    selectedSymbolForDetail = sym
                                                    currentTab = "alerts"
                                                }
                                            )
                                        }

                                        "alerts" -> {
                                            AlertListScreen(viewModel = viewModel)
                                         }

                                         "logs" -> {
                                             LogsScreen(viewModel = viewModel)
                                        }

                                        "settings" -> {
                                            SettingsScreen(
                                                viewModel = viewModel,
                                                onNavigateToAboutApp = { currentRoute = "about_app" },
                                                onNavigateToAboutDeveloper = { currentRoute = "about_dev" },
                                                onNavigateToPermissions = { currentRoute = "permissions" }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        "detail" -> {
                            SymbolDetailScreen(
                                symbol = selectedSymbolForDetail,
                                viewModel = viewModel,
                                onBack = { currentRoute = "home" }
                            )
                        }

                        "about_app" -> {
                            AboutAppScreen(onBack = { currentRoute = "home"; currentTab = "settings" })
                        }

                        "about_dev" -> {
                            AboutDeveloperScreen(onBack = { currentRoute = "home"; currentTab = "settings" })
                        }

                        "permissions" -> {
                            PermissionHelperScreen(onBack = { currentRoute = "home"; currentTab = "settings" })
                        }
                    }
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN) {
            val keyCode = event.keyCode
            if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP ||
                keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
                com.example.service.AlertSoundPlayer.stopPlayback()
                com.example.service.NotificationHelper.cancelVibration(applicationContext)
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
