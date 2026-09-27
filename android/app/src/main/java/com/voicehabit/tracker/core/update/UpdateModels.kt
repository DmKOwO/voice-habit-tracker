package com.voicehabit.tracker.core.update

/**
 * Канал обновлений по воздуху (OTA): GitHub Releases в репозитории DmKOwO/voice-habit-tracker.
 * Никакого ввода директорий и репозиториев от пользователя не требуется — всё зашито в код.
 */
const val UPDATE_GITHUB_OWNER = "DmKOwO"
const val UPDATE_GITHUB_REPO = "voice-habit-tracker"

/**
 * Состояния канала обновлений GitHub Releases — единственного канала распространения приложения.
 */
sealed interface AppUpdateUiState {
    data object Idle : AppUpdateUiState
    data object Checking : AppUpdateUiState
    data class Unavailable(val reason: String) : AppUpdateUiState
    data object UpToDate : AppUpdateUiState
    /**
     * Доступно обновление с GitHub Releases: версия, тег, список изменений, размер APK.
     */
    data class Available(
        val version: String,
        val tag: String,
        val notes: String,
        val sizeBytes: Long?,
        val prerelease: Boolean = false
    ) : AppUpdateUiState

    data class InProgress(
        val bytesDownloaded: Long = 0L,
        val totalBytes: Long = 0L
    ) : AppUpdateUiState

    data object Downloaded : AppUpdateUiState
    data class Failed(val reason: String) : AppUpdateUiState
}

