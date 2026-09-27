package com.voicehabit.tracker.widget

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.voicehabit.tracker.core.audio.AudioRecorderManager
import com.voicehabit.tracker.data.local.AppDatabase
import com.voicehabit.tracker.data.repository.HabitRepositoryImpl
import com.voicehabit.tracker.data.repository.TaskRepositoryImpl
import com.voicehabit.tracker.data.repository.VoiceRepositoryImpl
import com.voicehabit.tracker.domain.usecase.ApplyVoiceActionsUseCase
import com.voicehabit.tracker.domain.usecase.ProcessVoiceUseCase
import com.voicehabit.tracker.presentation.theme.DuroBackground
import com.voicehabit.tracker.presentation.theme.DuroBorder
import com.voicehabit.tracker.presentation.theme.DuroOrange
import com.voicehabit.tracker.presentation.theme.DuroSurface
import com.voicehabit.tracker.presentation.theme.DuroTextMuted
import com.voicehabit.tracker.presentation.theme.DuroTextPrimary
import com.voicehabit.tracker.presentation.theme.DuroTextSecondary
import com.voicehabit.tracker.worker.VoiceUploadWorker
import kotlinx.coroutines.launch
import java.io.File

class QuickRecordActivity : ComponentActivity() {

    private lateinit var container: com.voicehabit.tracker.core.di.AppContainer
    private lateinit var audioRecorder: AudioRecorderManager
    private lateinit var speechRecognizer: com.voicehabit.tracker.core.audio.SpeechRecognizerHelper
    private lateinit var processVoiceUseCase: ProcessVoiceUseCase
    private lateinit var applyVoiceActionsUseCase: ApplyVoiceActionsUseCase

    @Volatile
    private var liveTranscript: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Граф зависимостей берётся из контейнера: раньше здесь собиралась вторая копия,
        // которая со временем расходилась с HomeViewModel.
        container = com.voicehabit.tracker.core.di.AppContainer.get(applicationContext)
        processVoiceUseCase = container.processVoiceUseCase
        applyVoiceActionsUseCase = container.applyVoiceActionsUseCase
        audioRecorder = container.audioRecorder
        speechRecognizer = container.speechRecognizer

        setContent {
            var hasPermission by remember {
                mutableStateOf(
                    ContextCompat.checkSelfPermission(
                        this@QuickRecordActivity,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                )
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                hasPermission = isGranted
                if (isGranted) {
                    startRecording()
                } else {
                    Toast.makeText(this@QuickRecordActivity, "Требуется доступ к микрофону", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }

            LaunchedEffect(Unit) {
                if (hasPermission) {
                    startRecording()
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }

            val isRecording by audioRecorder.isRecording.collectAsState()
            val durationSeconds by audioRecorder.recordDurationSeconds.collectAsState()
            val amplitude by audioRecorder.amplitudeNormalized.collectAsState()

            var isProcessing by remember { mutableStateOf(false) }
            var statusMessage by remember { mutableStateOf("Говорите свободно...") }

            Dialog(onDismissRequest = {
                audioRecorder.cancelRecording()
                finish()
            }) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.dp, DuroBorder, RoundedCornerShape(24.dp)),
                    color = DuroSurface
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(DuroOrange)
                            )
                            Text(
                                text = "Duro Quick Voice",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = DuroTextPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Mic Visualizer / Pulsing circle
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val pulseScale by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.18f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(700, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulseScale"
                        )

                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .scale(if (isRecording) pulseScale else 1f)
                                .clip(CircleShape)
                                .background(DuroOrange.copy(alpha = 0.15f))
                                .border(2.dp, DuroOrange.copy(alpha = 0.6f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Recording",
                                tint = DuroOrange,
                                modifier = Modifier.size(44.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Timer
                        val minutes = durationSeconds / 60
                        val seconds = durationSeconds % 60
                        Text(
                            text = String.format("%02d:%02d", minutes, seconds),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DuroTextPrimary
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (isProcessing) statusMessage else "ИИ автоматически структурирует привычки, задачи и заметки",
                            fontSize = 12.sp,
                            color = if (isProcessing) DuroOrange else DuroTextSecondary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        if (isProcessing) {
                            CircularProgressIndicator(
                                color = DuroOrange,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(36.dp)
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Cancel Button
                                OutlinedButton(
                                    onClick = {
                                        speechRecognizer.stopListening()
                                        audioRecorder.cancelRecording()
                                        finish()
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = DuroTextMuted
                                    ),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Отмена", modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Отмена", fontSize = 13.sp)
                                }

                                // Finish & Analyze Button
                                Button(
                                    onClick = {
                                        speechRecognizer.stopListening()
                                        val recordedFile = audioRecorder.stopRecording()
                                        if (recordedFile == null) {
                                            finish()
                                            return@Button
                                        }

                                        val capturedTranscript = liveTranscript
                                        isProcessing = true
                                        statusMessage = "Анализируем запись..."

                                        lifecycleScope.launch {
                                            val result = processVoiceUseCase(recordedFile, spokenTranscript = capturedTranscript)
                                            result.onSuccess { action ->
                                                applyVoiceActionsUseCase(action)
                                                DuroHabitWidgetProvider.updateAllWidgets(applicationContext)
                                                DuroHabitCardWidgetProvider.updateAllCardWidgets(applicationContext)
                                                Toast.makeText(
                                                    this@QuickRecordActivity,
                                                    "✓ " + action.summary,
                                                    Toast.LENGTH_LONG
                                                ).show()
                                                finish()
                                            }.onFailure { error ->
                                                val dbInstance = AppDatabase.getInstance(applicationContext)
                                                val habits = dbInstance.habitDao().getAllHabitsList()
                                                val tasks = dbInstance.taskDao().getOpenTasksList()
                                                val offlineAction = com.voicehabit.tracker.data.remote.OfflineVoiceParser.parse(
                                                    transcript = capturedTranscript
                                                        ?: "Не удалось распознать речь",
                                                    activeHabits = habits,
                                                    openTasks = tasks
                                                )
                                                applyVoiceActionsUseCase(offlineAction)
                                                offlineAction.logId?.let { logId ->
                                                    container.database.voiceLogDao().updateStatus(
                                                        logId,
                                                        VoiceUploadWorker.VOICE_STATUS_APPLIED,
                                                        offlineAction.summary
                                                    )
                                                }
                                                DuroHabitWidgetProvider.updateAllWidgets(applicationContext)
                                                DuroHabitCardWidgetProvider.updateAllCardWidgets(applicationContext)
                                                Toast.makeText(
                                                    this@QuickRecordActivity,
                                                    "✓ " + offlineAction.summary,
                                                    Toast.LENGTH_LONG
                                                ).show()
                                                finish()
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1.5f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = DuroOrange,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "Готово", modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Сохранить", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startRecording() {
        liveTranscript = null
        val audioFile = File(cacheDir, "quick_voice_${System.currentTimeMillis()}.m4a")
        audioRecorder.startRecording(audioFile)
        if (speechRecognizer.isAvailable()) {
            speechRecognizer.startListening(
                onPartialText = { partial ->
                    liveTranscript = partial
                },
                onFinalText = { text ->
                    liveTranscript = text
                },
                onError = { /* fallback to audio/offline parser */ }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.stopListening()
        audioRecorder.cancelRecording()
    }
}
