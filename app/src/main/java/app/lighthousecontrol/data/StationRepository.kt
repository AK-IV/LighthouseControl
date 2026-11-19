package app.lighthousecontrol.data

import android.content.Context
import app.lighthousecontrol.ble.PowerAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import androidx.core.content.edit

/**
 * Stores the station list (MAC, type, name, v1 ID) in SharedPreferences.
 * The data survives app and device restarts.
 */
class StationRepository(context: Context) {

    private val prefs = context.getSharedPreferences("stations", Context.MODE_PRIVATE)

    private val _stations = MutableStateFlow(load())
    val stations: StateFlow<List<Station>> = _stations.asStateFlow()

    /** Last "all stations" command; the tile uses it to decide whether to turn on or sleep. */
    var lastAllAction: PowerAction?
        get() = prefs.getString(KEY_LAST_ALL, null)
            ?.let { runCatching { PowerAction.valueOf(it) }.getOrNull() }
        set(value) {
            prefs.edit { putString(KEY_LAST_ALL, value?.name) }
        }

    fun upsert(station: Station) {
        _stations.update { list ->
            if (list.any { it.mac == station.mac }) {
                list.map { if (it.mac == station.mac) station else it }
            } else {
                list + station
            }
        }
        save()
    }

    fun remove(mac: String) {
        _stations.update { list -> list.filterNot { it.mac == mac } }
        save()
    }

    private fun save() {
        val array = JSONArray()
        _stations.value.forEach { s ->
            array.put(
                JSONObject()
                    .put("mac", s.mac)
                    .put("bleName", s.bleName)
                    .put("type", s.type.name)
                    .put("label", s.label)
                    .put("v1Id", s.v1Id ?: JSONObject.NULL)
            )
        }
        prefs.edit { putString(KEY_STATIONS, array.toString()) }
    }

    private fun load(): List<Station> {
        val raw = prefs.getString(KEY_STATIONS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val o = array.getJSONObject(i)
                val type = runCatching { StationType.valueOf(o.getString("type")) }.getOrNull()
                    ?: return@mapNotNull null
                Station(
                    mac = o.getString("mac"),
                    bleName = o.optString("bleName"),
                    type = type,
                    label = o.optString("label"),
                    v1Id = if (o.isNull("v1Id")) null else o.getString("v1Id"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val KEY_STATIONS = "stations_json"
        const val KEY_LAST_ALL = "last_all_action"
    }
}
