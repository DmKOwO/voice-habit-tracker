package com.voicehabit.tracker.presentation.routines

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.voicehabit.tracker.domain.model.Routine
import com.voicehabit.tracker.presentation.home.HomeViewModel
import com.voicehabit.tracker.presentation.hub.ScreenHeader
import com.voicehabit.tracker.presentation.theme.*
import java.util.UUID

/** F2: голосовые макросы — одна фраза отмечает несколько привычек. */
@Composable
fun RoutinesScreen(viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
        .statusBarsPadding()
            .statusBarsPadding()
            .background(DuroBackground)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        ScreenHeader(title = "Рутины", subtitle = "Скажите триггер — отметится всё сразу") {
            viewModel.closeScreen()
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Пример: рутина «Утро» с триггером «утренняя рутина» — фраза «выполни утреннюю рутину» отметит все её привычки.",
            fontSize = 12.sp,
            color = DuroTextSecondary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = {
                viewModel.openRoutineEditor(
                    Routine(id = "rtn_" + UUID.randomUUID().toString(), title = "", triggerPhrase = "")
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = DuroOrange),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("+ Новая рутина")
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (state.routines.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                Text(text = "Рутин пока нет", fontSize = 13.sp, color = DuroTextSecondary)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                items(state.routines, key = { it.id }) { routine ->
                    val names = routine.habitIds.mapNotNull { id ->
                        state.habits.find { it.id == id }?.title
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(DuroSurface)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = routine.title.ifBlank { "Без названия" },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = DuroTextPrimary
                            )
                            Text(
                                text = "«${routine.triggerPhrase}» · ${names.size} прив.: ${names.take(3).joinToString(", ")}",
                                fontSize = 11.sp,
                                color = DuroTextSecondary
                            )
                        }
                        IconButton(
                            onClick = { viewModel.runRoutine(routine.id) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Запустить рутину",
                                tint = AppTheme.colors.accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.openRoutineEditor(routine) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Редактировать",
                                tint = AppTheme.colors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.deleteRoutine(routine.id) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Удалить",
                                tint = AppTheme.colors.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    state.editingRoutine?.let { routine ->
        RoutineEditor(
            routine = routine,
            habitTitles = state.habits.associate { it.id to it.title },
            onSave = { viewModel.saveRoutine(it) },
            onDismiss = { viewModel.closeRoutineEditor() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineEditor(
    routine: Routine,
    habitTitles: Map<String, String>,
    onSave: (Routine) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember(routine.id) { mutableStateOf(routine.title) }
    var trigger by remember(routine.id) { mutableStateOf(routine.triggerPhrase) }
    var selected by remember(routine.id) { mutableStateOf(routine.habitIds.toSet()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = DuroSurface,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(text = "Рутина", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = DuroTextPrimary)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = trigger,
                    onValueChange = { trigger = it },
                    label = { Text("Триггер-фраза", fontSize = 12.sp) },
                    placeholder = { Text("утренняя рутина", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "Привычки в рутине:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DuroTextSecondary)
                habitTitles.forEach { (id, name) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = if (id in selected) selected - id else selected + id
                            }
                    ) {
                        Checkbox(
                            checked = id in selected,
                            onCheckedChange = {
                                selected = if (id in selected) selected - id else selected + id
                            },
                            colors = CheckboxDefaults.colors(checkedColor = DuroOrange)
                        )
                        Text(text = name, fontSize = 13.sp, color = DuroTextPrimary)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        onSave(routine.copy(title = title.trim(), triggerPhrase = trigger.trim(), habitIds = selected.toList()))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DuroOrange),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = title.isNotBlank() && trigger.isNotBlank() && selected.isNotEmpty()
                ) {
                    Text("Сохранить")
                }
            }
        }
    }
}
