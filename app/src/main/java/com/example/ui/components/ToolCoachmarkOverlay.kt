package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.data.local.PdfOperationType

data class CoachmarkStep(
    val targetOperation: PdfOperationType,
    @StringRes val badgeRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val summaryRes: Int,
    val howToStepResIds: List<Int>,
    @StringRes val tryButtonLabelRes: Int,
    val icon: ImageVector,
    val accentColor: Color
)

val DEFAULT_COACHMARK_STEPS = listOf(
    CoachmarkStep(
        targetOperation = PdfOperationType.MERGE,
        badgeRes = R.string.coachmark_step1_badge,
        titleRes = R.string.coachmark_step1_title,
        summaryRes = R.string.coachmark_step1_summary,
        howToStepResIds = listOf(
            R.string.coachmark_step1_tip1,
            R.string.coachmark_step1_tip2,
            R.string.coachmark_step1_tip3
        ),
        tryButtonLabelRes = R.string.coachmark_step1_try,
        icon = Icons.Default.Merge,
        accentColor = Color(0xFF10B981)
    ),
    CoachmarkStep(
        targetOperation = PdfOperationType.SPLIT,
        badgeRes = R.string.coachmark_step2_badge,
        titleRes = R.string.coachmark_step2_title,
        summaryRes = R.string.coachmark_step2_summary,
        howToStepResIds = listOf(
            R.string.coachmark_step2_tip1,
            R.string.coachmark_step2_tip2,
            R.string.coachmark_step2_tip3
        ),
        tryButtonLabelRes = R.string.coachmark_step2_try,
        icon = Icons.Default.ContentCut,
        accentColor = Color(0xFF3B82F6)
    ),
    CoachmarkStep(
        targetOperation = PdfOperationType.COMPRESS,
        badgeRes = R.string.coachmark_step3_badge,
        titleRes = R.string.coachmark_step3_title,
        summaryRes = R.string.coachmark_step3_summary,
        howToStepResIds = listOf(
            R.string.coachmark_step3_tip1,
            R.string.coachmark_step3_tip2,
            R.string.coachmark_step3_tip3
        ),
        tryButtonLabelRes = R.string.coachmark_step3_try,
        icon = Icons.Default.Compress,
        accentColor = Color(0xFFF59E0B)
    ),
    CoachmarkStep(
        targetOperation = PdfOperationType.ROTATE,
        badgeRes = R.string.coachmark_step4_badge,
        titleRes = R.string.coachmark_step4_title,
        summaryRes = R.string.coachmark_step4_summary,
        howToStepResIds = listOf(
            R.string.coachmark_step4_tip1,
            R.string.coachmark_step4_tip2,
            R.string.coachmark_step4_tip3
        ),
        tryButtonLabelRes = R.string.coachmark_step4_try,
        icon = Icons.AutoMirrored.Filled.RotateRight,
        accentColor = Color(0xFF8B5CF6)
    )
)

