package app.lighthousecontrol.data

import androidx.annotation.StringRes
import app.lighthousecontrol.R

/** Версия базовой станции. */
enum class StationType(val title: String) {
    V1("v1.0"),
    V2("v2.0"),
}

/** Сохранённая станция. */
data class Station(
    /** MAC-адрес — по нему подключаемся без повторного сканирования. */
    val mac: String,
    /** Имя, под которым станция видна по Bluetooth (HTC BS ... / LHB-...). */
    val bleName: String,
    val type: StationType,
    /** Название, которое задал пользователь («Левая», «Правая»). */
    val label: String,
    /** Только для v1.0: 8 hex-символов с наклейки на задней стороне. */
    val v1Id: String? = null,
)

/** Состояние питания станции. */
enum class StationPowerState(@StringRes val titleRes: Int) {
    UNKNOWN(R.string.power_state_unknown),
    ON(R.string.power_state_on),
    BOOTING(R.string.power_state_booting),
    STANDBY(R.string.power_state_standby),
    SLEEP(R.string.power_state_sleep),
}
