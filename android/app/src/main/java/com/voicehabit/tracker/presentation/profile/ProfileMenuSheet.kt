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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    var showObsidianSyncSheet by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    if (showObsidianSyncSheet) {
        ObsidianSyncSheet(
            viewModel = viewModel,
            onDismiss = { showObsidianSyncSheet = false }
        )
    }

    ProfileMenuSheet(
        hasApiKeys = state.hasApiKeysConfigured,
        isObsidianConfigured = state.isObsidianConfigured,
        obsidianVaultName = state.obsidianVaultName,
        onOpenObsidianSync = {
            showObsidianSyncSheet = true
        },
        onOpenSettings = {
            onDismiss()
            viewModel.openSettings()
        },
        onOpenUserPersona = {
            onDismiss()
            viewModel.openScreen(AppScreen.USER_PERSONA)
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
        onOpenMore = {
            onDismiss()
            viewModel.openScreen(AppScreen.HUB)
        },
        onDismiss = onDismiss,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileMenuSheet(
    hasApiKeys: Boolean,
    isObsidianConfigured: Boolean = false,
    obsidianVaultName: String = "Не подключено",
    onOpenObsidianSync: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onOpenUserPersona: () -> Unit = {},
    onOpenArchive: () -> Unit = {},
    onOpenQueue: () -> Unit = {},
    onOpenOperationsLog: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onOpenMore: () -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AppTheme.colors.surface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = AppTheme.colors.border)
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
                    color = AppTheme.colors.accent.copy(alpha = 0.15f),
                    shape = CircleShape,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        DairyLogo(size = 28.dp, color = AppTheme.colors.accent)
                    }
                }

                Column {
                    Text(
                        text = "Профиль и система",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AppTheme.colors.textPrimary
                    )
                    Text(
                        text = "dairy • Minimalist Edition",
                        fontSize = 12.sp,
                        color = AppTheme.colors.textSecondary
                    )
                }
            }

            HorizontalDivider(color = AppTheme.colors.border)

            // Settings Items List
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileMenuItem(
                    icon = Icons.Default.Psychology,
                    title = "Контекст и память ИИ",
                    subtitle = "Интересы, сферы жизни, идеи и память",
                    badgeColor = AppTheme.colors.accent,
                    onClick = {
                        onDismiss()
                        onOpenUserPersona()
                    }
                )

                ProfileMenuItem(
                    icon = Icons.Default.Settings,
                    title = "Настройки ИИ и API-ключей",
                    subtitle = if (hasApiKeys) "Ключи Groq & Gemini настроены" else "Требуется настройка ключей",
                    badgeColor = if (hasApiKeys) AppTheme.colors.accent else AppTheme.colors.error,
                    onClick = {
                        onDismiss()
                        onOpenSettings()
                    }
                )

                ProfileMenuItem(
                    icon = Icons.Default.Sync,
                    title = "Синхронизация с Obsidian Vault",
                    subtitle = if (isObsidianConfigured) "Подключено: $obsidianVaultName • Экспорт" else "Выбор папки Vault и экспорт заметок",
                    badgeColor = AppTheme.colors.accent,
                    onClick = {
                        onOpenObsidianSync()
                    }
                )

                ProfileMenuItem(
                    icon = Icons.Default.Archive,
                    title = "Архив и корзина",
                    subtitle = "Восстановление привычек и задач",
                    badgeColor = AppTheme.colors.accentSecondary,
                    onClick = {
                        onDismiss()
                        onOpenArchive()
                    }
                )

                ProfileMenuItem(
                    icon = Icons.Default.CloudQueue,
                    title = "Очередь голосовой обработки",
                    subtitle = "Офлайн-буфер и статус распознавания",
                    badgeColor = AppTheme.colors.textSecondary,
                    onClick = {
                        onDismiss()
                        onOpenQueue()
                    }
                )

                ProfileMenuItem(
                    icon = Icons.Default.Terminal,
                    title = "Журнал операций",
                    subtitle = "Логирование и активные фоновые задачи",
                    badgeColor = AppTheme.colors.accent,
                    onClick = {
                        onDismiss()
                        onOpenOperationsLog()
                    }
                )

                ProfileMenuItem(
                    icon = Icons.Default.Info,
                    title = "О приложении dairy",
                    subtitle = "Архитектура спокойной продуктивности и Дневника мыслей",
                    badgeColor = AppTheme.colors.textMuted,
                    onClick = {
                        onDismiss()
                        onOpenAbout()
                    }
                )

                ProfileMenuItem(
                    icon = Icons.Default.Apps,
                    title = "Ещё",
                    subtitle = "Поиск по всему, отчёты, выводы, импорт",
                    badgeColor = AppTheme.colors.accent,
                    onClick = {
                        onDismiss()
                        onOpenMore()
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
        color = AppTheme.colors.surfaceElevated,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.border),
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
                    Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.textPrimary)
                    Text(text = subtitle, fontSize = 11.sp, color = AppTheme.colors.textSecondary)
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = AppTheme.colors.textMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