@Composable
fun ToolCoachmarkOverlay(
    currentStepIndex: Int,
    steps: List<CoachmarkStep> = DEFAULT_COACHMARK_STEPS,
    targetBoundsInParent: Rect?,
    onStepChange: (Int) -> Unit,
    onTryTool: (PdfOperationType) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onDismiss() }

    val safeIndex = currentStepIndex.coerceIn(0, steps.lastIndex)
    val step = steps[safeIndex]
    val density = LocalDensity.current

    val infiniteTransition = rememberInfiniteTransition(label = "coachmarkPulse")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("tool_coachmark_overlay")
    ) {
        val containerHeightPx = with(density) { maxHeight.toPx() }
        val spotlightPaddingPx = with(density) { 6.dp.toPx() }
        val cornerRadiusPx = with(density) { 18.dp.toPx() }
        val animatedAccent by animateColorAsState(
            targetValue = step.accentColor,
            animationSpec = tween(260),
            label = "coachmarkAccent"
        )
        val hasSpot = targetBoundsInParent != null && !targetBoundsInParent.isEmpty &&
            targetBoundsInParent.width > 0f && targetBoundsInParent.height > 0f
        val spotSpec = spring<Float>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
        val animLeft by animateFloatAsState(
            targetValue = if (hasSpot) (targetBoundsInParent!!.left - spotlightPaddingPx).coerceAtLeast(0f) else 0f,
            animationSpec = spotSpec,
            label = "spotLeft"
        )
        val animTop by animateFloatAsState(
            targetValue = if (hasSpot) (targetBoundsInParent!!.top - spotlightPaddingPx).coerceAtLeast(0f) else 0f,
            animationSpec = spotSpec,
            label = "spotTop"
        )
        val animRight by animateFloatAsState(
            targetValue = if (hasSpot) (targetBoundsInParent!!.right + spotlightPaddingPx) else 0f,
            animationSpec = spotSpec,
            label = "spotRight"
        )
        val animBottom by animateFloatAsState(
            targetValue = if (hasSpot) (targetBoundsInParent!!.bottom + spotlightPaddingPx) else 0f,
            animationSpec = spotSpec,
            label = "spotBottom"
        )

        // Scrim canvas with smooth spring-animated spotlight cutout over the active tool card
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    // Tapping outside advances or closes on last step
                    if (safeIndex < steps.lastIndex) {
                        onStepChange(safeIndex + 1)
                    } else {
                        onDismiss()
                    }
                }
        ) {
            drawRect(color = Color.Black.copy(alpha = 0.74f))

            if (hasSpot) {
                val left = animLeft.coerceIn(0f, size.width)
                val top = animTop.coerceIn(0f, size.height)
                val right = animRight.coerceIn(left + 1f, size.width)
                val bottom = animBottom.coerceIn(top + 1f, size.height)
                val spotWidth = (right - left).coerceAtLeast(1f)
                val spotHeight = (bottom - top).coerceAtLeast(1f)

                // Punch out clear hole for the highlighted tool card
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = Offset(left, top),
                    size = Size(spotWidth, spotHeight),
                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                    blendMode = BlendMode.Clear
                )

                // Animated pulsing outer halo
                val extraPulse = (pulseRadius - 1f) * 80f
                drawRoundRect(
                    color = animatedAccent.copy(alpha = 0.55f),
                    topLeft = Offset(left - extraPulse, top - extraPulse),
                    size = Size(spotWidth + extraPulse * 2, spotHeight + extraPulse * 2),
                    cornerRadius = CornerRadius(cornerRadiusPx + extraPulse, cornerRadiusPx + extraPulse),
                    style = Stroke(width = 3.dp.toPx())
                )

                // Crisp inner highlight border
                drawRoundRect(
                    color = animatedAccent,
                    topLeft = Offset(left, top),
                    size = Size(spotWidth, spotHeight),
                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                    style = Stroke(width = 2.5.dp.toPx())
                )
            }
        }

        // Determine whether the tooltip card should dock near top or bottom so it never covers the spotlighted card
        val placeCardAtTop = remember(targetBoundsInParent, containerHeightPx) {
            val rect = targetBoundsInParent
            if (rect == null || rect.isEmpty) {
                false
            } else {
                rect.center.y > containerHeightPx * 0.52f
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            contentAlignment = if (placeCardAtTop) Alignment.TopCenter else Alignment.BottomCenter
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                border = BorderStroke(1.5.dp, step.accentColor.copy(alpha = 0.65f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* Consume clicks inside tooltip card */ }
            ) {
                AnimatedContent(
                    targetState = safeIndex,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(150))
                    },
                    label = "coachmarkStepTransition"
                ) { idx ->
                    val currentStep = steps[idx]
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        // Top Row: Badge + Step Dots + Close Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = currentStep.accentColor.copy(alpha = 0.14f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = currentStep.accentColor,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = stringResource(currentStep.badgeRes),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = currentStep.accentColor
                                    )
                                }
                            }

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("coachmark_close_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.coachmark_skip_tour),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Tool Icon + Title
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = currentStep.accentColor.copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, currentStep.accentColor.copy(alpha = 0.4f)),
                                modifier = Modifier.size(46.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = currentStep.icon,
                                        contentDescription = null,
                                        tint = currentStep.accentColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(currentStep.titleRes),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Text(
                                    text = stringResource(currentStep.summaryRes),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 16.sp
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // How-to Steps Box
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                currentStep.howToStepResIds.forEachIndexed { stepNum, instructionRes ->
                                    Row(
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = currentStep.accentColor,
                                            modifier = Modifier
                                                .padding(top = 1.dp)
                                                .size(18.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "${stepNum + 1}",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(instructionRes),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 12.sp,
                                                lineHeight = 16.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Step Indicator Pills + Try Tool CTA
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                steps.forEachIndexed { dotIdx, dotStep ->
                                    val isCurrent = dotIdx == idx
                                    val pillWidth by animateDpAsState(
                                        targetValue = if (isCurrent) 24.dp else 8.dp,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        ),
                                        label = "coachmarkDot_$dotIdx"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .height(8.dp)
                                            .width(pillWidth)
                                            .clip(CircleShape)
                                            .background(
                                                if (isCurrent) dotStep.accentColor
                                                else MaterialTheme.colorScheme.outlineVariant
                                            )
                                            .clickable { onStepChange(dotIdx) }
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = {
                                    onTryTool(currentStep.targetOperation)
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = currentStep.accentColor.copy(alpha = 0.15f),
                                    contentColor = currentStep.accentColor
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("coachmark_try_tool_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(currentStep.tryButtonLabelRes),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Bottom Navigation Row: Skip / Back / Next or Finish
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier.testTag("coachmark_skip_button")
                            ) {
                                Text(
                                    text = stringResource(R.string.coachmark_skip_tour),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (idx > 0) {
                                    OutlinedButton(
                                        onClick = { onStepChange(idx - 1) },
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                        modifier = Modifier.testTag("coachmark_prev_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = stringResource(R.string.preview_action_previous),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(R.string.action_back),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        if (idx < steps.lastIndex) {
                                            onStepChange(idx + 1)
                                        } else {
                                            onDismiss()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = currentStep.accentColor,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    modifier = Modifier.testTag("coachmark_next_button")
                                ) {
                                    Text(
                                        text = if (idx < steps.lastIndex) stringResource(R.string.coachmark_next_tool) else stringResource(R.string.coachmark_got_it),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = if (idx < steps.lastIndex) Icons.AutoMirrored.Filled.ArrowForward else Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
