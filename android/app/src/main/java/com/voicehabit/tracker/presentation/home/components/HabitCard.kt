package com.voicehabit.tracker.presentation.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicehabit.tracker.domain.model.Habit
import com.voicehabit.tracker.presentation.theme.*

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun HabitCard(
    habit: Habit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onCardClick: () -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    heroKey: String? = null
) {
    val defaultAccent = AppTheme.colors.accent
    val accentColor = try {
        Color(android.graphics.Color.parseColor(habit.colorHex))
    } catch (e: Exception) {
        defaultAccent
    }

    val animatedBg by animateColorAsState(
        targetValue = if (habit.isCompletedToday) accentColor.copy(alpha = 0.12f) else AppTheme.colors.surface,
        animationSpec = DuroColorSpring,
        label = "bg"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, if (habit.isCompletedToday) accentColor.copy(0.4f) else AppTheme.colors.border.copy(alpha = 0.72f), RoundedCornerShape(18.dp))
            .duroPressable(onClick = onCardClick),
        colors = CardDefaults.cardColors(containerColor = animatedBg)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Check circle
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (habit.isCompletedToday) accentColor else AppTheme.colors.surfaceElevated
                        )
                        .border(
                            width = if (habit.isCompletedToday) 0.dp else 1.5.dp,
                            color = if (habit.isCompletedToday) Color.Transparent else AppTheme.colors.border,
                            shape = CircleShape
                        )
                        .duroPressable(
                            haptic = HapticFeedbackType.LongPress,
                            onClick = onToggle
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = habit.isCompletedToday,
                        transitionSpec = {
                            (fadeIn(DuroIconSpring) + scaleIn(DuroIconSpring, initialScale = 0.68f))
                                .togetherWith(fadeOut(DuroContentSpring) + scaleOut(DuroContentSpring, targetScale = 0.68f))
                        },
                        label = "habitCheckMorph"
                    ) { completed ->
                        if (completed) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Выполнено",
                                tint = AppTheme.colors.surface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .duroHero(
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            key = heroKey
                        )
                ) {
                    Text(
                        text = habit.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = habit.category,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = AppTheme.colors.textSecondary
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = AppTheme.colors.textMuted
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "${habit.currentStreak} дн.",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.accent
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            if (habit.quote.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = habit.quote,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = AppTheme.colors.textMuted
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                    modifier = Modifier.weight(0.6f)
                )
            }
        }
    }
}
