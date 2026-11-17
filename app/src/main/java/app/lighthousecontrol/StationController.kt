package app.lighthousecontrol

import app.lighthousecontrol.ble.LighthouseBle
import app.lighthousecontrol.ble.PowerAction
import app.lighthousecontrol.data.Station
import app.lighthousecontrol.data.StationPowerState
import app.lighthousecontrol.data.StationRepository
import app.lighthousecontrol.data.StationType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class StationStatus(
    val power: StationPowerState = StationPowerState.UNKNOWN,
    val busy: Boolean = false,
    val error: String? = null,
)

/**
 * Single entry point for controlling stations, shared by the screen and the Quick Settings tile.
 * Keeps the current status of each station (by MAC) for display in the UI.
 */
class StationController(
    private val repository: StationRepository,
    private val ble: LighthouseBle,
) {
    private val _status = MutableStateFlow<Map<String, StationStatus>>(emptyMap())
    val status: StateFlow<Map<String, StationStatus>> = _status.asStateFlow()

    /** Sends a command to a single station. Returns true on success. */
    suspend fun apply(station: Station, action: PowerAction): Boolean = runOp(station) {
        ble.sendPower(station, action)
        when (action) {
            PowerAction.ON -> StationPowerState.ON
            PowerAction.SLEEP -> StationPowerState.SLEEP
            PowerAction.STANDBY -> StationPowerState.STANDBY
        }
    }

    /** Sends a command to all stations. Returns the number of stations that didn't respond. */
    suspend fun applyToAll(action: PowerAction): Int {
        repository.lastAllAction = action
        var failed = 0
        for (station in repository.stations.value) {
            // v1.0 has no standby, so use sleep instead.
            val effective =
                if (action == PowerAction.STANDBY && station.type == StationType.V1) PowerAction.SLEEP
                else action
            if (!apply(station, effective)) failed++
        }
        return failed
    }

    /** Reads the actual state (v2.0 only; v1.0 doesn't report it). */
    suspend fun refresh(station: Station) {
        if (station.type != StationType.V2) return
        runOp(station) { ble.readV2State(station) }
    }

    fun forget(mac: String) {
        _status.update { it - mac }
    }

    private suspend fun runOp(station: Station, op: suspend () -> StationPowerState): Boolean {
        update(station.mac) { it.copy(busy = true, error = null) }
        return try {
            val newState = op()
            update(station.mac) { it.copy(busy = false, power = newState) }
            true
        } catch (e: CancellationException) {
            update(station.mac) { it.copy(busy = false) }
            throw e
        } catch (e: Exception) {
            update(station.mac) {
                it.copy(busy = false, error = e.message ?: e.javaClass.simpleName)
            }
            false
        }
    }

    private fun update(mac: String, f: (StationStatus) -> StationStatus) {
        _status.update { map -> map + (mac to f(map[mac] ?: StationStatus())) }
    }
}
