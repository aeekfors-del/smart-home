package com.besenok.smarthome.ui.screens.time

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeScreen(viewModel: TimeViewModel = hiltViewModel()) {
    val timeState by viewModel.time.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Точное время") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            val displayTime = when (timeState) {
                is com.besenok.smarthome.ui.common.UiState.Success -> (timeState as com.besenok.smarthome.ui.common.UiState.Success).data.dateTime.substring(11, 19)
                else -> "--:--:--"
            }
            val displayDate = when (timeState) {
                is com.besenok.smarthome.ui.common.UiState.Success -> (timeState as com.besenok.smarthome.ui.common.UiState.Success).data.dateTime.substring(0, 10)
                else -> "----/--/--"
            }
            val displayTimezone = when (timeState) {
                is com.besenok.smarthome.ui.common.UiState.Success -> (timeState as com.besenok.smarthome.ui.common.UiState.Success).data.timezone
                else -> ""
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .padding(32.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        displayTime,
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        displayDate,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Часовой пояс", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(displayTimezone, style = MaterialTheme.typography.bodyLarge)
                }
            }

            Button(
                onClick = { viewModel.syncTime() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Синхронизировать с сервером")
            }
        }
    }
}
