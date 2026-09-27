# Voice Habit & Task Tracker — Android Client

Modern native Android application built with **Jetpack Compose**, **Clean Architecture + MVI**, and **Room Database (Offline-First)**.

## Key Modules & Features
- **Clean Architecture & MVI**: Strict separation between Presentation (`HomeScreen`, `VoiceRecordFab`, `ReviewBottomSheet`), Domain (UseCases, Entities), and Data (Room DB, Retrofit API client).
- **Voice-First Recording**: `AudioRecorderManager` utilizing Android hardware `VOICE_RECOGNITION` (noise cancellation, AGC) recording mono 16 kHz AAC at 32 kbps (~1.2 MB for 5 minutes).
- **Live Waveform & Lock Gesture**: Canvas-drawn pulsating sound wave and Telegram-style record flow.
- **Interactive ReviewSheet**: Auto-apply countdown timer (8s), checkboxes to select/deselect parsed habits, inline task details.
- **Offline-First Resilience**: `VoiceUploadWorker` via WorkManager automatically buffers recordings when offline and uploads upon network reconnect.

## How to Build & Run
1. Open this folder in **Android Studio Ladybug (or newer)**.
2. Ensure JDK 17 is selected in **Settings -> Build, Execution, Deployment -> Build Tools -> Gradle -> Gradle JDK**.
3. Sync Project with Gradle Files.
4. If testing on Android Emulator, the app connects to your local FastAPI backend at `http://10.0.2.2:8000/`. If using a physical phone, replace `10.0.2.2` with your machine's local Wi-Fi IP in `VoiceApiService.kt`.
