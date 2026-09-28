package com.voicehabit.tracker.presentation.home.components

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.voicehabit.tracker.core.update.AppUpdateUiState
import com.voicehabit.tracker.data.local.SettingsManager
import com.voicehabit.tracker.presentation.theme.*
import com.voicehabit.tracker.widget.WidgetPinHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@Composable
fun SettingsDialog(
    settingsManager: SettingsManager,
    githubUpdateController: com.voicehabit.tracker.core.update.github.GithubUpdateController,
    onDismiss: () -> Unit,
    viewModel: com.voicehabit.tracker.presentation.home.HomeViewModel? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var groqKey by remember { mutableStateOf(settingsManager.groqApiKey) }
    var geminiKey by remember { mutableStateOf(settingsManager.geminiApiKey) }
    var customUrl by remember { mutableStateOf(settingsManager.customBackendUrl) }
    var useDirectCloud by remember { mutableStateOf(settingsManager.useDirectCloud) }
    val githubUpdateState by githubUpdateController.state.collectAsState()

    var testStatus by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = AppTheme.colors.surface,
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.border),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = AppTheme.colors.accent,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Настройки ИИ и виджетов",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )
                }

                Text(
                    text = "Для работы распознавания речи прямо с телефона (на 4G / Wi-Fi без ПК) вставьте ваши бесплатные ключи API:",
                    fontSize = 12.sp,
                    color = AppTheme.colors.textSecondary,
                    lineHeight = 16.sp
                )

                // Встроенные ключи из сборки: ничего вставлять не нужно,
                // свои значения в полях ниже их перекроют.
                if (settingsManager.isUsingBundledKeys) {
                    Text(
                        text = "Встроенные ключи активны — ИИ работает из коробки. Свои ключи вводить не нужно.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppTheme.colors.accent,
                        lineHeight = 16.sp
                    )
                }

                // 1. Groq API Key
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Groq API Key (Whisper Turbo STT):", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.colors.textPrimary, modifier = Modifier.weight(1f))
                        TextButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = cm.primaryClip?.getItemAt(0)?.text?.toString()
                                if (!clip.isNullOrBlank()) {
                                    groqKey = clip.trim()
                                    Toast.makeText(context, "Вставлено из буфера", Toast.LENGTH_SHORT).show()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            modifier = Modifier.wrapContentWidth()
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp), tint = AppTheme.colors.accent)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Вставить", fontSize = 11.sp, color = AppTheme.colors.accent, maxLines = 1, softWrap = false)
                        }
                    }
                    OutlinedTextField(
                        value = groqKey,
                        onValueChange = { groqKey = it },
                        placeholder = { Text("gsk_...", fontSize = 12.sp, color = AppTheme.colors.textMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppTheme.colors.accent,
                            unfocusedBorderColor = AppTheme.colors.border,
                            focusedTextColor = AppTheme.colors.textPrimary,
                            unfocusedTextColor = AppTheme.colors.textPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 2. Gemini API Key
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Gemini API Key (Gemini 3.5 Flash-Lite):", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DuroTextPrimary, modifier = Modifier.weight(1f))
                        TextButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = cm.primaryClip?.getItemAt(0)?.text?.toString()
                                if (!clip.isNullOrBlank()) {
                                    geminiKey = clip.trim()
                                    Toast.makeText(context, "Вставлено из буфера", Toast.LENGTH_SHORT).show()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            modifier = Modifier.wrapContentWidth()
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp), tint = AppTheme.colors.accent)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Вставить", fontSize = 11.sp, color = AppTheme.colors.accent, maxLines = 1, softWrap = false)
                        }
                    }
                    OutlinedTextField(
                        value = geminiKey,
                        onValueChange = { geminiKey = it },
                        placeholder = { Text("AIzaSy...", fontSize = 12.sp, color = DuroTextMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppTheme.colors.accent,
                            unfocusedBorderColor = DuroBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Test Connection Button
                Button(
                    onClick = {
                        isTesting = true
                        testStatus = "Проверка соединения..."
                        coroutineScope.launch {
                            val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).build()
                            // Проверяем эффективные ключи: введённые или встроенные в сборку.
                            val groqToTest = groqKey.trim().ifBlank {
                                com.voicehabit.tracker.data.local.BundledKeys.groq
                            }
                            val geminiToTest = geminiKey.trim().ifBlank {
                                com.voicehabit.tracker.data.local.BundledKeys.gemini
                            }
                            var groqOk = false
                            var geminiOk = false
                            withContext(Dispatchers.IO) {
                                try {
                                    val req = Request.Builder()
                                        .url("https://api.groq.com/openai/v1/models")
                                        .addHeader("Authorization", "Bearer $groqToTest")
                                        .build()
                                    val res = client.newCall(req).execute()
                                    groqOk = res.isSuccessful
                                } catch (e: Exception) {
                                    groqOk = false
                                }
                                try {
                                    val req = Request.Builder()
                                        .url("https://generativelanguage.googleapis.com/v1beta/models?key=$geminiToTest")
                                        .build()
                                    val res = client.newCall(req).execute()
                                    geminiOk = res.isSuccessful
                                } catch (e: Exception) {
                                    geminiOk = false
                                }
                            }
                            isTesting = false
                            testStatus = when {
                                groqOk && geminiOk -> "✅ Groq и Gemini готовы к работе!"
                                groqOk && !geminiOk -> "⚠️ Groq подключен, ошибка ключа Gemini"
                                !groqOk && geminiOk -> "⚠️ Gemini подключен, ошибка ключа Groq"
                                else -> "❌ Ошибка проверки ключей. Проверьте интернет и правильность ключей."
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF20202C))
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DuroOrange, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Проверить ключи API", fontSize = 13.sp)
                }

                if (testStatus != null) {
                    Text(
                        text = testStatus!!,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (testStatus!!.startsWith("✅")) DuroLime else DuroOrange
                    )
                }

                HorizontalDivider(color = DuroBorder)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = AppTheme.colors.accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Обновления по воздуху (OTA)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = DuroTextPrimary
                    )
                }

                val currentVer = com.voicehabit.tracker.core.update.github.GithubUpdateController.currentSemVer(context)?.toString() ?: "1.0.1"
                Text(
                    text = "Канал: GitHub Releases (DmKOwO/voice-habit-tracker). Обновления приходят по воздуху без ввода путей и папок. Текущая версия: v$currentVer.",
                    fontSize = 12.sp,
                    color = DuroTextSecondary
                )
                OutlinedButton(
                    onClick = {
                        githubUpdateController.check(auto = false)
                    },
                    enabled = githubUpdateState !is AppUpdateUiState.Checking,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text("Проверить обновление сейчас", fontSize = 12.sp)
                }
                UpdateStatusContent(
                    state = githubUpdateState,
                    onStart = githubUpdateController::startDownload,
                    onComplete = { githubUpdateController.installDownloaded() }
                )

                HorizontalDivider(color = AppTheme.colors.border)

                // 3. WIDGET PINNING SECTION
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Widgets, contentDescription = null, tint = AppTheme.colors.accent, modifier = Modifier.size(20.dp))
                    Text("Виджеты на рабочий стол", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.textPrimary)
                }

                Text(
                    text = "Нажмите кнопку ниже, чтобы система добавила виджет прямо на ваш экран:",
                    fontSize = 12.sp,
                    color = AppTheme.colors.textSecondary
                )

                // Pin Card 2x2
                OutlinedButton(
                    onClick = {
                        WidgetPinHelper.pinHabitCardWidget(context)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppTheme.colors.textPrimary)
                ) {
                    Text("Закрепить карточку 2x2 с хитмапом", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = {
                        WidgetPinHelper.pinOverviewWidget(context)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppTheme.colors.textPrimary)
                ) {
                    Text("Закрепить список привычек 4x2", fontSize = 12.sp)
                }

                // Pin Quick Mic 1x1
                OutlinedButton(
                    onClick = {
                        WidgetPinHelper.pinVoiceWidget(context)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppTheme.colors.textPrimary)
                ) {
                    Text("Закрепить микрофон быстрой записи (1x1)", fontSize = 12.sp)
                }

                HorizontalDivider(color = AppTheme.colors.border)

                // Внешний вид (F19)
                SettingsSectionTitle("Внешний вид")

                Text(
                    text = "Тема оформления",
                    fontSize = 12.sp,
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )

                var currentPreset by remember { mutableStateOf(settingsManager.themePreset) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppThemePreset.entries.forEach { preset ->
                        val selected = currentPreset == preset
                        val presetPalette = getPaletteForPreset(preset, false)
                        Surface(
                            color = presetPalette.surface,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                if (selected) 2.dp else 1.dp,
                                if (selected) presetPalette.accent else AppTheme.colors.border
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    currentPreset = preset
                                    settingsManager.themePreset = preset
                                    viewModel?.setThemePreset(preset)
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(presetPalette.accent)
                                    )
                                    Text(
                                        text = preset.title,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = presetPalette.textPrimary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = preset.subtitle,
                                    fontSize = 10.sp,
                                    color = presetPalette.textMuted
                                )
                            }
                        }
                    }
                }

                var amoled by remember { mutableStateOf(settingsManager.amoledTheme) }
                SettingsSwitch(
                    title = "AMOLED-чёрная тема",
                    subtitle = "Чистый чёрный фон",
                    checked = amoled,
                    onChecked = {
                        amoled = it
                        settingsManager.amoledTheme = it
                        viewModel?.refreshThemeState()
                    }
                )
                Text(
                    text = "Размер шрифта",
                    fontSize = 12.sp,
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.padding(top = 8.dp)
                )
                var fontScale by remember { mutableStateOf(settingsManager.fontScale) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(0.85f to "Мелкий", 1.0f to "Обычный", 1.15f to "Крупный", 1.3f to "Огромный").forEach { (scale, label) ->
                        val selected = fontScale == scale
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) AppTheme.colors.accent.copy(alpha = 0.2f) else AppTheme.colors.surface)
                                .clickable {
                                    fontScale = scale
                                    settingsManager.fontScale = scale
                                    viewModel?.setFontScale(scale)
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                color = if (selected) AppTheme.colors.accent else AppTheme.colors.textSecondary
                            )
                        }
                    }
                }
                Text(
                    text = "Язык",
                    fontSize = 12.sp,
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.padding(top = 8.dp)
                )
                var lang by remember { mutableStateOf(settingsManager.appLanguage) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("ru" to "Русский", "en" to "English").forEach { (code, label) ->
                        val selected = lang == code
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) AppTheme.colors.accent.copy(alpha = 0.2f) else AppTheme.colors.surface)
                                .clickable {
                                    lang = code
                                    viewModel?.setLanguage(code)
                                        ?: run { settingsManager.appLanguage = code }
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                color = if (selected) AppTheme.colors.accent else AppTheme.colors.textSecondary
                            )
                        }
                    }
                }

                HorizontalDivider(color = AppTheme.colors.border)

                // Голос (G1/F1)
                SettingsSectionTitle("Голос")
                var ttsOn by remember { mutableStateOf(settingsManager.ttsEnabled) }
                SettingsSwitch(
                    title = "Озвучка ответов",
                    subtitle = "Сводка дня и достижения вслух",
                    checked = ttsOn,
                    onChecked = {
                        ttsOn = it
                        settingsManager.ttsEnabled = it
                        viewModel?.setTtsEnabled(it)
                    }
                )
                var voskOn by remember { mutableStateOf(settingsManager.offlineSttEnabled) }
                SettingsSwitch(
                    title = "Офлайн-распознавание (Vosk)",
                    subtitle = "Без сети, модель ~40 МБ",
                    checked = voskOn,
                    onChecked = {
                        voskOn = it
                        viewModel?.setOfflineStt(it)
                            ?: run { settingsManager.offlineSttEnabled = it }
                    }
                )
                val voskStatus = viewModel?.let {
                    val s by it.state.collectAsState()
                    s.voskStatus to s.voskProgress
                }
                if (voskOn) {
                    Text(
                        text = when (voskStatus?.first) {
                            "READY" -> "Модель готова ✅"
                            "DOWNLOADING" -> "Скачивание: ${(100 * (voskStatus?.second ?: 0f)).toInt()}%"
                            "ERROR" -> "Ошибка загрузки — проверьте сеть"
                            else -> "Модель не загружена"
                        },
                        fontSize = 12.sp,
                        color = DuroTextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    if (voskStatus?.first == "DOWNLOADING") {
                        LinearProgressIndicator(
                            progress = { voskStatus?.second ?: 0f },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            color = AppTheme.colors.accent
                        )
                    }
                    if (voskStatus?.first != "READY") {
                        OutlinedButton(
                            onClick = { viewModel?.downloadVoskModel() },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Text("Скачать модель", fontSize = 12.sp, color = DuroTextPrimary)
                        }
                    }
                }

                HorizontalDivider(color = DuroBorder)

                // Уведомления (G22/G40)
                SettingsSectionTitle("Уведомления")
                var quiet by remember { mutableStateOf(settingsManager.quietHoursEnabled) }
                SettingsSwitch(
                    title = "Тихие часы 23:00–07:00",
                    subtitle = "Ночью не будим",
                    checked = quiet,
                    onChecked = {
                        quiet = it
                        settingsManager.quietHoursEnabled = it
                        viewModel?.setQuietHours(it)
                    }
                )
                var autoUpd by remember { mutableStateOf(settingsManager.autoUpdateCheck) }
                SettingsSwitch(
                    title = "Автопроверка обновлений",
                    subtitle = "Тихо, раз в сутки",
                    checked = autoUpd,
                    onChecked = {
                        autoUpd = it
                        settingsManager.autoUpdateCheck = it
                        viewModel?.setAutoUpdateCheck(it)
                    }
                )

                HorizontalDivider(color = DuroBorder)

                // Данные (F13)
                SettingsSectionTitle("Данные")
                OutlinedButton(
                    onClick = {
                        viewModel?.openScreen(com.voicehabit.tracker.presentation.home.AppScreen.BACKUP)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = viewModel != null
                ) {
                    Text("Бэкап и восстановление", fontSize = 12.sp, color = AppTheme.colors.textPrimary)
                }

                HorizontalDivider(color = AppTheme.colors.border)

                // Save & Close Button
                Button(
                    onClick = {
                        settingsManager.groqApiKey = groqKey
                        settingsManager.geminiApiKey = geminiKey
                        settingsManager.customBackendUrl = customUrl
                        settingsManager.useDirectCloud = useDirectCloud
                        Toast.makeText(context, "Настройки сохранены!", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppTheme.colors.accent,
                        contentColor = AppTheme.colors.surface
                    )
                ) {
                    Text("Сохранить настройки", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun UpdateStatusContent(
    state: AppUpdateUiState,
    onStart: () -> Unit,
    onComplete: () -> Unit
) {
    when (state) {
        AppUpdateUiState.Idle -> Text(
            text = "Проверка запускается автоматически или по кнопке выше.",
            fontSize = 12.sp,
            color = DuroTextMuted
        )
        AppUpdateUiState.Checking -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = AppTheme.colors.accent,
                strokeWidth = 2.dp
            )
            Text("Проверяем наличие обновлений на GitHub…", fontSize = 12.sp, color = DuroTextSecondary)
        }
        is AppUpdateUiState.Unavailable -> Text(
            text = state.reason,
            fontSize = 12.sp,
            color = DuroOrange
        )
        AppUpdateUiState.UpToDate -> Text(
            text = "Установленная версия актуальна.",
            fontSize = 12.sp,
            color = DuroLime
        )
        is AppUpdateUiState.Available -> Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Доступно обновление ${state.version} (${state.tag})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = DuroTextPrimary
                )
                if (state.prerelease) {
                    Text(
                        text = "beta",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = DuroOrange
                    )
                }
            }
            if (state.notes.isNotBlank()) {
                Text(
                    text = state.notes.take(600),
                    fontSize = 12.sp,
                    color = DuroTextSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 120.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
            state.sizeBytes?.let { size ->
                Text(
                    text = "Размер APK: ${formatBytes(size)}",
                    fontSize = 12.sp,
                    color = DuroTextMuted
                )
            }
            OutlinedButton(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text("Скачать обновление", fontSize = 12.sp)
            }
        }
        is AppUpdateUiState.InProgress -> Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.totalBytes > 0) {
                val progress = (state.bytesDownloaded.toFloat() / state.totalBytes).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = AppTheme.colors.accent,
                    trackColor = DuroBorder
                )
                Text(
                    text = "Загружено ${formatBytes(state.bytesDownloaded)} из ${formatBytes(state.totalBytes)} (${(progress * 100).toInt()}%)",
                    fontSize = 12.sp,
                    color = DuroTextSecondary
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = AppTheme.colors.accent,
                        strokeWidth = 2.dp
                    )
                    Text(
                        "Загрузка обновления… ${formatBytes(state.bytesDownloaded)}",
                        fontSize = 12.sp,
                        color = DuroTextSecondary
                    )
                }
            }
        }
        AppUpdateUiState.Downloaded -> Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Обновление успешно загружено и готово к установке.",
                fontSize = 12.sp,
                color = DuroLime
            )
            Button(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = DuroLime, contentColor = Color.Black)
            ) {
                Text(
                    "Установить обновление",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
        is AppUpdateUiState.Failed -> Column(
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Ошибка обновления: ${state.reason}",
                fontSize = 12.sp,
                color = DuroOrange
            )
            OutlinedButton(
                onClick = onStart,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text("Повторить загрузку", fontSize = 12.sp)
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 Б"
    val units = arrayOf("Б", "КБ", "МБ", "ГБ")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "${value.toLong()} ${units[unit]}"
    else "%.1f ${units[unit]}".format(value)
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = AppTheme.colors.textPrimary
    )
}

@Composable
private fun SettingsSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 13.sp, color = AppTheme.colors.textPrimary)
            Text(text = subtitle, fontSize = 11.sp, color = AppTheme.colors.textSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AppTheme.colors.accent,
                checkedTrackColor = AppTheme.colors.accent.copy(alpha = 0.4f)
            )
        )
    }
}
