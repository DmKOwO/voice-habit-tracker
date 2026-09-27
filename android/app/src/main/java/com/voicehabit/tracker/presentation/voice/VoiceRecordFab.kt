package com.voicehabit.tracker.presentation.voice

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicehabit.tracker.presentation.theme.*
import kotlinx.coroutines.flow.StateFlow

@Composable
fun VoiceRecordFab(
    isRecordingFlow: StateFlow<Boolean>,
    durationSecondsFlow: StateFlow<Int>,
    amplitudeFlow: StateFlow<Float>,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
    onCancelRecord: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Recording streams are collected here, not in HomeState: amplitude ticks
    // redraw only this FAB instead of recomposing the whole home screen.
    val isRecording by isRecordingFlow.collectAsState()
    val durationSeconds by durationSecondsFlow.collectAsState()
    val amplitude by amplitudeFlow.collectAsState()
    val haptics = rememberDuroHaptics()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        AnimatedVisibility(visible = isRecording) {
            RecordingStatusPanel(
                durationSeconds = durationSeconds,
                amplitude = amplitude,
                onCancel = {
                    haptics.confirm()
                    onCancelRecord()
                },
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        // Floating Action Button
        PulsingRecordControl(
            isRecording = isRecording,
            onClick = {
                if (isRecording) onStopRecord() else onStartRecord()
            }
        )
    }
}

@Composable
private fun RecordingStatusPanel(
    durationSeconds: Int,
    amplitude: Float,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Spring smoothing turns raw audio ticks into organic, stable wave motion.
    val animatedAmplitude by animateFloatAsState(
        targetValue = amplitude.coerceIn(0f, 1f),
        animationSpec = DuroContentSpring,
        label = "recordingAmplitude"
    )
    val cancelInteractions = remember { MutableInteractionSource() }

    Surface(
        color = DuroSurface,
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DuroBorder),
        shadowElevation = 12.dp,
        // Было width(280.dp): на тонком телефоне панель вылезала за край.
        // Теперь ширина — доля доступного места с верхним пределом.
        modifier = modifier
            .fillMaxWidth(0.86f)
            .widthIn(max = 300.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onCancel,
                interactionSource = cancelInteractions,
                modifier = Modifier
                    .duroPressScale(cancelInteractions)
                    .size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Отмена",
                    tint = DuroRed,
                    modifier = Modifier.size(18.dp)
                )
            }

            VoiceWaveform(
                amplitude = animatedAmplitude,
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .padding(horizontal = 8.dp)
            )

            Text(
                text = "%02d:%02d".format(durationSeconds / 60, durationSeconds % 60),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = DuroTextPrimary
            )
        }
    }
}

@Composable
private fun VoiceWaveform(
    amplitude: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val barWidth = 3.dp.toPx()
        val spacing = 3.dp.toPx()
        val totalBars = (size.width / (barWidth + spacing)).toInt().coerceAtLeast(1)
        val midY = size.height / 2

        for (i in 0 until totalBars) {
            val x = i * (barWidth + spacing)
            val factor = ((i + 1) * 31 % 7) / 7f
            val height = (size.height * 0.15f + size.height * 0.85f * amplitude * factor)
                .coerceIn(4f, size.height)

            drawLine(
                color = DuroOrange,
                start = Offset(x, midY - height / 2),
                end = Offset(x, midY + height / 2),
                strokeWidth = barWidth
            )
        }
    }
}

@Composable
private fun PulsingRecordControl(
    isRecording: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // The infinite breathing animation is created only while recording, so the
    // idle button costs no animation frames.
    val pulseScale = if (isRecording) {
        val pulse = rememberInfiniteTransition(label = "recordPulse")
        pulse.animateFloat(
            initialValue = 1f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "recordPulseScale"
        ).value
    } else {
        1f
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        // A static blurred halo reads as glow/depth without blurring the live
        // recording waveform every frame.
        Box(
            modifier = Modifier
                .size(88.dp)
                .blur(30.dp)
                .background(
                    (if (isRecording) DuroRed else DuroOrange).copy(alpha = 0.16f),
                    CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(62.dp)
            .graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
                transformOrigin = TransformOrigin.Center
            }
            .clip(CircleShape)
            .background(
                if (isRecording) {
                    Brush.linearGradient(listOf(DuroRed, Color(0xFFDC2626)))
                } else {
                    Brush.linearGradient(listOf(DuroOrange, Color(0xFFFF854D)))
                }
            )
            .border(2.dp, Color.White.copy(alpha = 0.25f), CircleShape)
            .duroPressable(
                haptic = HapticFeedbackType.LongPress,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = isRecording,
            transitionSpec = {
                (fadeIn(DuroIconSpring) + scaleIn(DuroIconSpring, initialScale = 0.72f))
                    .togetherWith(fadeOut(DuroContentSpring) + scaleOut(DuroContentSpring, targetScale = 0.72f))
            },
            label = "recordIconMorph"
        ) { recording ->
            Icon(
                imageVector = if (recording) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = if (recording) "Остановить запись" else "Начать голосовой ввод",
                tint = Color.White,
                modifier = Modifier.size(30.dp)
            )
        }
        }
    }
}
