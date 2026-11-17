package app.lighthousecontrol.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import app.lighthousecontrol.R
import kotlinx.coroutines.CompletableDeferred
import java.io.IOException
import java.util.UUID

/**
 * A single GATT connection to a station, wrapped in suspend functions.
 * Usage: connect() → discover() → write()/read() → close().
 * Each operation waits for its own callback, so everything runs strictly in sequence.
 */
@SuppressLint("MissingPermission")
internal class GattSession(
    private val context: Context,
    private val device: BluetoothDevice,
) : BluetoothGattCallback() {

    private var gatt: BluetoothGatt? = null
    private val connected = CompletableDeferred<Unit>()
    private val discovered = CompletableDeferred<Unit>()

    @Volatile private var pendingWrite: CompletableDeferred<Unit>? = null
    @Volatile private var pendingRead: CompletableDeferred<ByteArray>? = null

    suspend fun connect() {
        gatt = device.connectGatt(context, false, this, BluetoothDevice.TRANSPORT_LE)
            ?: throw IOException(context.getString(R.string.err_connect_start))
        connected.await()
    }

    suspend fun discover() {
        val g = requireGatt()
        if (!g.discoverServices()) throw IOException(context.getString(R.string.err_discover_start))
        discovered.await()
    }

    suspend fun write(uuid: UUID, value: ByteArray) {
        val g = requireGatt()
        val ch = findCharacteristic(uuid)
        val writeType =
            if ((ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }

        val result = CompletableDeferred<Unit>()
        pendingWrite = result
        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(ch, value, writeType) == BluetoothStatusCodes.SUCCESS
        } else {
            legacyWrite(g, ch, value, writeType)
        }
        if (!started) throw IOException(context.getString(R.string.err_write_start))
        result.await()
    }

    suspend fun read(uuid: UUID): ByteArray {
        val g = requireGatt()
        val ch = findCharacteristic(uuid)
        val result = CompletableDeferred<ByteArray>()
        pendingRead = result
        if (!g.readCharacteristic(ch)) throw IOException(context.getString(R.string.err_read_start))
        return result.await()
    }

    fun close() {
        val g = gatt ?: return
        gatt = null
        runCatching { g.disconnect() }
        runCatching { g.close() }
        failAll(IOException(context.getString(R.string.err_closed)))
    }

    // --- internals ---

    private fun requireGatt(): BluetoothGatt = gatt ?: throw IOException(context.getString(R.string.err_not_connected))

    private fun findCharacteristic(uuid: UUID): BluetoothGattCharacteristic =
        requireGatt().services?.firstNotNullOfOrNull { it.getCharacteristic(uuid) }
            ?: throw IOException(context.getString(R.string.err_char_missing))

    @Suppress("DEPRECATION")
    private fun legacyWrite(
        g: BluetoothGatt,
        ch: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: Int,
    ): Boolean {
        ch.writeType = writeType
        ch.value = value
        return g.writeCharacteristic(ch)
    }

    private fun failAll(e: Throwable) {
        connected.completeExceptionally(e)
        discovered.completeExceptionally(e)
        pendingWrite?.completeExceptionally(e)
        pendingRead?.completeExceptionally(e)
    }

    // --- callbacks (delivered on Binder threads) ---

    override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
            connected.complete(Unit)
        } else {
            failAll(IOException(context.getString(R.string.err_link_lost, status)))
        }
    }

    override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            discovered.complete(Unit)
        } else {
            discovered.completeExceptionally(IOException(context.getString(R.string.err_discover, status)))
        }
    }

    override fun onCharacteristicWrite(
        g: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int,
    ) {
        val d = pendingWrite ?: return
        if (status == BluetoothGatt.GATT_SUCCESS) {
            d.complete(Unit)
        } else {
            d.completeExceptionally(IOException(context.getString(R.string.err_write, status)))
        }
    }

    /** Android 13+. */
    override fun onCharacteristicRead(
        g: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int,
    ) {
        handleRead(value, status)
    }

    /** Android 12 and below. */
    @Deprecated("Used only below Android 13")
    @Suppress("DEPRECATION")
    override fun onCharacteristicRead(
        g: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            handleRead(characteristic.value ?: ByteArray(0), status)
        }
    }

    private fun handleRead(value: ByteArray, status: Int) {
        val d = pendingRead ?: return
        if (status == BluetoothGatt.GATT_SUCCESS) {
            d.complete(value)
        } else {
            d.completeExceptionally(IOException(context.getString(R.string.err_read, status)))
        }
    }
}
