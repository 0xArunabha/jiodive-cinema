# JioDive Cinema (Android)

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Native Android stereoscopic VR cinema player (`com.jiodive.cinema`) built with Jetpack Compose, Media3 ExoPlayer, and OpenGL ES 3.0 for JioDive headsets.

---

## Architecture & Tech Stack

- **Companion Launcher ([MainActivity.kt](android-app/app/src/main/java/com/jiodive/cinema/MainActivity.kt)):** Portrait Jetpack Compose UI with Library & Theater tabs synced to a liquid bottom cradle dock ([CradleBottomBar.kt](android-app/app/src/main/java/com/jiodive/cinema/ui/components/CradleBottomBar.kt)), local device video scan/picker, network/HLS stream URL input, `.srt`/`.vtt` subtitle loading, and dark/light theme support.
- **Stereoscopic VR Player ([VrPlayerActivity.kt](android-app/app/src/main/java/com/jiodive/cinema/vr/VrPlayerActivity.kt)):** Fullscreen landscape OpenGL ES player powered by Media3 ExoPlayer, sensor head tracking (`HeadTracker`), curved virtual theater environments, and a gaze-interactive VR dock.
- **Android SDK Targets ([build.gradle.kts](android-app/app/build.gradle.kts)):** `minSdk = 28` (Android 9+), `targetSdk = 37`, `compileSdk = 37`, Java 11.

---

## Features

- **Virtual Theater Environments:** Switch between **Cinema Hall** (curved auditorium with screen light bounce), **OLED Void** (pure black theatre), and **Bed / Ceiling** (reclined overhead viewing).
- **Screen Presets:** **IMAX** (`6.0 m`, `52°` curve), **Standard** (`6.8 m`, `36°` curve), and **Balcony** (`9.8 m`, `26°` curve).
- **Video Formats & Eye Order:** `2D`, `Half-SBS`, `Full-SBS`, `Half-OU`, and `Full-OU` with live **Eye Swap**.
- **JioDive Optics & Thermal Controls:** Calibrated defaults (`62 mm` IPD, `80°` FOV, Brown-Conrady barrel correction `k1 = 0.101`, `k2 = 0.098`, edge sharpening, `1150 ms` optional gaze dwell, 60Hz display preference lock, and thermal brightness cap).
- **Subtitles:** Local `.srt` and `.vtt` parsing ([SrtVttParser.kt](android-app/app/src/main/java/com/jiodive/cinema/data/SrtVttParser.kt)).

---

## Building from Source

Open the `android-app/` folder in **Android Studio**, or build from the command line:

```powershell
cd android-app
.\gradlew.bat testDebugUnitTest assembleDebug assembleRelease
```

APK outputs:
- Debug: `android-app/app/build/outputs/apk/debug/app-debug.apk`
- Release: `android-app/app/build/outputs/apk/release/app-release.apk`

---

## License

This project is licensed under the MIT License — see [LICENSE](LICENSE).
