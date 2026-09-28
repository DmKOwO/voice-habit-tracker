package com.voicehabit.tracker.presentation.profile

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicehabit.tracker.presentation.home.HomeViewModel
import com.voicehabit.tracker.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObsidianSyncSheet(
    viewModel: HomeViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = rememberDuroHaptics()
    val state by viewModel.state.collectAsState()

    val openVaultFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            haptics.confirm()
            viewModel.setObsidianVaultUri(it)
        }
    }

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
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    color = AppTheme.colors.accent.copy(alpha = 0.18f),
                    shape = CircleShape,
                    modifier = Modifier.size(50.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(id = com.voicehabit.tracker.R.drawable.ic_dairy_logo),
                            contentDescription = null,
                            tint = AppTheme.colors.accent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column {
                    Text(
                        text = "Obsidian Vault",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AppTheme.colors.textPrimary
                    )
                    Text(
                        text = "Local-First синхронизация мыслей и задач",
                        fontSize = 12.sp,
                        color = AppTheme.colors.textSecondary
                    )
                }
            }

            HorizontalDivider(color = AppTheme.colors.border)

            // Vault Status Card
            Surface(
                color = DuroSurfaceElevated,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    1.dp,
                    if (state.isObsidianConfigured) DuroLime.copy(alpha = 0.35f) else DuroBorder
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = if (state.isObsidianConfigured) DuroLime else DuroOrange,
                                shape = CircleShape,
                                modifier = Modifier.size(8.dp)
                            ) {}
                            Text(
                                text = if (state.isObsidianConfigured) "Хранилище подключено" else "Хранилище не выбрано",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (state.isObsidianConfigured) DuroLime else DuroOrange
                            )
                        }

                        if (state.isObsidianConfigured) {
                            TextButton(
                                onClick = {
                                    haptics.reject()
                                    viewModel.clearObsidianVault()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Отключить",
                                    fontSize = 11.sp,
                                    color = DuroRed
                                )
                            }
                        }
                    }

                    if (state.isObsidianConfigured) {
                        Text(
                            text = "Папка: ${state.obsidianVaultName}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = DuroTextPrimary
                        )
                        Text(
                            text = "Заметки сохраняются в подпапку «Voice Journal/», а аудиозаписи в «Voice Journal/attachments/».",
                            fontSize = 12.sp,
                            color = DuroTextSecondary,
                            lineHeight = 18.sp
                        )

                        Button(
                            onClick = {
                                haptics.select()
                                openVaultFolderLauncher.launch(null)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DuroSurface,
                                contentColor = DuroTextPrimary
                            ),
                            border = BorderStroke(1.dp, DuroBorder),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Сменить папку Vault", fontSize = 13.sp)
                        }
                    } else {
                        Text(
                            text = "Выберите папку хранилища на вашем телефоне. Приложение запишет заметки с Frontmatter и аудиозаписями.",
                            fontSize = 13.sp,
                            color = DuroTextSecondary,
                            lineHeight = 19.sp
                        )

                        Button(
                            onClick = {
                                haptics.confirm()
                                openVaultFolderLauncher.launch(null)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DuroJournalLavender,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Выбрать папку Obsidian Vault",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Auto-Export Toggle
            Surface(
                color = DuroSurfaceElevated,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, DuroBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Авто-экспорт новых записей",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = DuroTextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Автоматически создавать заметку и сохранять аудио в Vault при каждой новой мысли",
                            fontSize = 11.sp,
                            color = DuroTextSecondary,
                            lineHeight = 16.sp
                        )
                    }

                    Switch(
                        checked = state.obsidianAutoExport,
                        onCheckedChange = {
                            haptics.select()
                            viewModel.setObsidianAutoExport(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = DuroJournalLavender,
                            uncheckedThumbColor = DuroTextMuted,
                            uncheckedTrackColor = DuroSurface
                        )
                    )
                }
            }

            // Export All Button
            Surface(
                color = DuroSurfaceElevated,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, DuroBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Экспорт всех записей",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = DuroTextPrimary
                    )
                    Text(
                        text = "Экспортирует все ${state.digests.size} мыслей из Дневника и файл «Привычки и задачи.md» в хранилище.",
                        fontSize = 12.sp,
                        color = DuroTextSecondary,
                        lineHeight = 17.sp
                    )

                    Button(
                        onClick = {
                            if (!state.isObsidianConfigured) {
                                haptics.reject()
                                Toast.makeText(context, "Сначала выберите папку Vault выше", Toast.LENGTH_SHORT).show()
                            } else {
                                haptics.confirm()
                                viewModel.exportAllToObsidian()
                            }
                        },
                        enabled = state.isObsidianConfigured && state.digests.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DuroJournalLavender,
                            contentColor = Color.White,
                            disabledContainerColor = DuroJournalLavender.copy(alpha = 0.3f),
                            disabledContentColor = DuroTextMuted
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Экспортировать все записи в Obsidian",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Open in Obsidian Button (if configured)
            if (state.isObsidianConfigured) {
                OutlinedButton(
                    onClick = {
                        haptics.select()
                        val opened = viewModel.obsidianVaultManager.openNoteInObsidian(state.obsidianVaultName, "Voice Journal")
                        if (!opened) {
                            Toast.makeText(context, "Приложение Obsidian не установлено на устройстве", Toast.LENGTH_SHORT).show()
                        }
                    },
                    border = BorderStroke(1.dp, DuroJournalLavender.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Launch,
                        contentDescription = null,
                        tint = DuroJournalLavender,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Открыть папку в Obsidian",
                        fontSize = 13.sp,
                        color = DuroJournalLavender,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
