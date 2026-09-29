package com.voicehabit.tracker.presentation.hub

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicehabit.tracker.presentation.theme.AppTheme

/**
 * Жалоба на баг/UI прямо из приложения: категория + текст + версия + хвост лога.
 * Отправляется через системный шаринг — не нужно идти со скриншотами.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackSheet(
    appVersion: String,
    recentLog: String,
    usageSummary: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var category by remember { mutableStateOf("Баг") }
    var text by remember { mutableStateOf("") }
    var includeLog by remember { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AppTheme.colors.surface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 36.dp)
        ) {
            Text("Сообщить о проблеме", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AppTheme.colors.textPrimary)
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Баг", "UI", "Идея").forEach { c ->
                    FilterChip(
                        selected = category == c,
                        onClick = { category = c },
                        label = { Text(c) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Что сломалось и где…") },
                minLines = 3,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                Checkbox(checked = includeLog, onCheckedChange = { includeLog = it })
                Text(
                    "Приложить лог и версию ($appVersion)",
                    fontSize = 13.sp,
                    color = AppTheme.colors.textPrimary,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    val body = buildString {
                        append("[$category] dairy $appVersion\n\n")
                        append(text.ifBlank { "(без описания)" })
                        append("\n\n--- использование ---\n$usageSummary")
                        if (includeLog) append("\n\n--- лог ---\n${recentLog.take(3000)}")
                    }
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "dairy: $category")
                        putExtra(Intent.EXTRA_TEXT, body)
                    }
                    context.startActivity(Intent.createChooser(send, "Отправить отчёт"))
                    onDismiss()
                },
                enabled = text.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Отправить")
            }
        }
    }
}
