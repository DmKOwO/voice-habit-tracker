package com.voicehabit.tracker.presentation.hub

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicehabit.tracker.core.analysis.TokenOverlapSearch
import com.voicehabit.tracker.domain.model.DigestRecord
import com.voicehabit.tracker.domain.model.Habit
import com.voicehabit.tracker.domain.model.Task
import com.voicehabit.tracker.presentation.theme.AppTheme

/**
 * Глобальный поиск по всему: привычки, задачи, конспекты.
 * Ранжирование — по пересечению токенов (TokenOverlapSearch), а не substring:
 * «релиз команды» находит «созвон с командой про релиз».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchSheet(
    query: String,
    onQueryChange: (String) -> Unit,
    habits: List<Habit>,
    tasks: List<Task>,
    digests: List<DigestRecord>,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AppTheme.colors.surface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 36.dp)
        ) {
            Text("Поиск по всему", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AppTheme.colors.textPrimary)
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Привычки, задачи, конспекты…") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (query.length < 2) {
                Text("Введите хотя бы 2 символа", fontSize = 13.sp, color = AppTheme.colors.textMuted)
                return@Column
            }
            val habitHits = TokenOverlapSearch.rank(query, habits, { it.title }).take(5)
            val taskHits = TokenOverlapSearch.rank(query, tasks, { it.title }).take(5)
            val digestHits = TokenOverlapSearch.rank(
                query, digests, { (it.title + " " + it.gist + " " + it.keyPoints.joinToString(" ")) }
            ).take(5)
            if (habitHits.isEmpty() && taskHits.isEmpty() && digestHits.isEmpty()) {
                Text("Ничего не найдено", fontSize = 13.sp, color = AppTheme.colors.textMuted)
                return@Column
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (habitHits.isNotEmpty()) {
                    item { Text("Привычки", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = AppTheme.colors.accent) }
                    items(habitHits) { (h, s) ->
                        Text("• ${h.title} (${(s * 100).toInt()}%)", fontSize = 14.sp, color = AppTheme.colors.textPrimary)
                    }
                }
                if (taskHits.isNotEmpty()) {
                    item { Text("Задачи", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = AppTheme.colors.accent) }
                    items(taskHits) { (t, s) ->
                        Text("• ${t.title} (${(s * 100).toInt()}%)", fontSize = 14.sp, color = AppTheme.colors.textPrimary)
                    }
                }
                if (digestHits.isNotEmpty()) {
                    item { Text("Конспекты", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = AppTheme.colors.accent) }
                    items(digestHits) { (d, s) ->
                        Text("• ${d.title.ifBlank { d.gist.take(40) }} (${(s * 100).toInt()}%)", fontSize = 14.sp, color = AppTheme.colors.textPrimary)
                    }
                }
            }
        }
    }
}
