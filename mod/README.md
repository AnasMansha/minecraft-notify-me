# Notify Me (mod)

A Forge 1.7.10 mod for GT New Horizons 2.9 that adds the **Redstone Notifier** block and a small link server that the
[Redstone Pager](../app) app connects to. For installing and using it, see the [main README](../README.md).

## Build
Needs JDK 21 (the GTNH Gradle setup compiles down to Java 8).
```bash
./gradlew spotlessApply build      # -> build/libs/notifyme-<version>.jar
./gradlew runClient                # dev client
./gradlew runServer                # dev server (run/server)
```
The version is set in `build.gradle.kts`, not taken from git tags.

## How it works
- **`block/`**: `BlockNotifier`, `TileNotifier` and `ItemBlockNotifier`.
  - The tile entity only stores a UUID.
  - Config and state live in `NotifierRegistry` (`data/notifyme.dat`), so phones can see notifiers in unloaded chunks too.
  - Breaking the block drops an item that keeps the UUID and settings.
- **`link/LinkServer`**: a WebSocket endpoint `ws://<host>:25570/ws`. It uses the Netty 4.0 bundled with Minecraft, so there are no extra libraries. Netty threads hand every message to the server thread through `NotifyMe.runOnServer`.
- **`link/LinkManager`**: phone sessions and authentication, alert delivery, offline queues and acknowledgements.
  - Authentication is an EC P-256 challenge-response with the phone's key pinned on first connect.
  - The phone's **callsign is derived from its public key**, so the server can verify it.
- **`link/AlertEngine`**: trigger mode, threshold, cooldown, anti-spam cap and the "While ON" repeats.
- **`link/Beacon`**: LAN discovery on UDP 25571.
  - It broadcasts every 2 s and answers phone probes immediately.
  - Its `invite` list tells phones that a notifier linked them but they aren't connected yet. The app turns that into an **Accept** prompt.
- **`gui/`**: the right-click GUI (flat widgets, the strength meter, drag sliders, pixel icon buttons).
- **`util/Callsign`**: `Adjective Noun NN`, from SHA-256(public key). It must match the app and `protocol/callsign_ref.py`.

## Tools
- `tools/gen_textures.py` regenerates the block textures.
- `tools/gen_app_icon.py` turns the block texture into the app's launcher icon.
- `tools/LinkTestClient.java` is a scripted fake phone for end-to-end tests against `runServer`:
  ```bash
  java -cp build/libs/notifyme-<v>-dev.jar tools/LinkTestClient.java 127.0.0.1 25570 callsign.txt
  ```
