package com.besenok.smarthome.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.besenok.smarthome.ui.navigation.Screen
import com.besenok.smarthome.ui.navigation.SmartHomeNavGraph
import com.besenok.smarthome.ui.splash.SplashScreen
import com.besenok.smarthome.ui.theme.SmartHomeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        
        setContent {
            SmartHomeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var showSplash by remember { mutableStateOf(true) }
                    
                    if (showSplash) {
                        SplashScreen(
                            onSplashComplete = {
                                showSplash = false
                            }
                        )
                    } else {
                        val navController = rememberNavController()
                        var selectedTab by remember { mutableIntStateOf(0) }
                        val tabs = listOf(
                            TabItem(Screen.Dashboard.route, "Главная", Icons.Default.Home),
                            TabItem(Screen.Devices.route, "Устройства", Icons.Default.Build),
                            TabItem(Screen.Sensors.route, "Мониторинг", Icons.Default.Info),
                            TabItem(Screen.Scenarios.route, "Сценарии", Icons.Default.List),
                            TabItem(Screen.Settings.route, "Настройки", Icons.Default.Settings)
                        )

                        Scaffold(
                            bottomBar = {
                                NavigationBar {
                                    tabs.forEachIndexed { index, tab ->
                                        NavigationBarItem(
                                            icon = { Icon(tab.icon, tab.label) },
                                            label = { Text(tab.label) },
                                            selected = selectedTab == index,
                                            onClick = {
                                                selectedTab = index
                                                navController.navigate(tab.route) {
                                                    popUpTo(navController.graph.startDestinationId) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        ) { paddingValues ->
                            SmartHomeNavGraph(
                                modifier = Modifier.padding(paddingValues),
                                navController = navController
                            )
                        }
                    }
                }
            }
        }
    }

    private data class TabItem(
        val route: String,
        val label: String,
        val icon: androidx.compose.ui.graphics.vector.ImageVector
    )
}
