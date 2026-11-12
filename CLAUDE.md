# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

"온길 (On-Gil)": an in-car Android app for Hyundai's Pleos Connect (Android Automotive OS, landscape 2560x1440, 160dpi) built for a contest. Single Gradle module `:app`, package `com.example.hyundai`, Kotlin 2.0.0 + Jetpack Compose (BOM 2024.04.01), AGP 8.8.2, compileSdk 35. The repo also holds research datasets (`data/`, `dcx_collector/`, `src/`) that are unrelated to the app build. Product plan: `PROJECT_PLAN.md`, overview: `README.md` (parts of README describe planned or other-session work; trust the code).

Internal identifiers still say `Todak` (`TodakColors`, `TodakModals`, `TodakGilDashboard`) from the old name 토닥길. User-visible text must say 온길.

## Commands

Windows + PowerShell. Gradle needs Android Studio's JDK:

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.hyundai.nav.RouteWindowTest"   # one test class
```

- A full build+test takes 1–3 minutes and the IDE shell tool times out around 2–5 minutes. Redirect output to a file (`*> "$env:TEMP\build.log"`), run in the background, then read the log tail.
- Unit tests are pure JVM (`org.json` is added as a real `testImplementation` because android.jar's version does not work on the JVM). Logic that must be tested lives in plain Kotlin (`nav/`, `domain/`), not in composables.
- Install/run on the emulator `Pleos_Connect_v2_1_0`: `adb install -r -t app\build\outputs\apk\debug\app-debug.apk` then `adb shell am start -n com.example.hyundai/.MainActivity`. IDE "deploy" sometimes does not reinstall; use adb.
- Emulator quirks: the stock navigation app (`ai.umos.maps.android.navigation.app`) periodically takes the foreground and respawns if you `force-stop` it. Use `adb emu geo fix <lng> <lat>` to move GPS; a fix far from the route (>2 km) makes the app run its simulated demo drive.
- The Pleos SDK artifacts come from the `nexus-playground.pleos.ai` Maven repo (see `settings.gradle.kts`).

## Architecture

Single-activity MVVM. `MainViewModel` owns one `AppUiState` (a big immutable data class: telemetry, nav, notes, pet, settings, modals, current `AppScreen`). `MainActivity` composes `DriveScreen` (always, map underneath) and overlays `ParkedScreen` / `NoteInboxScreen` / `SettingsScreen` / modals / `SimulatorDrawer` on top.

**Driving-safety rule drives the UI structure:** nothing may be operated by touch while the car moves. `domain/NoteBrowsing.kt` `NoteAccess.canBrowse(gear, speed)` (P gear, or speed < 0.5 km/h) gates the parked dashboard, inbox, settings and modals; the ViewModel closes them as soon as the car starts moving, and `MainActivity` re-checks it. Notes during driving are accepted by voice only (the simulator drawer's "음성 승인" stands in for it). Keep new screens behind this gate.

**Data flow of driving state:** `sdk/PleosSdkManager` wraps the five Pleos SDKs (Vehicle, FusedLocation, NaviHelper, TTS, STT) and reports through a `Listener` interface into the ViewModel. With no usable real location the ViewModel advances a simulated drive along the route each second (`advanceDemoDrive`); real locations are accepted only within 2 km of the route (`REAL_LOCATION_MAX_OFF_ROUTE_M`). `SimulatorDrawer` (hidden handle at the bottom) injects driving events for demos; the manner/safe/smooth scoring in `domain/MannerEvaluationEngine` is fed by it and by vehicle signals (`VehicleSignalTracker`).

**Navigation (`nav/`, `ui/map/NavMap.kt`):**
- Route polyline comes from the public OSRM server (`RouteRepository`, parsed by `OsrmRouteParser`); `RouteRepository.fallback()` is the offline demo route. NaviHelper only provides turn-by-turn and destination, not geometry. `RouteTrack` (GeoMath.kt) holds cumulative distances and projection.
- The map is MapLibre with a fixed close camera. `NavMap` tweens camera position/bearing/progress linearly over one second between 1 Hz nav updates, draws the car as a fixed overlay at the camera center, and erases the driven part of the route with a `line-gradient` trim (`RouteWindow` decides the uploaded window and the trim fraction). Do not re-`setGeoJson` the route every second and do not use `easeCamera` per update; both caused visible stutter or vanishing line segments.
- Day/night map is chosen by `DayNight` (NOAA solar elevation, optionally vehicle light signal) and the `MapTheme` setting; styles are `assets/todak_map_style.json` (night) and `todak_map_day.json`.
- Measured: an always-animating composable pet over the map dropped map FPS from ~25 to ~8 on the emulator. Keep the drive-screen pet static unless a note is being read.

**Pleos SDK facts learned the hard way** (all in `PleosSdkManager`): `FUSED_LOCATION` is a runtime ("dangerous") permission and its listener receives `LocationData`, not `Location`; TTS never emits `onReady`, so speak optimistically and fall back to Android TTS on error; STT needs `Locale.KOREA` (`ko_KR`); `TextToSpeech.initialize(listener)` fails with a lateinit error, use `initialize()` then attach the event listener with retries.

**Notes/pet domain:** `AiNoteModerator` (rule-based block/purify/mask PII + category and lifetime), `NoteInbox` helpers, one pet "제로" (`ui/pet/PetDog.kt`, drawn on a Canvas; `PetModel` is the composable). A note being read sets `heldNote` and the pet holds it. Settings persist in `SettingsStore` (SharedPreferences). All note data is local seed data; there is no backend.

## Working conventions here

- UI text is Korean, short, no emoji, minimum 14sp; palette for non-map UI is limited to `TodakColors` (Hyundai blue, sand, one accent). The navigation screen (`NavTheme`) is exempt.
- Editing Kotlin files that contain Korean string literals: exact-match edit tools fail on mistyped Hangul. Copy the text from a fresh read instead of retyping.
- Another agent session may edit the same tree at the same time; re-read a file before editing if a tool reports it changed.
