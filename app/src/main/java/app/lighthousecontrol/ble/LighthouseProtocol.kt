package app.lighthousecontrol.ble

import app.lighthousecontrol.data.StationPowerState
import app.lighthousecontrol.data.StationType
import java.util.UUID

enum class PowerAction { ON, SLEEP, STANDBY }

/**
 * BLE power control protocols for base stations.
 *
 * v2.0 (Valve Index and others): a single byte written to the power characteristic.
 *   0x00 — sleep, 0x01 — on, 0x02 — standby (motor spinning, lasers off).
 *
 * v1.0 (HTC Vive): a 20-byte command written to characteristic 0000cb01-...
 *   On:    12 00 00 00 + ID (4 bytes, little-endian) + 12 zero bytes
 *   Sleep: 12 02 00 01 + ID (4 bytes, little-endian) + 12 zero bytes
 */
object LighthouseProtocol {

    val V2_POWER: UUID = UUID.fromString("00001525-1212-efde-1523-785feabcd124")
    val V1_CONTROL: UUID = UUID.fromString("0000cb01-0000-1000-8000-00805f9b34fb")

    private const val V1_PREFIX = "HTC BS"
    private const val V2_PREFIX = "LHB-"

    fun typeFromName(name: String?): StationType? = when {
        name == null -> null
        name.startsWith(V2_PREFIX) -> StationType.V2
        name.startsWith(V1_PREFIX) -> StationType.V1
        else -> null
    }

    private fun isHex(c: Char) = c in '0'..'9' || c in 'A'..'F'

    /** Normalizes an ID to the "1A2B3C4D" form. Returns null if it isn't 8 hex characters. */
    fun normalizeV1Id(raw: String): String? {
        val s = raw.trim().uppercase().removePrefix("0X")
        return if (s.length == 8 && s.all(::isHex)) s else null
    }

    /** Hex suffix of the "HTC BS XXXX" name, used to hint whether the ID was entered correctly. */
    fun v1NameSuffix(bleName: String): String =
        bleName.removePrefix(V1_PREFIX).trim().uppercase().takeLastWhile(::isHex)

    fun v2Command(action: PowerAction): ByteArray {
        val value = when (action) {
            PowerAction.SLEEP -> 0x00
            PowerAction.ON -> 0x01
            PowerAction.STANDBY -> 0x02
        }
        return byteArrayOf(value.toByte())
    }

    fun v1Command(action: PowerAction, id: String): ByteArray {
        val normalized = requireNotNull(normalizeV1Id(id)) { "Invalid station ID: $id" }
        val idValue = normalized.toLong(16)
        val cmd = ByteArray(20)
        cmd[0] = 0x12
        when (action) {
            PowerAction.ON -> {
                cmd[1] = 0x00; cmd[2] = 0x00; cmd[3] = 0x00
            }
            PowerAction.SLEEP -> {
                cmd[1] = 0x02; cmd[2] = 0x00; cmd[3] = 0x01
            }
            PowerAction.STANDBY ->
                throw IllegalArgumentException("v1.0 stations do not support standby")
        }
        for (i in 0 until 4) {
            cmd[4 + i] = ((idValue shr (8 * i)) and 0xFF).toByte()
        }
        return cmd
    }

    /** Parses a value read from the v2.0 power characteristic. */
    fun parseV2State(value: ByteArray?): StationPowerState {
        val b = value?.firstOrNull()?.toInt()?.and(0xFF) ?: return StationPowerState.UNKNOWN
        return when (b) {
            0x00 -> StationPowerState.SLEEP
            0x02 -> StationPowerState.STANDBY
            0x0B -> StationPowerState.ON
            0x01, 0x08, 0x09 -> StationPowerState.BOOTING
            else -> StationPowerState.UNKNOWN
        }
    }
}
