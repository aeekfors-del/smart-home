package com.besenok.smarthome.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.besenok.smarthome.ui.screens.dashboard.DashboardScreen
import com.besenok.smarthome.ui.screens.devices.DeviceListScreen
import com.besenok.smarthome.ui.screens.devices.device_detail.DeviceDetailScreen
import com.besenok.smarthome.ui.screens.scenarios.ScenarioListScreen
import com.besenok.smarthome.ui.screens.sensors.SensorsScreen
import com.besenok.smarthome.ui.screens.settings.SettingsScreen

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object Devices : Screen("devices")
    object Sensors : Screen("sensors")
    object Scenarios : Screen("scenarios")
    object Settings : Screen("settings")
    object DeviceDetail : Screen("device_detail/{deviceId}") {
        fun createRoute(deviceId: String) = "device_detail/$deviceId"
    }
}

@Composable
fun SmartHomeNavGraph(
    navController: NavHostController,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
        modifier = modifier
    ) {
        mainScreen(navController)
    }
}

fun NavGraphBuilder.mainScreen(
    navController: NavHostController
) {
    composable(Screen.Dashboard.route) {
        DashboardScreen()
    }
    composable(Screen.Devices.route) {
        DeviceListScreen(
            onDeviceClick = { deviceId ->
                navController.navigate(Screen.DeviceDetail.createRoute(deviceId))
            }
        )
    }
    composable(Screen.Sensors.route) {
        SensorsScreen()
    }
    composable(Screen.Scenarios.route) {
        ScenarioListScreen()
    }
    composable(Screen.Settings.route) {
        SettingsScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }
    composable(Screen.DeviceDetail.route) {
        navBackStackEntry ->
        val deviceId = navBackStackEntry.arguments?.getString("deviceId") ?: ""
        DeviceDetailScreen(
            deviceId = deviceId,
            onNavigateBack = { navController.popBackStack() }
        )
    }
}
