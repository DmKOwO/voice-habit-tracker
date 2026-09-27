package com.voicehabit.tracker.presentation.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicehabit.tracker.domain.model.Habit
import com.voicehabit.tracker.presentation.theme.*

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun DuroHabitGridCard(
    habit: Habit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onCardClick: () -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    heroKey: String? = null
) {
    val accentColor = try {
        Color(android.graphics.Color.parseColor(habit.colorHex))
    } catch (e: Exception) {
        DuroOrange
    }

    val animatedBgColor by animateColorAsState(
        targetValue = if (habit.isCompletedToday) accentColor.copy(alpha = 0.16f) else Color(0xFF141419),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "bgColor"
    )

    val animatedBorderColor by animateColorAsState(
        targetValue = if (habit.isCompletedToday) accentColor.copy(alpha = 0.6f) else DuroBorder,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "borderColor"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .duroPressable(onClick = onCardClick)
    ) {
        // Upper Squircle Visualization Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.05f)
                .clip(RoundedCornerShape(22.dp))
                .background(animatedBgColor)
                .border(1.dp, animatedBorderColor, RoundedCornerShape(22.dp))
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            // Quick-toggle check button on top right of squircle
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (habit.isCompletedToday) accentColor else Color(0x22FFFFFF))
                    .border(1.dp, if (habit.isCompletedToday) Color.Transparent else Color(0x44FFFFFF), CircleShape)
                    .duroPressable(onClick = onToggle),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = habit.isCompletedToday,
                    transitionSpec = {
                        (fadeIn(DuroIconSpring) + scaleIn(DuroIconSpring, initialScale = 0.68f))
                            .togetherWith(fadeOut(DuroContentSpring) + scaleOut(DuroContentSpring, targetScale = 0.68f))
                    },
                    label = "gridCheckMorph"
                ) { completed ->
                    if (completed) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Выполнено",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
            when (habit.displayType.uppercase()) {
                "STREAKS" -> {
                    // Flame + Streak count
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(accentColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = "Streaks",
                                tint = accentColor,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${habit.currentStreak} Streaks",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false
                        )
                    }
                }
                "GRID" -> {
                    // 4 x 7 Heatmap Grid of rounded squares
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val history = if (habit.historyDaysCompleted.size >= 28) {
                            habit.historyDaysCompleted.takeLast(28)
                        } else {
                            val list = MutableList(28) { false }
                            if (habit.isCompletedToday) list[27] = true
                            list
                        }

                        for (row in 0 until 4) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                for (col in 0 until 7) {
                                    val idx = row * 7 + col
                                    val isFilled = history.getOrElse(idx) { false }
                                    val isToday = idx == 27

                                    Box(
                                        modifier = Modifier
                                            .size(9.dp)
                                            .clip(RoundedCornerShape(2.5.dp))
                                            .background(
                                                when {
                                                    isToday && habit.isCompletedToday -> accentColor
                                                    isFilled -> accentColor.copy(alpha = 0.85f)
                                                    else -> Color(0xFF262633)
                                                }
                                            )
                                            .then(
                                                if (isToday && !habit.isCompletedToday) {
                                                    Modifier.border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(2.5.dp))
                                                } else Modifier
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
                "BAR_GRAPH" -> {
                    // 7-day Weekly Bar Graph
                    val days = listOf("M", "T", "W", "T", "F", "S", "S")
                    val weekly = if (habit.weeklyCompletions.size >= 7) {
                        habit.weeklyCompletions.takeLast(7)
                    } else {
                        val list = MutableList(7) { false }
                        list[6] = habit.isCompletedToday
                        list
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        days.forEachIndexed { i, dayLetter ->
                            val done = weekly.getOrElse(i) { false }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(7.dp)
                                        .height(if (done) 42.dp else 14.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(
                                            if (done) accentColor else Color(0xFF282836)
                                        )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = dayLetter,
                                    fontSize = 8.sp,
                                    color = DuroTextMuted
                                )
                            }
                        }
                    }
                }
                "MINIMAL" -> {
                    // Minimal circular percentage ring
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(54.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { (habit.completionPercentage / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxSize(),
                            color = accentColor,
                            trackColor = Color(0xFF282835),
                            strokeWidth = 4.dp
                        )
                        Text(
                            text = "${habit.completionPercentage}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = DuroTextPrimary
                        )
                    }
                }
                else -> { // "DAILY_CHECK"
                    // Large Check Circle with "Today"
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(
                                    if (habit.isCompletedToday) accentColor else Color(0xFF1E1E28)
                                )
                                .border(
                                    width = if (habit.isCompletedToday) 0.dp else 1.5.dp,
                                    color = if (habit.isCompletedToday) Color.Transparent else accentColor.copy(alpha = 0.5f),
                                    shape = CircleShape
                                )
                                .duroPressable(onClick = onToggle),
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedContent(
                                targetState = habit.isCompletedToday,
                                transitionSpec = {
                                    (fadeIn(DuroIconSpring) + scaleIn(DuroIconSpring, initialScale = 0.68f))
                                        .togetherWith(fadeOut(DuroContentSpring) + scaleOut(DuroContentSpring, targetScale = 0.68f))
                                },
                                label = "dailyCheckMorph"
                            ) { completed ->
                                if (completed) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Done",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Today",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (habit.isCompletedToday) accentColor else DuroTextMuted
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Lower Metadata: Title, Category, and Percentage
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .duroHero(
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    key = heroKey
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = habit.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                    fontWeight = FontWeight.Bold,
                    color = DuroTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = habit.category,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = DuroTextSecondary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = if (habit.completionPercentage > 0) "${habit.completionPercentage}%" else "— %",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = DuroTextMuted
                ),
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
