package app.lighthousecontrol

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import app.lighthousecontrol.ble.LighthouseBle
import app.lighthousecontrol.data.StationRepository

class LighthouseApp : Application() {

    lateinit var repository: StationRepository
        private set
    lateinit var ble: LighthouseBle
        private set
    lateinit var controller: StationController
        private set

    override fun onCreate() {
        super.onCreate()
        repository = StationRepository(this)
        ble = LighthouseBle(this)
        controller = StationController(repository, ble)
    }
}

val Context.lighthouseApp: LighthouseApp get() = applicationContext as LighthouseApp

object BlePermissions {
    val required: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun granted(context: Context): Boolean = required.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}
