package com.voicehabit.tracker.presentation.achievements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import com.voicehabit.tracker.R
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicehabit.tracker.domain.model.ALL_ACHIEVEMENTS
import com.voicehabit.tracker.presentation.home.HomeViewModel
import com.voicehabit.tracker.presentation.hub.ScreenHeader
import com.voicehabit.tracker.presentation.theme.*

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/** F16: витрина достижений. */
@Composable
fun AchievementsScreen(viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsState()
    val unlocked = state.unlockedAchievements

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(AppTheme.colors.background)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        ScreenHeader(
            title = "Достижения",
            subtitle = "${unlocked.size} из ${ALL_ACHIEVEMENTS.size} открыто"
        ) { viewModel.closeScreen() }
        Spacer(modifier = Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            items(ALL_ACHIEVEMENTS) { def ->
                val isUnlocked = def.id in unlocked
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isUnlocked) AppTheme.colors.surface else AppTheme.colors.background)
                        .alpha(if (isUnlocked) 1f else 0.45f)
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (isUnlocked) AppTheme.colors.accent.copy(alpha = 0.15f) else AppTheme.colors.surfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isUnlocked) {
                            Icon(
                                imageVector = getAchievementIcon(def.id),
                                contentDescription = null,
                                tint = AppTheme.colors.accent,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Заблокировано",
                                tint = AppTheme.colors.textMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = def.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isUnlocked) AppTheme.colors.accent else AppTheme.colors.textSecondary
                    )
                    Text(
                        text = def.description,
                        fontSize = 11.sp,
                        color = AppTheme.colors.textSecondary
                    )
                }
            }
        }
    }
}

private fun getAchievementIcon(id: String): ImageVector = when (id) {
    "first_habit" -> Icons.Default.AddCircleOutline
    "first_voice" -> Icons.Default.Mic
    "streak_7" -> Icons.Default.TrendingUp
    "streak_30" -> Icons.Default.Diamond
    "tasks_10" -> Icons.Default.CheckCircle
    "tasks_100" -> Icons.Default.DoneAll
    "focus_5" -> Icons.Default.Timer
    "early_bird" -> Icons.Default.WbSunny
    "night_owl" -> Icons.Default.Nightlight
    "perfect_week" -> Icons.Default.Star
    "routine_master" -> Icons.Default.PlayCircleOutline
    "challenge_done" -> Icons.Default.Flag
    else -> Icons.Default.EmojiEvents
}
