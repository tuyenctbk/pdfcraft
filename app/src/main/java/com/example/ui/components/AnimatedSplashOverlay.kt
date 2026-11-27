package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.EmeraldPrimaryLight
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.PlusJakartaSansFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AnimatedSplashOverlay(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val logoScale = remember { Animatable(0.76f) }
    val logoAlpha = remember { Animatable(0f) }
    val titleAlpha = remember { Animatable(0f) }
    val titleOffsetY = remember { Animatable(24f) }
    val progressSweep = remember { Animatable(0f) }
    val exitAlpha = remember { Animatable(1f) }
    val exitScale = remember { Animatable(1f) }

    val pulseTransition = rememberInfiniteTransition(label = "splashGlowPulse")
    val glowAlpha by pulseTransition.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.42f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    LaunchedEffect(Unit) {
        launch {
            logoAlpha.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
        }
        launch {
            logoScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
        launch {
            delay(140)
            launch { titleAlpha.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }
            launch { titleOffsetY.animateTo(0f, spring(dampingRatio = 0.78f, stiffness = 360f)) }
        }
        launch {
            delay(120)
            progressSweep.animateTo(1f, tween(760, easing = FastOutSlowInEasing))
        }

        delay(980)

        // Smooth scale + fade dissolve into app content
        val fadeJob = launch { exitAlpha.animateTo(0f, tween(280, easing = LinearEasing)) }
        val scaleJob = launch { exitScale.animateTo(1.06f, tween(280, easing = FastOutSlowInEasing)) }
        fadeJob.join()
        scaleJob.join()
        onFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = exitAlpha.value
                scaleX = exitScale.value
                scaleY = exitScale.value
            }
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D1C15),
                        Color(0xFF09130F),
                        Color(0xFF09090B)
                    )
                )
            )
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            EmeraldPrimary.copy(alpha = glowAlpha),
                            EmeraldPrimary.copy(alpha = 0.06f),
                            Color.Transparent
                        ),
                        center = Offset(size.width / 2f, size.height * 0.43f),
                        radius = size.minDimension * 0.62f
                    ),
                    radius = size.minDimension * 0.62f,
                    center = Offset(size.width / 2f, size.height * 0.43f)
                )
            }
            .safeDrawingPadding()
            .testTag("animated_splash_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            // Studio Emblem inside crisp beveled squircle
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFF0B1A13),
                border = BorderStroke(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            EmeraldPrimaryLight.copy(alpha = 0.65f),
                            EmeraldPrimary.copy(alpha = 0.18f)
                        )
                    )
                ),
                shadowElevation = 20.dp,
                modifier = Modifier
                    .size(116.dp)
                    .scale(logoScale.value)
                    .alpha(logoAlpha.value)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_pdfcraft_emblem_1795782138112),
                    contentDescription = stringResource(R.string.cd_studio_logo),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(28.dp))
                )
            }

            Spacer(modifier = Modifier.height(26.dp))

            // Brand Name & STUDIO pill
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    alpha = titleAlpha.value
                    translationY = titleOffsetY.value
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        fontFamily = PlusJakartaSansFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 30.sp,
                        letterSpacing = (-0.5).sp,
                        color = Color(0xFFFAFAFA)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = EmeraldPrimary.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = stringResource(R.string.brand_studio_badge),
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 1.2.sp,
                            color = EmeraldPrimaryLight,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(EmeraldPrimary)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = stringResource(R.string.brand_splash_tagline),
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        letterSpacing = 1.1.sp,
                        color = Color(0xFFA7F3D0).copy(alpha = 0.85f)
                    )
                }
            }
        }

        // Bottom sleek loading progress hairline
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 44.dp)
                .width(132.dp)
                .height(3.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = progressSweep.value.coerceIn(0f, 1f)
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                    }
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(EmeraldPrimary, EmeraldPrimaryLight)
                        )
                    )
            )
        }
    }
}
