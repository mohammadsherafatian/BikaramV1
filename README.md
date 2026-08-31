# بیکارم! (Bikaram)

Bikaram is a lightweight, offline Android fidget game built around two stylized hanging objects and intentionally silly pendulum physics. The visual treatment is playful and non-explicit. It uses the Android Canvas directly and has no runtime libraries, ads, analytics, network client, background service, or native binary.

## Features

- Home screen with clear routes to Classic play, Speed Mode, modes, challenges, records, achievements, skins, settings, and about
- Delta-time pendulum physics with damping, momentum, collision response, soft screen constraints, device tilt, and a rest-aware frame loop
- Normal, Zen, Rage, Office, Turbo, and Gravity modes configured through one `GameModeConfig` model
- Classic, Football, Coconut, Disco, Watermelon, Moon, and Ping Pong materials with distinct Canvas-rendered texture and shading
- Persisted sound, haptic, device-gravity, reduced-motion, and animation-intensity settings
- Daily challenges, a preserved 10-second two-player duel, classic run history, Speed records, and tap/Speed achievements
- Responsive portrait layouts based on the current view bounds; gameplay is intentionally portrait-locked so rotation cannot destroy an active timed run

## Speed Mode

Speed Mode begins with an animated `3, 2, 1, GO` countdown and runs continuous timed missions. Finishing early carries unused time into the next mission, capped at 15 seconds so difficulty cannot be bypassed by unlimited accumulation.

The catalog contains 20 data-driven templates: left/right targets, combined totals, per-side goals, exact-side rules, alternating patterns, ordered sequences, and asymmetric targets. After mission 20, `SpeedMissionCatalog` varies targets and time using the mission index while enforcing a feasible minimum time. Mission rules are evaluated by `SpeedMissionEngine`, rather than UI-specific `if/else` chains.

Results persist completed missions, total taps, duration, score, skin ID/name, date, highest difficulty, and maximum carry. Records can be filtered by All, Classic, Speed, or Challenge.

## Architecture

- `MainActivity`: lifecycle owner and MediaStore share boundary
- `BoredomView`: navigation, touch handling, Canvas rendering, audio/haptic dispatch, and lifecycle-aware frame scheduling
- `PhysicsBall`: frame-rate-independent pendulum integration and soft motion constraints
- `GameModeConfig`: centralized physics/audio parameters for every mode
- `GamePreferences`: centralized `SharedPreferences` access, old-key-compatible statistics, settings, achievements, and records
- `SpeedMission`, `SpeedMissionCatalog`, `SpeedMissionEngine`: data, generation, and independently testable rule evaluation
- `GameRecord`: compact local record serialization

The `Choreographer` callback applies clamped elapsed time. It stops when both objects are resting and there are no timers, particles, or messages to animate. Activity pause removes the callback, unregisters the accelerometer, flushes batched tap statistics, and therefore pauses Speed and daily-challenge timers fairly.

## Build

Requirements:

- JDK 17 or 21
- Android SDK Platform 35
- Android SDK Build Tools 35 (AGP may also select its compatible 34.x tool internally)

The repository includes Gradle Wrapper 8.9:

```bash
./gradlew testDebugUnitTest assembleDebug
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Run a clean release build with:

```bash
./gradlew clean testDebugUnitTest assembleRelease
```

Without release credentials, Gradle produces an unsigned release APK suitable for signing later. With all four signing values below, the release variant is signed automatically.

## Release signing

Never commit a keystore or password. Generate a 4096-bit RSA upload/release key and store it outside this repository:

```bash
keytool -genkeypair -v \
  -keystore /secure/location/bikaram-release.jks \
  -alias bikaram-release \
  -keyalg RSA -keysize 4096 -validity 10000
```

Supply credentials using environment variables (or private Gradle properties with the same names):

```bash
export BIKARAM_KEYSTORE_PATH=/secure/location/bikaram-release.jks
export BIKARAM_KEYSTORE_PASSWORD='...'
export BIKARAM_KEY_ALIAS=bikaram-release
export BIKARAM_KEY_PASSWORD='...'
./gradlew assembleRelease
```

Signed release output (when credentials are present):

```text
app/build/outputs/apk/release/app-release.apk
```

Unsigned release output (when credentials are absent):

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

Verify the APK and certificate with the SDK tools:

```bash
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
keytool -printcert -jarfile app/build/outputs/apk/release/app-release.apk
```

Keep production keys backed up offline. Losing the signing key can prevent updates to an already distributed application.

## Security and permissions

The manifest requests only `android.permission.VIBRATE`, which is used for optional gameplay haptics and can be fully disabled in Settings. There is no Internet permission, storage permission, install-package permission, overlay, accessibility service, boot receiver, background service, WebView, dynamic code loading, reflection, or bundled native code. Cleartext traffic and Android backup are disabled. Screenshot sharing writes through scoped `MediaStore` on Android 10+, so no broad storage permission is needed.

Application metadata:

- Package: `com.bikaram.toy`
- Version: `1.1.0` (`versionCode 2`)
- Minimum SDK: 29
- Target/compile SDK: 35
- Java: 17 source/target compatibility

## Tests

The unit suite covers mission catalog integrity, exact and alternating rule failure, per-side completion, post-catalog feasibility/scaling, delta-time physics consistency, extreme-motion constraints, malformed record handling, and complete Speed-record serialization.
