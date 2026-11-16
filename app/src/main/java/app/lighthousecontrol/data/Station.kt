package app.lighthousecontrol.data

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
enum class StationPowerState(val title: String) {
    UNKNOWN("—"),
    ON("Работает"),
    BOOTING("Запуск"),
    STANDBY("Standby"),
    SLEEP("Сон"),
}
