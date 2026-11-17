package app.lighthousecontrol.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.lighthousecontrol.R
import app.lighthousecontrol.ble.PowerAction
import app.lighthousecontrol.ble.ScannedStation
import app.lighthousecontrol.data.Station
import app.lighthousecontrol.data.StationType
import app.lighthousecontrol.lighthouseApp
import app.lighthousecontrol.tile.PowerTileService
import app.lighthousecontrol.work.PowerAllWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application.lighthouseApp

    val stations = app.repository.stations
    val status = app.controller.status

    private val _scanResults = MutableStateFlow<List<ScannedStation>>(emptyList())
    val scanResults: StateFlow<List<ScannedStation>> = _scanResults.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    private var scanJob: Job? = null

    val bluetoothEnabled: Boolean get() = app.ble.isEnabled

    fun startScan() {
        scanJob?.cancel()
        _scanResults.value = emptyList()
        scanJob = viewModelScope.launch {
            _scanning.value = true
            try {
                withTimeoutOrNull(SCAN_DURATION_MS) {
                    app.ble.scan().collect { found ->
                        _scanResults.update { list ->
                            if (list.any { it.mac == found.mac }) {
                                list.map { if (it.mac == found.mac) found else it }
                            } else {
                                list + found
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit(e.message ?: app.getString(R.string.err_scan))
            } finally {
                _scanning.value = false
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
    }

    fun saveStation(station: Station) {
        app.repository.upsert(station)
    }

    fun removeStation(mac: String) {
        app.repository.remove(mac)
        app.controller.forget(mac)
    }

    fun power(station: Station, action: PowerAction) {
        viewModelScope.launch {
            if (!app.controller.apply(station, action)) {
                _messages.tryEmit(app.getString(R.string.msg_command_failed, station.label))
            }
        }
    }

    fun powerAll(action: PowerAction) {
        app.repository.lastAllAction = action
        PowerAllWorker.enqueue(getApplication<Application>(), action)
        PowerTileService.requestUpdate(getApplication<Application>())
    }

    /** Reads the actual state of v2.0 stations. */
    fun refresh() {
        viewModelScope.launch {
            stations.value
                .filter { it.type == StationType.V2 }
                .forEach { app.controller.refresh(it) }
        }
    }

    private companion object {
        const val SCAN_DURATION_MS = 12_000L
    }
}
