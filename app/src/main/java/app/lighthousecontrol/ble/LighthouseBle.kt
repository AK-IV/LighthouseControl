package app.lighthousecontrol.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import app.lighthousecontrol.data.Station
import app.lighthousecontrol.data.StationPowerState
import app.lighthousecontrol.data.StationType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.io.IOException

data class ScannedStation(
    val mac: String,
    val name: String,
    val type: StationType,
    val rssi: Int,
)

/**
 * Сканирование и отправка команд базовым станциям.
 * Все GATT-операции идут через один Mutex: Android плохо переносит
 * параллельные подключения, поэтому станции обрабатываются по одной.
 */
@SuppressLint("MissingPermission")
class LighthouseBle(private val context: Context) {

    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    private val mutex = Mutex()

    val isEnabled: Boolean get() = adapter?.isEnabled == true

    /** Поток найденных станций. Сканирование идёт, пока поток собирается. */
    fun scan(): Flow<ScannedStation> = callbackFlow {
        val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
        if (scanner == null) {
            close(IOException("Bluetooth выключен"))
            return@callbackFlow
        }
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.scanRecord?.deviceName ?: result.device.name ?: return
                val type = LighthouseProtocol.typeFromName(name) ?: return
                trySend(ScannedStation(result.device.address, name, type, result.rssi))
            }

            override fun onScanFailed(errorCode: Int) {
                close(IOException("Ошибка сканирования (код $errorCode)"))
            }
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(null, settings, callback)
        awaitClose { runCatching { scanner.stopScan(callback) } }
    }

    suspend fun sendPower(station: Station, action: PowerAction) {
        val (uuid, payload) = when (station.type) {
            StationType.V2 ->
                LighthouseProtocol.V2_POWER to LighthouseProtocol.v2Command(action)
            StationType.V1 -> {
                val id = station.v1Id ?: throw IOException("Не задан ID станции")
                LighthouseProtocol.V1_CONTROL to LighthouseProtocol.v1Command(action, id)
            }
        }
        mutex.withLock {
            withRetry { withSession(station.mac) { it.write(uuid, payload) } }
        }
    }

    /** Чтение текущего состояния. Работает только для v2.0. */
    suspend fun readV2State(station: Station): StationPowerState {
        require(station.type == StationType.V2)
        return mutex.withLock {
            withRetry {
                withSession(station.mac) {
                    LighthouseProtocol.parseV2State(it.read(LighthouseProtocol.V2_POWER))
                }
            }
        }
    }

    private suspend fun <T> withSession(mac: String, block: suspend (GattSession) -> T): T {
        val a = adapter?.takeIf { it.isEnabled } ?: throw IOException("Bluetooth выключен")
        val session = GattSession(context, a.getRemoteDevice(mac))
        try {
            return withTimeout(SESSION_TIMEOUT_MS) {
                session.connect()
                session.discover()
                block(session)
            }
        } finally {
            session.close()
        }
    }

    /** BLE на Android часто сбоит с первого раза (ошибка 133 и т. п.), поэтому повторяем. */
    private suspend fun <T> withRetry(block: suspend () -> T): T {
        var last: Throwable? = null
        repeat(ATTEMPTS) { attempt ->
            try {
                return block()
            } catch (e: TimeoutCancellationException) {
                last = IOException("Станция не ответила")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                last = e
            }
            delay(RETRY_DELAY_MS * (attempt + 1))
        }
        throw last ?: IOException("Неизвестная ошибка")
    }

    private companion object {
        const val ATTEMPTS = 3
        const val RETRY_DELAY_MS = 500L
        const val SESSION_TIMEOUT_MS = 10_000L
    }
}
