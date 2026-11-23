# Nightfall

Nightfall is a private, offline-first sleep-sound app for iPhone (iOS 17+). It has no accounts, ads, analytics, purchases, tracking, or network client. Preferences and favorites are stored locally in `Documents/Nightfall/preferences.json`.

## Open and run

1. Open `Nightfall.xcodeproj` on a Mac with Xcode 15 or later.
2. Select the **Nightfall** scheme and an iPhone simulator or connected iPhone.
3. Set a unique Bundle Identifier in the target's **Signing & Capabilities** settings and select your development team for a device build.
4. Build and run. The app never starts playback automatically.
5. Run the **Nightfall** scheme's unit tests, or use `xcodebuild -project Nightfall.xcodeproj -scheme Nightfall -destination 'platform=iOS Simulator,name=iPhone 16' test`.

This workspace was prepared on Linux, which does not include Xcode or `xcodebuild`; the project must be compiled and exercised on macOS before distribution.

## Audio and licenses

`Nightfall/Resources/Sounds` contains original **synthetic placeholders** for Rain, Ocean, Fan, Fireplace, Train, Wind, and Forest. They are not field recordings. The files are 11.89-second, mono, 22.05 kHz, 16-bit PCM WAV loops, generated with `Scripts/generate_placeholder_audio.py` and included so the app is usable offline immediately.

White, pink, and brown noise are synthesized locally in `AudioPlayerService.swift`. They do not require bundled audio.

To replace a placeholder with a licensed recording:

1. Obtain a loop you have explicit rights to distribute in an iOS app. Keep the license, proof of purchase, and any required attribution with your source materials.
2. Export/convert it as mono or stereo PCM WAV or AAC, preferably 44.1 kHz or 48 kHz. A 30–120 second ambience is a practical starting point. Make the start/end crossfade seamless before importing it.
3. Replace the matching filename (`Rain.wav`, `Ocean.wav`, `Fan.wav`, `Fireplace.wav`, `Train.wav`, `Wind.wav`, or `Forest.wav`) in the Xcode project's **Sounds** group. Preserve the resource's target membership. Or change `SoundKind.bundledFilename`.
4. Listen to repeated loops on headphones and the iPhone speaker at low volume; check the boundary at several volume levels. The current generated files are demo placeholders and should be replaced if production-quality natural recordings are required.

The Xcode target bundles every sound. Playback does not make network requests. The application does not declare an internet entitlement or include networking/analytics SDKs.

## Architecture

- `Models.swift`: sound catalog, mix rules, timer math, and volume calculation.
- `AudioPlayerService.swift`: AVAudioSession, looping playback, local noise generation, Lock Screen/Control Center commands, interruptions, and route changes.
- `SleepTimerService.swift`: persisted timer deadline, countdown, and five-minute fade window.
- `PersistenceService.swift`: atomic JSON preference storage in Documents.
- `NightfallViewModel.swift`: screen state, favorites, mix editing, recent mixes, and service coordination.
- `NightfallView.swift`: accessible SwiftUI screen, timer picker, library, favorites, and presets.
- `NightfallTests`: unit tests for mix creation, volume math, timer countdown/fade, and persistence round-trip.

Future extension points are the `SoundKind` catalog, file-backed `AudioPlayerService.audioData(for:)`, `SoundMix`, and the persisted preferences model. Custom recordings, alarms, and Watch remote control are intentionally not implemented.

## Background playback and controls

Test on a real iPhone as well as the simulator:

1. Start a sound and lock the phone; audio should continue and Nightfall should appear in Lock Screen playback controls.
2. Try play/pause from Control Center and the Lock Screen.
3. Connect and disconnect wired headphones, AirPods, and a Bluetooth speaker. Disconnecting the active output pauses playback so sound does not unexpectedly switch to the phone speaker.
4. Place a test call or start another audio app. Nightfall pauses for an interruption and resumes only when iOS indicates it is appropriate and the user had not paused it.
5. Start a short timer and verify the five-minute fade with a five-minute-or-longer timer, then confirm audio stops at expiry. For faster UI checks, temporarily use a shorter timer in a development-only change.
6. Background modes in `Info.plist` include `audio`, `airplay`, and `picture-in-picture`; `.playback` plus AirPlay routing are configured by `AudioPlayerService`. Picture-in-picture is enabled as requested, though Nightfall has no video and does not present a PiP window.

## TestFlight

An Apple Developer Program membership and a Mac with Xcode are required.

1. In App Store Connect, open **Apps → + → New App**, choose **iOS**, enter Nightfall, select the Bundle ID configured in Xcode, and set an SKU. Complete required app information and privacy details before review/distribution.
2. In Xcode, select **Any iOS Device (arm64)**, set the version/build number, choose **Product → Archive**, then use Organizer's **Distribute App → App Store Connect → Upload**.
3. In App Store Connect, open the app's **TestFlight** tab and wait for processing. Complete the export-compliance and beta-review questions if prompted.
4. To invite one tester by email, add their address under **TestFlight → Internal Testing** (they need an App Store Connect user role), or create an external tester/group and invite their email. The tester accepts Apple's email invitation and installs TestFlight from the App Store.
5. TestFlight builds expire **90 days after upload**. Upload and distribute a refreshed build periodically if the tester needs uninterrupted access.

No Apple account, sign-in, purchase, or network service is needed to use Nightfall itself; App Store Connect is only for distributing test builds.
