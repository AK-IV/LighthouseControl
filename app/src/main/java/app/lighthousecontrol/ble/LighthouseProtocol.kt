package app.lighthousecontrol.ble

import app.lighthousecontrol.data.StationPowerState
import app.lighthousecontrol.data.StationType
import java.util.UUID

enum class PowerAction { ON, SLEEP, STANDBY }

/**
 * Протоколы управления питанием базовых станций по BLE.
 *
 * v2.0 (Valve Index и др.): один байт в характеристику питания.
 *   0x00 — сон, 0x01 — включить, 0x02 — standby (мотор крутится, лазеры выключены).
 *
 * v1.0 (HTC Vive): 20-байтная команда в характеристику 0000cb01-...
 *   Включить: 12 00 00 00 + ID (4 байта, little-endian) + 12 нулей
 *   Сон:      12 02 00 01 + ID (4 байта, little-endian) + 12 нулей
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

    /** Приводит ID к виду «1A2B3C4D». Возвращает null, если это не 8 hex-символов. */
    fun normalizeV1Id(raw: String): String? {
        val s = raw.trim().uppercase().removePrefix("0X")
        return if (s.length == 8 && s.all(::isHex)) s else null
    }

    /** Hex-хвост из имени «HTC BS XXXX» — для подсказки, что ID введён верно. */
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
        val normalized = requireNotNull(normalizeV1Id(id)) { "Некорректный ID станции: $id" }
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
                throw IllegalArgumentException("Станции v1.0 не поддерживают standby")
        }
        for (i in 0 until 4) {
            cmd[4 + i] = ((idValue shr (8 * i)) and 0xFF).toByte()
        }
        return cmd
    }

    /** Разбор значения, прочитанного из характеристики питания v2.0. */
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
