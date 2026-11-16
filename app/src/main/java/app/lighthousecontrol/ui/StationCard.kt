package app.lighthousecontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lighthousecontrol.StationStatus
import app.lighthousecontrol.ble.PowerAction
import app.lighthousecontrol.data.Station
import app.lighthousecontrol.data.StationPowerState
import app.lighthousecontrol.data.StationType

@Composable
fun StationCard(
    station: Station,
    status: StationStatus,
    onAction: (PowerAction) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(station.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${station.type.title} · ${station.bleName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusBadge(station, status)
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Меню")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (station.type == StationType.V1) "Изменить название / ID" else "Переименовать") },
                            onClick = { menuOpen = false; onEdit() },
                        )
                        DropdownMenuItem(
                            text = { Text("Удалить") },
                            onClick = { menuOpen = false; onDelete() },
                        )
                    }
                }
            }

            status.error?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val enabled = !status.busy
                Button(
                    onClick = { onAction(PowerAction.ON) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) { Text("Вкл") }
                if (station.type == StationType.V2) {
                    OutlinedButton(
                        onClick = { onAction(PowerAction.STANDBY) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                    ) { Text("Standby") }
                }
                FilledTonalButton(
                    onClick = { onAction(PowerAction.SLEEP) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) { Text("Сон") }
            }
        }
    }
}

@Composable
private fun StatusBadge(station: Station, status: StationStatus) {
    if (status.busy) {
        CircularProgressIndicator(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .size(20.dp),
            strokeWidth = 2.dp,
        )
        return
    }
    if (status.power == StationPowerState.UNKNOWN) return
    // v1.0 не сообщает состояние — показываем последнюю отправленную команду.
    val label = if (station.type == StationType.V1) "${status.power.title}*" else status.power.title
    AssistChip(onClick = {}, label = { Text(label) })
}
