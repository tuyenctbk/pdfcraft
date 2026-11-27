package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Adds a crisp tactile spring press-scale animation combined with Material 3 ripple feedback.
 */
fun Modifier.bounceClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.965f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bounceScale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = ripple(),
            enabled = enabled,
            onClick = onClick
        )
}

/**
 * Smooth staggered entrance reveal (fade + gentle upward spring settle) for cards and grid tiles.
 */
fun Modifier.staggeredEntrance(
    index: Int,
    delayStepMs: Int = 36,
    initialOffsetYPx: Float = 26f
): Modifier = composed {
    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(initialOffsetYPx) }

    LaunchedEffect(Unit) {
        val clampedDelay = (index.coerceIn(0, 10) * delayStepMs).toLong()
        if (clampedDelay > 0L) delay(clampedDelay)
        launch {
            alpha.animateTo(1f, tween(durationMillis = 240, easing = FastOutSlowInEasing))
        }
        launch {
            offsetY.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    this.graphicsLayer {
        this.alpha = alpha.value
        this.translationY = offsetY.value
    }
}
