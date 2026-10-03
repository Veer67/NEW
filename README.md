# Heading AI

Heading AI is a native Kotlin Android assistant built from the existing Gemini Live voice assistant foundation. The app keeps real-time microphone capture, streaming Gemini audio, barge-in interruption, local conversation history, and lifecycle-aware session cleanup while migrating the product identity to `com.heading.ai`.

## Current architecture

- `ui/` — native Activities, ViewModels, chat history, and orb visualizer
- `data/` — models, encrypted preferences, and local chat repository
- `network/` — Gemini Live WebSocket transport with reconnection and session resumption
- `audio/` — 16 kHz microphone capture and 24 kHz PCM playback
- `util/` — assistant prompt generation

## Local setup

1. Install Android Studio with Android SDK 34 and JDK 17.
2. Open this directory in Android Studio, or use the Gradle wrapper.
3. Enter a Gemini API key in the in-app Settings screen. The value is stored using Android Keystore-backed encrypted preferences and is never included in source control.
4. Start a voice session from the home screen.

Build the debug APK locally:

```bash
chmod +x ./gradlew
./gradlew :app:assembleDebug --no-daemon
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## APIs and secrets

- **Gemini Live API:** required for real-time AI voice conversation. Configure the key in the app; do not hardcode it.
- **ElevenLabs:** not yet wired into the current native UI. It should be added through a `VoiceProvider` abstraction in a later pass; no key is currently required or committed.
- **OpenRouter:** not yet wired into the current provider selection UI. No key is required or committed.

Never commit `local.properties`, `.env` files, API keys, keystores, or signing credentials. The repository `.gitignore` includes common secret and signing patterns.

## Permissions

- `RECORD_AUDIO` — requested only when the user starts a voice session.
- `INTERNET` and `ACCESS_NETWORK_STATE` — Gemini Live network connection and connection-state handling.
- `MODIFY_AUDIO_SETTINGS` — assistant audio routing and playback behavior.

Camera, notifications, file selection, alarms, contacts, and other action permissions are intentionally not declared until their corresponding real features are implemented.

## GitHub Actions

`.github/workflows/build-apk.yml` runs on pushes to `main` and on manual `workflow_dispatch` runs. It uses JDK 17, the repository Gradle wrapper, builds `:app:assembleDebug`, and uploads `app/build/outputs/apk/debug/*.apk` as the artifact **Heading-AI-debug-apk**.

The current debug build does not need GitHub Secrets because the Gemini key is entered at runtime. Release signing is not configured; no private signing material belongs in the repository.

## Known limitations

This pass focuses on preserving the working native assistant while completing identity, package, secure-storage, branding foundation, and build automation. The following are not falsely represented as complete yet:

- ElevenLabs and OpenRouter provider implementations
- text chat composer and multimodal file/image flows
- camera and Android action/tool router
- Room-backed multi-chat management and controlled memory
- reliable always-listening wake word (`Hey Heading`) without a dedicated licensed SDK
- automated device testing of microphone, Gemini, and audio hardware

The launcher icon currently uses a temporary Heading AI vector placeholder and is ready to be replaced with the logo you provide.
