# Deaf Less

Hearing safety assistant built with Flutter. The app monitors the environment in the background and sends alerts when it detects sounds you have configured as important.

## Features
- 24/7 monitoring: background listening without interrupting other activities.
- Smart alerts: instant notifications when selected sounds are detected.
- Fully configurable: enable only the sounds that matter to you.
- On-device detection: processing happens locally; audio is not sent to servers.

## Requirements
- Flutter `>=3.x` and Dart installed.
- Android device with ARM architecture (iOS currently not supported).
- Permissions: microphone (required) and notifications (Android).

## Install and run
```bash
# Install dependencies
flutter pub get

# Run on an Android device/emulator
flutter run -d android
```

## Project structure
- `lib/`: main source code (`go_router` for navigation, `flutter_bloc` for state, `hive` for preferences).
- `assets/`: local files and models for on-device processing.
- `android/` and `ios/`: platform-specific native configuration.

## Configuration and permissions
- The app will request microphone permission when starting listening.
- On Android, notification permission is also requested for alerts.
- On unsupported devices (non-ARM or non-Android), a notice is shown and listening is disabled.

## Release build (Android)
```bash
# Generate release APK
flutter build apk --release

# Generate App Bundle for Play Store
flutter build appbundle --release
```

## Privacy
- Audio is not uploaded or transmitted off the device.
- Local preferences (`Hive`) store your sound configuration.

## Contributing
- Issues and PRs are welcome to improve compatibility and features.
- Before contributing, run tests:
```bash
flutter test
```

## License
This project is distributed under a **Non-Commercial Open Use** license. See the `LICENSE` file for full terms and conditions.
