# Redstone Pager (Android app)

The phone side of [Notify Me](../README.md). It's written in Kotlin with Jetpack Compose and supports Android 8+, targeting Android 15.

## Build
Needs JDK 17+ and the Android SDK (platform 35). Point `local.properties` at the SDK:
```
sdk.dir=C:/path/to/Android/Sdk
```
Then:
```bash
./gradlew assembleDebug        # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # unit tests + screenshot tests (PNGs in app/screenshots, no emulator needed)
```

## Structure
| File | What it does |
|---|---|
| `Identity.kt` | An EC P-256 key pair in the Android Keystore. The callsign is derived from the public key. |
| `LinkService.kt` | A foreground service (`specialUse` type, which avoids Android 15's 6 h/day `dataSync` limit). It keeps one WebSocket per world (`ServerConnection.kt`), and runs a Wi-Fi lock and a network callback. |
| `AlertEngine.kt` | Tiers become notification channels. Urgent alerts repeat; Alarm uses a full-screen intent. It also handles quiet hours, the watchdog and invites. |
| `AlarmActivity.kt`, `AlarmController.kt` | The full-screen alarm: alarm-stream sound, vibration, flashlight strobe and text-to-speech. |
| `Discovery.kt` | The LAN scan (UDP probe plus beacon). |
| `LanWatcher.kt` | Background beacon listener that turns "linked in-game" into an Accept prompt. |
| `Store.kt` | App state as StateFlows, persisted as JSON in SharedPreferences. |
| `ui/` | The screens and the Minecraft/redstone theme (`Theme.kt`). |

The app talks directly to the mod. The protocol is described in [protocol/PROTOCOL.md](../protocol/PROTOCOL.md).
