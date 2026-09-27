package com.voicehabit.tracker.presentation.theme

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.SharedContentState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared physics so repeated animations feel cinematic instead of mechanical.
 *
 * Tweens change the clock. Springs change the material. Every spring below is
 * tuned for 120 Hz: enough overshoot to feel alive, but settled quickly enough
 * to avoid another frame of visible motion.
 */
val DuroPressSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMedium
)
val DuroContentSpring = spring<Float>(
    dampingRatio = 0.82f,
    stiffness = 420f
)
val DuroIconSpring = spring<Float>(
    dampingRatio = 0.48f,
    stiffness = 520f
)
// Цвет и смещение требуют своих типов AnimationSpec — Float-спринг сюда нельзя.
val DuroColorSpring = spring<Color>(
    dampingRatio = 0.82f,
    stiffness = 420f
)
val DuroOffsetSpring = spring<androidx.compose.ui.unit.IntOffset>(
    dampingRatio = 0.82f,
    stiffness = 420f
)

/**
 * Responsive press target: the surface compresses under the finger and springs
 * back on release. The scale is drawn on the graphics layer, so child content
 * does not need to be recomposed while the finger is down.
 */
@Composable
fun Modifier.duroPressable(
    enabled: Boolean = true,
    pressedScale: Float = 0.94f,
    haptic: HapticFeedbackType? = null,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = DuroPressSpring,
        label = "duroPressScale"
    )
    val feedback = LocalHapticFeedback.current

    return this
        .graphicsLayer {
            scaleX = pressScale
            scaleY = pressScale
            transformOrigin = TransformOrigin.Center
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = Role.Button,
            onClick = {
                if (haptic != null) feedback.performHapticFeedback(haptic)
                onClick()
            }
        )
}

/**
 * Press-only visual for components that already consume clicks, such as
 * Material 3 IconButton. Call it with the same interaction source that the
 * clickable component uses so the scale exactly tracks the press.
 */
@Composable
fun Modifier.duroPressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.9f
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = DuroPressSpring,
        label = "duroIconPressScale"
    )
    return graphicsLayer {
        scaleX = pressScale
        scaleY = pressScale
        transformOrigin = TransformOrigin.Center
    }
}

/** Signature circular action: spring press, tactile feedback, compact action target. */
@Composable
fun DuroIconActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    containerColor: Color,
    iconTint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 20.dp,
    pressedScale: Float = 0.9f,
    haptic: HapticFeedbackType = HapticFeedbackType.TextHandleMove
) {
    val interactionSource = remember { MutableInteractionSource() }
    val feedback = LocalHapticFeedback.current

    IconButton(
        onClick = {
            feedback.performHapticFeedback(haptic)
            onClick()
        },
        interactionSource = interactionSource,
        modifier = modifier
            .duroPressScale(interactionSource, pressedScale)
            .size(size)
            .clip(CircleShape)
            .background(containerColor)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(iconSize)
        )
    }
}

/** Hairline separator: tonal gradient instead of a hard gray rule. */
@Composable
fun DuroHairline(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        DuroBorder.copy(alpha = 0.72f),
                        Color.Transparent
                    )
                )
            )
    )
}

/**
 * Shared identity transition. Pass the same [key] in both the source card and
 * the detail sheet, for example `"habit-${habit.id}-hero"`.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.duroHero(
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    key: String?
): Modifier {
    if (sharedTransitionScope == null ||
        animatedVisibilityScope == null ||
        key == null
    ) {
        return this
    }
    val sharedContentState = sharedTransitionScope.rememberSharedContentState(key)
    return with(sharedTransitionScope) {
        sharedBounds(
            sharedContentState = sharedContentState,
            animatedVisibilityScope = animatedVisibilityScope,
            enter = androidx.compose.animation.fadeIn(DuroContentSpring),
            exit = androidx.compose.animation.fadeOut(DuroContentSpring),
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds
        )
    }
}

/** Tactile vocabulary: every haptic has a consistent meaning. */
@Stable
class DuroHaptics(private val feedback: HapticFeedback) {
    /** Selection, tab change, and other lightweight navigation. */
    fun select() = feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)

    /** Important confirmation: record start/stop, habit completion, save. */
    fun confirm() = feedback.performHapticFeedback(HapticFeedbackType.LongPress)

    /** Destructive or invalid action. */
    fun reject() = feedback.performHapticFeedback(HapticFeedbackType.LongPress)
}

@Composable
fun rememberDuroHaptics(): DuroHaptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { DuroHaptics(feedback) }
}

/**
 * Elegant shimmer instead of a spinning loader. The moving gradient is drawn
 * in the draw phase, so the sweep does not add layout nodes or measure passes.
 */
@Composable
fun Modifier.duroShimmer(
    visible: Boolean = true,
    baseAlpha: Float = 0.24f,
    highlightAlpha: Float = 0.55f
): Modifier {
    if (!visible) return this
    val transition = rememberInfiniteTransition(label = "duroShimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1450,
                easing = FastOutSlowInEasing
            )
        ),
        label = "duroShimmerProgress"
    )
    return drawWithContent {
        drawContent()
        val sweepWidth = size.width * 0.34f
        val startX = size.width * (progress * 1.42f - 0.21f) - sweepWidth
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = highlightAlpha),
                    Color.Transparent
                ),
                start = Offset(startX, 0f),
                end = Offset(startX + sweepWidth, 0f)
            ),
            alpha = (baseAlpha + highlightAlpha) / 2f
        )
    }
}

/** Graceful loading state for the voice/audio status banner. */
@Composable
fun DuroSkeletonLine(
    widthFraction: Float = 0.62f,
    height: Dp = 12.dp,
    cornerRadius: Dp = 6.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(DuroSurfaceElevated.copy(alpha = 0.72f))
            .duroShimmer()
    )
}

/** Graceful loading state for the voice/audio status banner. */
@Composable
fun DuroLoadingBanner(
    status: String? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        color = DuroSurface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DuroBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            DuroSkeletonLine(widthFraction = 0.42f)
            Spacer(modifier = Modifier.height(10.dp))
            DuroSkeletonLine(widthFraction = 0.78f, height = 10.dp)
            if (!status.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = DuroTextSecondary
                    )
                )
            }
        }
    }
}
