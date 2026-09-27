package com.voicehabit.tracker.presentation.challenges

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.voicehabit.tracker.domain.model.Challenge
import com.voicehabit.tracker.presentation.home.HomeViewModel
import com.voicehabit.tracker.presentation.hub.ScreenHeader
import com.voicehabit.tracker.presentation.theme.*
import java.time.LocalDate
import java.util.UUID

/** F17: челленджи на N дней с календарём прогресса. */
@Composable
fun ChallengesScreen(viewModel: HomeViewModel) {
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
        ScreenHeader(title = "Челленджи", subtitle = "Держите серию день за днём") {
            viewModel.closeScreen()
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = {
                viewModel.openChallengeEditor(
                    Challenge(
                        id = "chl_" + UUID.randomUUID().toString(),
                        title = "",
                        startEpochDay = LocalDate.now().toEpochDay()
                    )
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = DuroOrange),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("+ Новый челлендж")
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (state.challenges.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                Text(text = "Челленджей пока нет — начните с 30 дней", fontSize = 13.sp, color = DuroTextSecondary)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                items(state.challenges, key = { it.id }) { challenge ->
                    ChallengeCard(viewModel = viewModel, challenge = challenge)
                }
            }
        }
    }

    state.editingChallenge?.let { challenge ->
        ChallengeEditor(
            challenge = challenge,
            habitTitles = state.habits.associate { it.id to it.title },
            onSave = { viewModel.saveChallenge(it) },
            onDismiss = { viewModel.closeChallengeEditor() }
        )
    }
}

@Composable
private fun ChallengeCard(viewModel: HomeViewModel, challenge: Challenge) {
    var progress by remember(challenge.id) { mutableStateOf(0 to challenge.lengthDays) }
    LaunchedEffect(challenge.id) {
        progress = viewModel.challengeProgressDays(challenge)
    }
    val (done, total) = progress
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DuroSurface)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = challenge.title.ifBlank { "Без названия" },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = DuroTextPrimary
                )
                Text(
                    text = "$done / $total дней",
                    fontSize = 12.sp,
                    color = if (done >= total) DuroLime else DuroOrange
                )
            }
            TextButton(onClick = { viewModel.openChallengeEditor(challenge) }) {
                Text(text = "✎", color = DuroTextSecondary)
            }
            TextButton(onClick = { viewModel.deleteChallenge(challenge.id) }) {
                Text(text = "✕", color = DuroRed)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { if (total > 0) done.toFloat() / total else 0f },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = DuroLime,
            trackColor = DuroBorder
        )
        if (done >= total && challenge.isActive) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    viewModel.saveChallenge(challenge.copy(isActive = false))
                    viewModel.evaluateChallengeDone()
                },
                colors = ButtonDefaults.buttonColors(containerColor = DuroLime),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🏁 Завершить челлендж", color = androidx.compose.ui.graphics.Color.Black)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChallengeEditor(
    challenge: Challenge,
    habitTitles: Map<String, String>,
    onSave: (Challenge) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember(challenge.id) { mutableStateOf(challenge.title) }
    var habitId by remember(challenge.id) { mutableStateOf(challenge.habitId) }
    var length by remember(challenge.id) { mutableStateOf(challenge.lengthDays) }
    var note by remember(challenge.id) { mutableStateOf(challenge.note) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(color = DuroSurface, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(text = "Челлендж", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = DuroTextPrimary)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Название", fontSize = 12.sp) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Привычка:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DuroTextSecondary)
                habitTitles.forEach { (id, name) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { habitId = if (habitId == id) null else id }
                    ) {
                        RadioButton(
                            selected = habitId == id,
                            onClick = { habitId = if (habitId == id) null else id },
                            colors = RadioButtonDefaults.colors(selectedColor = DuroOrange)
                        )
                        Text(text = name, fontSize = 13.sp, color = DuroTextPrimary)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Длина: $length дней", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DuroTextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7, 30, 60, 100).forEach { option ->
                        val selected = length == option
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) DuroOrange.copy(alpha = 0.25f) else DuroBackground)
                                .clickable { length = option }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$option",
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) DuroOrange else DuroTextSecondary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("Ставка/заметка", fontSize = 12.sp) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        onSave(challenge.copy(title = title.trim(), habitId = habitId, lengthDays = length, note = note.trim()))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DuroOrange),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = title.isNotBlank()
                ) {
                    Text("Сохранить")
                }
            }
        }
    }
}
