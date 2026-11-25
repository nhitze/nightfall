# Nightfall for Android

This is Nightfall's native Android client, alongside the SwiftUI iOS app in the repository root.

## Requirements

- JDK 17
- Android SDK platform 35
- Android 8.0 / API 26 or newer

The Gradle wrapper pins Gradle 8.9. Set `ANDROID_HOME` if the SDK is not at `/opt/android-sdk`.

## Build and test

From this directory:

```bash
ANDROID_HOME=/opt/android-sdk ./gradlew testDebugUnitTest
ANDROID_HOME=/opt/android-sdk ./gradlew ktlintCheck
ANDROID_HOME=/opt/android-sdk ./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Audio and privacy

- Sound loops are read from the iOS app's shared source assets at `../Nightfall/Resources/Sounds`; the WAVs are packaged uncompressed for Android's asset descriptor.
- White, pink, and brown noise are generated locally as WAV data.
- No network permission or networking SDK is included.
- Preferences are stored in app-private SharedPreferences. Android backup is disabled.
- Android requires a foreground media-playback service and an ongoing system media-control notification while background playback is active. It is shown only for playback and provides play/pause/stop controls; it is not a promotional or alert notification.
- Audio never starts automatically on launch.

## Functional test checklist

Run unit tests first, then use a real Android device (API 26+) for the platform audio behaviors:

1. Fresh install opens silently; no internet permission is requested.
2. Play, pause, and resume a single sound. Verify White/Pink/Brown noise and each bundled sound are audible.
3. Add up to three distinct sounds; verify the fourth is rejected. Change each track and master volume independently.
4. Pause one track in a mix and verify the other continues.
5. Lock the device and use the system media controls. Confirm playback continues.
6. Disconnect wired/Bluetooth headphones; playback should pause instead of unexpectedly switching to the speaker.
7. Cause an audio-focus interruption (for example, a call). Playback should resume only when Android grants focus and the user had not paused it.
8. Set a timer, background the app, verify the fade in the final five minutes, then confirm all sounds stop.
9. Force-stop and relaunch. Preferences should remain saved, but audio must not autoplay.

No Android device/emulator was attached in the development environment, so the real-device checklist remains unverified.

## Release

Version 1.0.0 is cut from the `release/1.0.0` branch and tagged `v1.0.0`. Do not distribute the debug APK.

Signed release builds are produced when a local `android/keystore.properties` exists (it is git-ignored and never committed):

```properties
storeFile=/home/you/.android/nightfall-release.jks
storePassword=...
keyAlias=nightfall
keyPassword=...
```

```bash
ANDROID_HOME=/opt/android-sdk ./gradlew testDebugUnitTest ktlintCheck assembleDebug assembleRelease
/opt/android-sdk/build-tools/35.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

Without that file the release task still runs but emits an unsigned APK. Keep the keystore backed up; losing it means future builds cannot update an installed copy in place. For Google Play, configure Play App Signing / an upload key separately and upload an AAB to the internal testing track first. Keep keystore files and passwords in a secure local/CI secret store; never commit them.
