package com.voicehabit.tracker.presentation.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicehabit.tracker.presentation.home.HomeViewModel
import com.voicehabit.tracker.presentation.hub.ScreenHeader
import com.voicehabit.tracker.presentation.theme.*

/** F13/G34/G35: экспорт, импорт с предпросмотром, автобэкап. */
@Composable
fun BackupScreen(viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    var pickedText by remember { mutableStateOf("") }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                pickedText = text
                viewModel.previewImport(text)
            } catch (e: Exception) {
                viewModel.showSnackbar("Файл не читается: ${e.message}")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
        .statusBarsPadding()
            .statusBarsPadding()
            .background(DuroBackground)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        ScreenHeader(title = "Бэкап", subtitle = "Ваши данные — только ваши") { viewModel.closeScreen() }
        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Экспорт", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DuroTextSecondary)
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { viewModel.doBackup() },
            colors = ButtonDefaults.buttonColors(containerColor = DuroOrange),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("💾 Создать бэкап JSON")
        }
        if (state.lastBackupName != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Последний: ${state.lastBackupName}", fontSize = 12.sp, color = DuroLime)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { viewModel.shareLastBackup(context) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("📤 Поделиться файлом", color = DuroTextPrimary)
            }
        }
        Text(
            text = "Еженедельный автобэкап включён и выполняется в фоне.",
            fontSize = 11.sp,
            color = DuroTextSecondary,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))
        Text(text = "Импорт", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DuroTextSecondary)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = { pickFile.launch("application/json") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("📥 Выбрать файл бэкапа", color = DuroTextPrimary)
        }

        state.importPreview?.let { preview ->
            Spacer(modifier = Modifier.height(12.dp))
            Surface(color = DuroSurface, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Найдено строк: ${preview.tables.values.sum()}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = DuroTextPrimary
                    )
                    preview.tables.forEach { (table, count) ->
                        Text(text = "• $table: $count", fontSize = 12.sp, color = DuroTextSecondary)
                    }
                    preview.warnings.forEach { warning ->
                        Text(text = "⚠ $warning", fontSize = 12.sp, color = DuroAmber)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.doImport(pickedText) },
                            colors = ButtonDefaults.buttonColors(containerColor = DuroLime),
                            modifier = Modifier.weight(1f),
                            enabled = pickedText.isNotBlank()
                        ) {
                            Text("Импортировать", color = androidx.compose.ui.graphics.Color.Black)
                        }
                        OutlinedButton(
                            onClick = { viewModel.clearImportPreview() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Отмена", color = DuroTextPrimary)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(120.dp))
    }
}
