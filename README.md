# Deaf Less

## Elevator Pitch
**"Your ears, amplified by AI — Deaf Less transforms critical sounds into instant vibration alerts, empowering people with hearing challenges to stay connected to the world around them."**

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

## Download AI Models

**IMPORTANT**: The AI models are too large to include in the GitHub repository. You must download them manually before running the app.

Since the app works **100% offline** with on-device processing (no internet connection required after setup), the models need to be available locally.

### Download Instructions

1. Visit the Hugging Face model repository: [https://huggingface.co/stiv14/audio-caption-categorizer-model](https://huggingface.co/stiv14/audio-caption-categorizer-model)

2. Download the following three model files:
   - `effb2_decoder_5sec.pte` (~15 MB)
   - `effb2_encoder_preprocess-2.onnx` (~31 MB)
   - `sentence_transformers_minilm.pte` (~90 MB)

3. Place all three files in the `assets/` folder of this project:
   ```
   deaf-less/
   ├── assets/
   │   ├── effb2_decoder_5sec.pte
   │   ├── effb2_encoder_preprocess-2.onnx
   │   ├── sentence_transformers_minilm.pte
   │   └── ... (other existing files)
   ```

> **Note**: These models enable audio captioning and semantic categorization entirely on your device. No audio data is sent to external servers.

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
