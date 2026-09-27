package com.voicehabit.tracker.presentation.achievements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/** F16: витрина достижений. */
@Composable
fun AchievementsScreen(viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsState()
    val unlocked = state.unlockedAchievements

    Column(
        modifier = Modifier
            .fillMaxSize()
        .statusBarsPadding()
            .statusBarsPadding()
            .background(DuroBackground)
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
                        .background(if (isUnlocked) DuroSurface else DuroBackground)
                        .alpha(if (isUnlocked) 1f else 0.45f)
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = if (isUnlocked) def.icon else "🔒", fontSize = 32.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = def.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isUnlocked) DuroOrange else DuroTextSecondary
                    )
                    Text(
                        text = def.description,
                        fontSize = 11.sp,
                        color = DuroTextSecondary
                    )
                }
            }
        }
    }
}
