package app.lighthousecontrol.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lighthousecontrol.R
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
                Icon(
                    painterResource(R.drawable.ic_lighthouse),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
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
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_menu))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(if (station.type == StationType.V1) R.string.action_edit_v1 else R.string.action_rename)) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = { menuOpen = false; onEdit() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete)) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
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
                // Up to three buttons share the row, so use tighter padding than the default 24dp.
                val padding = PaddingValues(horizontal = 8.dp)
                Button(
                    onClick = { onAction(PowerAction.ON) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    contentPadding = padding,
                ) { IconLabel(R.drawable.ic_power, stringResource(R.string.action_on)) }
                if (station.type == StationType.V2) {
                    OutlinedButton(
                        onClick = { onAction(PowerAction.STANDBY) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        contentPadding = padding,
                    ) { IconLabel(R.drawable.ic_standby, stringResource(R.string.action_standby)) }
                }
                FilledTonalButton(
                    onClick = { onAction(PowerAction.SLEEP) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    contentPadding = padding,
                ) { IconLabel(R.drawable.ic_sleep, stringResource(R.string.action_sleep)) }
            }
        }
    }
}

/** Icon + label for button content, sized per Material guidelines. */
@Composable
internal fun IconLabel(@DrawableRes icon: Int, text: String) {
    Icon(
        painterResource(icon),
        contentDescription = null,
        modifier = Modifier.size(ButtonDefaults.IconSize),
    )
    Spacer(Modifier.width(6.dp))
    Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    // v1.0 doesn't report its state, so show the last command sent.
    val title = stringResource(status.power.titleRes)
    val label = if (station.type == StationType.V1) "$title*" else title
    AssistChip(onClick = {}, label = { Text(label) })
}
