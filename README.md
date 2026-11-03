# VISION# Vision – Android Voice Assistant

Vision is a voice assistant with a holographic interface, rewritten for Android in Kotlin and Jetpack Compose.

## Features

- Tap **Speak** (or type a command) to talk to Vision
- Opens YouTube and Google, plays songs on YouTube, searches Google
- Tells the time
- Answers other questions with an AI API
- Animated orb: blue while idle, red while speaking

## Setup

1. Open the project folder in **Android Studio (Ladybug or newer)**.
2. Let Gradle sync. Android Studio creates the Gradle wrapper automatically.
3. Open `app/src/main/java/com/vision/assistant/CommandProcessor.kt` and paste your API key into `API_KEY`.
4. Click **Run** on a device or emulator, or use **Build → Build APK(s)** to create an APK.

The APK is written to `app/build/outputs/apk/debug/`.

## Notes

- Speech recognition and text-to-speech use Google's services and need an internet connection.
- The desktop version's clap detection, wake word, and macOS commands are not included in this Android version.
- Do not commit your API key. Keep it in a local file or use a backend in production.

## License

MIT
