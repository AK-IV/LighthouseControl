package app.lighthousecontrol.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.lighthousecontrol.BlePermissions
import app.lighthousecontrol.R
import app.lighthousecontrol.StationStatus
import app.lighthousecontrol.ble.PowerAction
import app.lighthousecontrol.data.Station
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val stations by vm.stations.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val scanResults by vm.scanResults.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()

    var showScan by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<StationDraft?>(null) }
    var deleting by remember { mutableStateOf<Station?>(null) }

    // --- permissions and enabling Bluetooth ---
    var afterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }
    val enableBluetooth = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }
    val requestPermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val action = afterPermission
        afterPermission = null
        if (result.values.all { it }) {
            action?.invoke()
        } else {
            scope.launch { snackbar.showSnackbar(context.getString(R.string.msg_bluetooth_required)) }
        }
    }

    /** Runs an action after checking permissions and that Bluetooth is enabled. */
    fun withBluetooth(action: () -> Unit) {
        if (!BlePermissions.granted(context)) {
            afterPermission = action
            requestPermissions.launch(BlePermissions.required)
            return
        }
        if (!vm.bluetoothEnabled) {
            enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }
        action()
    }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }
    // Read the actual state of v2.0 stations when the app opens.
    LaunchedEffect(Unit) {
        if (BlePermissions.granted(context) && vm.bluetoothEnabled) vm.refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_stations)) },
                actions = {
                    if (stations.isNotEmpty()) {
                        IconButton(onClick = { withBluetooth { vm.refresh() } }) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    withBluetooth {
                        showScan = true
                        vm.startScan()
                    }
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.action_add)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (stations.isEmpty()) {
            EmptyState(Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    AllStationsControls(
                        onAllOn = { withBluetooth { vm.powerAll(PowerAction.ON) } },
                        onAllSleep = { withBluetooth { vm.powerAll(PowerAction.SLEEP) } },
                    )
                }
                items(stations, key = { it.mac }) { station ->
                    StationCard(
                        station = station,
                        status = status[station.mac] ?: StationStatus(),
                        onAction = { action -> withBluetooth { vm.power(station, action) } },
                        onEdit = { editing = StationDraft.fromStation(station) },
                        onDelete = { deleting = station },
                    )
                }
            }
        }
    }

    if (showScan) {
        ScanSheet(
            results = scanResults.filter { found -> stations.none { it.mac == found.mac } },
            scanning = scanning,
            onRescan = { vm.startScan() },
            onPick = { editing = StationDraft.fromScan(it) },
            onDismiss = {
                vm.stopScan()
                showScan = false
            },
        )
    }

    editing?.let { draft ->
        StationDialog(
            draft = draft,
            onDismiss = { editing = null },
            onSave = {
                vm.saveStation(it)
                editing = null
            },
        )
    }

    deleting?.let { station ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_title, station.label)) },
            text = { Text(stringResource(R.string.delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeStation(station.mac)
                    deleting = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun AllStationsControls(onAllOn: () -> Unit, onAllSleep: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = onAllOn,
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
        ) { Text(stringResource(R.string.action_all_on)) }
        FilledTonalButton(
            onClick = onAllSleep,
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
        ) { Text(stringResource(R.string.action_all_sleep)) }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.empty_body),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
