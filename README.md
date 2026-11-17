# Lighthouse Control

An Android app (Kotlin + Jetpack Compose) for controlling the power state of SteamVR base stations over Bluetooth LE.

- **v1.0 (HTC Vive):** on / sleep. Requires the station ID (8 characters from the sticker on the back), entered once.
- **v2.0 (Valve Index and others):** on / standby / sleep, plus reading the actual power state.
- Stations, their names and IDs are saved.
- "Turn all on" / "Sleep all" buttons.
- "Base stations" Quick Settings tile: a single tap turns all stations on or puts them to sleep.
- UI available in English, German, Spanish, French, Japanese, Russian, Ukrainian and Simplified Chinese.

## Getting started

1. Open the project folder in Android Studio (Ladybug 2024.2 or newer) and wait for Gradle Sync to finish.
2. Connect a phone (Android 8.0+) with USB debugging enabled and press Run.
3. In the app, tap "Add", pick a discovered station and give it a name (for v1.0, also enter its ID).
4. Add the tile manually: pull down Quick Settings → "Edit" → drag "Base stations" into place.

## Project structure

```
app/src/main/java/app/lighthousecontrol/
├── ble/LighthouseProtocol.kt   — v1/v2 command bytes, state parsing
├── ble/GattSession.kt          — a single coroutine-based GATT connection
├── ble/LighthouseBle.kt        — scanning, request queue (Mutex), retries on failure
├── data/                       — Station model and storage (SharedPreferences)
├── StationController.kt        — shared logic for the screen and the tile
├── work/PowerAllWorker.kt      — "all stations" commands in the background via WorkManager
├── tile/PowerTileService.kt    — Quick Settings tile
└── ui/                         — Compose screens
```

## Protocol

| | Characteristic | On | Sleep | Standby |
|---|---|---|---|---|
| v2.0 | `00001525-1212-efde-1523-785feabcd124` | `01` | `00` | `02` |
| v1.0 | `0000cb01-0000-1000-8000-00805f9b34fb` | `12 00 00 00` + ID(LE) + 12×`00` | `12 02 00 01` + ID(LE) + 12×`00` | — |

The v1.0 ID is written in reverse byte order: `1A2B3C4D` → `4D 3C 2B 1A`.

## Known limitations

- v1.0 stations don't report their state, so the app shows the last command sent (marked with an asterisk).
- The tile toggles "all on ↔ all asleep" based on the last command, not the actual state.
- On Android 11 and below, location services must be enabled for BLE scanning.
