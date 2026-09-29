package com.voicehabit.tracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.voicehabit.tracker.core.time.DayChangeTracker
import com.voicehabit.tracker.core.update.github.GithubUpdateController
import com.voicehabit.tracker.data.local.SettingsManager
import com.voicehabit.tracker.presentation.home.HomeScreen
import com.voicehabit.tracker.presentation.home.HomeViewModel
import com.voicehabit.tracker.presentation.theme.VoiceHabitTrackerTheme

private const val SPLASH_SAFETY_TIMEOUT_MS = 3000L

class MainActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels()

    private lateinit var githubUpdateController: GithubUpdateController
    private lateinit var dayChangeTracker: DayChangeTracker

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeSharedText(intent)
        checkUpdateInstallRequest(intent)
    }

    private fun checkUpdateInstallRequest(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_INSTALL_UPDATE, false) == true) {
            intent.removeExtra(EXTRA_INSTALL_UPDATE)
            if (githubUpdateController.cleanObsoleteApk()) {
                return
            }
            githubUpdateController.installDownloaded()
        }
    }

    private fun consumeSharedText(intent: Intent?) {
        // CharSequence, а не String: на API 33+ строковые extra помечены устаревшими,
        // а общий доступ к extra не ломается на старых версиях.
        val shared = when (intent?.action) {
            Intent.ACTION_SEND ->
                intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                    ?: intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString()

            Intent.ACTION_PROCESS_TEXT ->
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()

            else -> null
        }?.trim().orEmpty()

        // action == null — это обычный запуск из лаунчера или возврат из фона,
        // а не «поделиться». Читать extra в таком случае нельзя.
        if (shared.isBlank()) return
        // Сбрасываем action, иначе повторный show (например, после поворота экрана)
        // прогнал бы тот же текст через разбор второй раз и создал два конспекта.
        intent?.action = null
        viewModel.ingestExternalText(shared)
    }

    override fun onStart() {
        super.onStart()
        dayChangeTracker.start()
    }

    override fun onStop() {
        dayChangeTracker.stop()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // Страховка midnight-reset: если бродкаст смены суток был пропущен
        // (Doze, убитый процесс), день перепроверяется при каждом возврате.
        viewModel.onForegrounded()
    }

    @Volatile
    private var contentReady = false

    private val recordAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startRecording()
        } else {
            viewModel.onMicDenied()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* без уведомлений очередь всё равно обработается, просто без push */ }

    private var hasAskedForAudioPermission = false

    /**
     * Прогрессивный запрос микрофона: спрашиваем в момент нажатия на запись,
     * а не в onCreate до первого экрана. Пользователь понимает, зачем доступ.
     */
    fun requestAudioForRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.startRecording()
            return
        }
        hasAskedForAudioPermission = true
        recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            // The Duro identity is always dark; keep system icons light.
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !contentReady }
        super.onCreate(savedInstanceState)

        // Страховка: splash не должен висеть дольше SPLASH_SAFETY_TIMEOUT_MS, даже если Compose не отрисовался
        Handler(Looper.getMainLooper()).postDelayed({ contentReady = true }, SPLASH_SAFETY_TIMEOUT_MS)

        githubUpdateController = com.voicehabit.tracker.core.di.AppContainer.get(this).githubUpdater
        dayChangeTracker = DayChangeTracker(this, viewModel::onDayChanged)
        // Тихая автопроверка GitHub-канала
        githubUpdateController.check(auto = true)

        setContent {
            val uiState by viewModel.state.collectAsState()
            VoiceHabitTrackerTheme(
                preset = uiState.themePreset,
                amoled = uiState.amoledTheme,
                fontScale = uiState.fontScale
            ) {
                LaunchedEffect(Unit) { contentReady = true }
                HomeScreen(
                    viewModel = viewModel,
                    githubUpdateController = githubUpdateController
                )
            }
        }

        checkAndRequestNotificationPermission()
        consumeSharedText(intent)
        checkUpdateInstallRequest(intent)
    }

    companion object {
        const val EXTRA_INSTALL_UPDATE = "extra_install_update"
    }

    /**
     * Уведомление воркера о расшифровке записи не показывается на Android 13+,
     * пока разрешение не запрошено в рантайме: в манифесте.permission было,
     * но запроса не было нигде.
     */
    private fun checkAndRequestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
