package app.lighthousecontrol.data

import androidx.annotation.StringRes
import app.lighthousecontrol.R

/** Base station version. */
enum class StationType(val title: String) {
    V1("v1.0"),
    V2("v2.0"),
}

/** A saved station. */
data class Station(
    /** MAC address, used to connect without scanning again. */
    val mac: String,
    /** The station's Bluetooth name (HTC BS ... / LHB-...). */
    val bleName: String,
    val type: StationType,
    /** User-defined name ("Left", "Right"). */
    val label: String,
    /** v1.0 only: 8 hex characters from the sticker on the back. */
    val v1Id: String? = null,
)

/** Station power state. */
enum class StationPowerState(@StringRes val titleRes: Int) {
    UNKNOWN(R.string.power_state_unknown),
    ON(R.string.power_state_on),
    BOOTING(R.string.power_state_booting),
    STANDBY(R.string.power_state_standby),
    SLEEP(R.string.power_state_sleep),
}
