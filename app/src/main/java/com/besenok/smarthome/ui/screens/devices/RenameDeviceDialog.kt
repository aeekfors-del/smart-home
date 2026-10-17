package com.besenok.smarthome.ui.screens.devices

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import com.besenok.smarthome.domain.model.Device

/**
 * Диалог для переименования устройства
 * @param device Устройство для переименования
 * @param onDismiss Callback для закрытия диалога без сохранения
 * @param onConfirm Callback для сохранения нового имени
 */
@Composable
fun RenameDeviceDialog(
    device: Device,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by mutableStateOf(device.name)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null
            )
        },
        title = { Text("Переименовать устройство") },
        text = {
            Column {
                Text(
                    text = "Оригинальное имя: ${device.name}",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Новое имя") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    if (text.isNotBlank()) {
                        onConfirm(text)
                    }
                },
                enabled = text.isNotBlank()
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
