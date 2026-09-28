# CrazeEngine Android

CrazeEngine is now an Android client built around CrazeAi. The app provides a clean chat interface and connects directly to the Gemini API using a key supplied by the user at runtime.

## Features
- CrazeAi chat UI
- Conversation view and local in-memory session history
- Copy and regenerate responses
- Gemini model selection
- Custom system prompt
- Dark-first Material 3 UI
- GitHub Actions APK build

## Security
Never commit a Gemini API key. Enter it locally in the app Settings screen. For production distribution, use a backend/proxy or a properly restricted key rather than embedding a secret in the APK.

## Build
Run `gradle :app:assembleDebug` with JDK 17 and Android SDK 35. GitHub Actions builds and uploads the debug APK automatically.
