package com.voicehabit.tracker.presentation.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicehabit.tracker.presentation.theme.*

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.voicehabit.tracker.presentation.home.AppScreen
import com.voicehabit.tracker.presentation.home.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileMenuSheet(
    viewModel: HomeViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    ProfileMenuSheet(
        hasApiKeys = state.hasApiKeysConfigured,
        onOpenSettings = {
            onDismiss()
            viewModel.openSettings()
        },
        onOpenArchive = {
            onDismiss()
            viewModel.openScreen(AppScreen.ARCHIVE)
        },
        onOpenQueue = {
            onDismiss()
            viewModel.openVoiceQueue()
        },
        onOpenOperationsLog = {
            onDismiss()
            viewModel.openOperationsLog()
        },
        onOpenAbout = {
            onDismiss()
            viewModel.openScreen(AppScreen.ABOUT)
        },
        onDismiss = onDismiss,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileMenuSheet(
    hasApiKeys: Boolean,
    onOpenSettings: () -> Unit,
    onOpenArchive: () -> Unit = {},
    onOpenQueue: () -> Unit = {},
    onOpenOperationsLog: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DuroSurface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = DuroBorder)
        }
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: User / App Profile
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    color = DuroOrange.copy(alpha = 0.15f),
                    shape = CircleShape,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        DuroAsterisk(size = 28.dp, color = DuroOrange)
                    }
                }

                Column {
                    Text(
                        text = "Профиль и система",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = DuroTextPrimary
                    )
                    Text(
                        text = "Voice Habit & Journal v2.0 • Duro",
                        fontSize = 12.sp,
                        color = DuroTextSecondary
                    )
                }
            }

            HorizontalDivider(color = DuroBorder)

            // Settings Items List
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileMenuItem(
                    icon = Icons.Default.Settings,
                    title = "Настройки ИИ и API-ключей",
                    subtitle = if (hasApiKeys) "Ключи Groq & Gemini настроены" else "⚡ Требуется настройка ключей",
                    badgeColor = if (hasApiKeys) DuroLime else DuroOrange,
                    onClick = {
                        onDismiss()
                        onOpenSettings()
                    }
                )

                ProfileMenuItem(
                    icon = Icons.Default.Sync,
                    title = "Синхронизация с Obsidian Vault",
                    subtitle = "План интеграции и экспорт заметок в хранилище",
                    badgeColor = DuroJournalLavender,
                    onClick = {
                        Toast.makeText(context, "Obsidian Vault: экспорт доступен через кнопку «Obsidian MD» в деталях каждой мысли", Toast.LENGTH_LONG).show()
                    }
                )

                ProfileMenuItem(
                    icon = Icons.Default.Archive,
                    title = "Архив и корзина",
                    subtitle = "Восстановление привычек и задач",
                    badgeColor = DuroCyan,
                    onClick = {
                        onDismiss()
                        onOpenArchive()
                    }
                )

                ProfileMenuItem(
                    icon = Icons.Default.CloudQueue,
                    title = "Очередь голосовой обработки",
                    subtitle = "Офлайн-буфер и статус распознавания",
                    badgeColor = DuroTextSecondary,
                    onClick = {
                        onDismiss()
                        onOpenQueue()
                    }
                )

                ProfileMenuItem(
                    icon = Icons.Default.Terminal,
                    title = "Журнал операций",
                    subtitle = "Логирование и активные фоновые задачи",
                    badgeColor = DuroAmber,
                    onClick = {
                        onDismiss()
                        onOpenOperationsLog()
                    }
                )

                ProfileMenuItem(
                    icon = Icons.Default.Info,
                    title = "О приложении Duro Edition",
                    subtitle = "Архитектура спокойной продуктивности и Дневника мыслей",
                    badgeColor = DuroTextMuted,
                    onClick = {
                        onDismiss()
                        onOpenAbout()
                    }
                )
            }
        }
    }
}

@Composable
fun ProfileMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badgeColor: Color,
    onClick: () -> Unit
) {
    Surface(
        color = DuroSurfaceElevated,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DuroBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    color = badgeColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(imageVector = icon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(18.dp))
                    }
                }

                Column {
                    Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DuroTextPrimary)
                    Text(text = subtitle, fontSize = 11.sp, color = DuroTextSecondary)
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = DuroTextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
