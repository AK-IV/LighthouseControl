package app.lighthousecontrol.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.lighthousecontrol.ble.LighthouseProtocol
import app.lighthousecontrol.ble.ScannedStation
import app.lighthousecontrol.data.Station
import app.lighthousecontrol.data.StationType

/** Черновик станции для диалога добавления/редактирования. */
data class StationDraft(
    val mac: String,
    val bleName: String,
    val type: StationType,
    val label: String,
    val v1Id: String,
    val isNew: Boolean,
) {
    companion object {
        fun fromScan(s: ScannedStation) = StationDraft(
            mac = s.mac,
            bleName = s.name,
            type = s.type,
            label = "",
            v1Id = "",
            isNew = true,
        )

        fun fromStation(s: Station) = StationDraft(
            mac = s.mac,
            bleName = s.bleName,
            type = s.type,
            label = s.label,
            v1Id = s.v1Id.orEmpty(),
            isNew = false,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanSheet(
    results: List<ScannedStation>,
    scanning: Boolean,
    onRescan: () -> Unit,
    onPick: (ScannedStation) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Поиск станций", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (scanning) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = onRescan) { Text("Повторить") }
                }
            }
            Text(
                "Станции должны быть включены в розетку. Держите телефон поближе к ним.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            if (results.isEmpty() && !scanning) {
                Text(
                    "Новых станций не найдено. Уже добавленные станции здесь не показываются. " +
                        "На Android 11 и ниже для поиска нужна включённая геолокация.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }

            LazyColumn {
                items(results, key = { it.mac }) { found ->
                    ListItem(
                        headlineContent = { Text(found.name) },
                        supportingContent = { Text("${found.type.title} · сигнал ${found.rssi} dBm") },
                        modifier = Modifier.clickable { onPick(found) },
                    )
                }
            }
        }
    }
}

@Composable
fun StationDialog(
    draft: StationDraft,
    onDismiss: () -> Unit,
    onSave: (Station) -> Unit,
) {
    var label by remember(draft) { mutableStateOf(draft.label) }
    var id by remember(draft) { mutableStateOf(draft.v1Id) }

    val isV1 = draft.type == StationType.V1
    val normalizedId = LighthouseProtocol.normalizeV1Id(id)
    val idInvalid = isV1 && normalizedId == null
    val nameSuffix = if (isV1) LighthouseProtocol.v1NameSuffix(draft.bleName).takeLast(4) else ""
    val suffixMismatch = isV1 && normalizedId != null && nameSuffix.isNotEmpty() &&
        !normalizedId.endsWith(nameSuffix)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (draft.isNew) "Добавить станцию" else "Изменить станцию") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "${draft.bleName} · ${draft.type.title}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Название") },
                    placeholder = { Text("Например, «Левая»") },
                    singleLine = true,
                )
                if (isV1) {
                    OutlinedTextField(
                        value = id,
                        onValueChange = { input ->
                            id = input.uppercase()
                                .removePrefix("0X")
                                .filter { it in '0'..'9' || it in 'A'..'F' }
                                .take(8)
                        },
                        label = { Text("ID станции") },
                        placeholder = { Text("8 символов, например 1A2B3C4D") },
                        singleLine = true,
                        isError = id.isNotEmpty() && idInvalid,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        supportingText = {
                            Text(
                                when {
                                    id.isEmpty() -> "Напечатан на задней стороне станции"
                                    idInvalid -> "Нужно ровно 8 символов: 0–9 и A–F"
                                    suffixMismatch -> "Имя станции заканчивается на $nameSuffix — проверьте ID"
                                    else -> "ID выглядит корректно"
                                }
                            )
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = label.isNotBlank() && !idInvalid,
                onClick = {
                    onSave(
                        Station(
                            mac = draft.mac,
                            bleName = draft.bleName,
                            type = draft.type,
                            label = label.trim(),
                            v1Id = if (isV1) normalizedId else null,
                        )
                    )
                },
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}
