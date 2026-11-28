package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.JetBrainsMonoFamily
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onFinishOnboarding: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val pages = remember {
        listOf(
            OnboardingPageData(
                icon = Icons.Default.Lock,
                titleRes = R.string.onboarding_title_1,
                descRes = R.string.onboarding_desc_1,
                accentColor = Color(0xFF10B981),
                badgeRes = R.string.onboarding_badge_1
            ),
            OnboardingPageData(
                icon = Icons.Default.Speed,
                titleRes = R.string.onboarding_title_4,
                descRes = R.string.onboarding_desc_4,
                accentColor = Color(0xFF38BDF8),
                badgeRes = R.string.onboarding_badge_2
            ),
            OnboardingPageData(
                icon = Icons.Default.FolderSpecial,
                titleRes = R.string.onboarding_title_2,
                descRes = R.string.onboarding_desc_2,
                accentColor = Color(0xFFA78BFA),
                badgeRes = R.string.onboarding_badge_3
            ),
            OnboardingPageData(
                icon = Icons.Default.CheckCircle,
                titleRes = R.string.onboarding_title_3,
                descRes = R.string.onboarding_desc_3,
                accentColor = Color(0xFF10B981),
                badgeRes = R.string.onboarding_badge_4
            )
        )
    }

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val activeAccent by animateColorAsState(
        targetValue = pages[pagerState.currentPage].accentColor,
        animationSpec = tween(320),
        label = "onboardingAccent"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            activeAccent.copy(alpha = 0.16f),
                            activeAccent.copy(alpha = 0.04f),
                            Color.Transparent
                        ),
                        center = Offset(size.width / 2f, size.height * 0.36f),
                        radius = size.minDimension * 0.72f
                    ),
                    radius = size.minDimension * 0.72f,
                    center = Offset(size.width / 2f, size.height * 0.36f)
                )
            }
            .safeDrawingPadding()
    ) {
        val isWideScreen = maxWidth > 600.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .align(Alignment.Center)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar (Brand Emblem & Skip Action)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_pdfcraft_emblem_1795782138112),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.ExtraBold
                        )
                    )
                }

                if (pagerState.currentPage < pages.size - 1) {
                    TextButton(
                        onClick = onFinishOnboarding,
                        modifier = Modifier.testTag("onboarding_skip_button")
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_skip),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(48.dp))
                }
            }

            // Horizontal Pager with smooth parallax scale & fade transitions
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { pageIdx ->
                val page = pages[pageIdx]
                val scrollState = rememberScrollState()
                val pageOffset = ((pagerState.currentPage - pageIdx) + pagerState.currentPageOffsetFraction).absoluteValue
                val scale = (1f - (pageOffset * 0.14f)).coerceIn(0.84f, 1f)
                val alpha = (1f - (pageOffset * 0.45f)).coerceIn(0.45f, 1f)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(vertical = 16.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Ambient ringed studio icon badge
                    Surface(
                        shape = RoundedCornerShape(32.dp),
                        color = page.accentColor.copy(alpha = 0.14f),
                        border = BorderStroke(1.5.dp, page.accentColor.copy(alpha = 0.55f)),
                        shadowElevation = 8.dp,
                        modifier = Modifier.size(if (isWideScreen) 136.dp else 116.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = page.icon,
                                contentDescription = null,
                                tint = page.accentColor,
                                modifier = Modifier.size(if (isWideScreen) 62.dp else 52.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = page.accentColor.copy(alpha = 0.14f),
                        border = BorderStroke(0.5.dp, page.accentColor.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = stringResource(page.badgeRes),
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = page.accentColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = stringResource(page.titleRes),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.ExtraBold
                        ),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(page.descRes),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 22.sp
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            // Bottom Controls (Animated Pill Indicators & Primary CTA)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    pages.indices.forEach { index ->
                        val isActive = index == pagerState.currentPage
                        val dotWidth by animateDpAsState(
                            targetValue = if (isActive) 28.dp else 8.dp,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "onboardingDot_$index"
                        )
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .height(8.dp)
                                .width(dotWidth)
                                .clip(CircleShape)
                                .background(
                                    if (isActive) activeAccent
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                        )
                    }
                }

                Button(
                    onClick = {
                        coroutineScope.launch {
                            if (pagerState.currentPage < pages.size - 1) {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            } else {
                                onFinishOnboarding()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("onboarding_primary_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = activeAccent),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    AnimatedContent(
                        targetState = pagerState.currentPage == pages.size - 1,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                        label = "onboardingCtaLabel"
                    ) { isLast ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isLast) {
                                    stringResource(R.string.onboarding_get_started)
                                } else {
                                    stringResource(R.string.onboarding_next)
                                },
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class OnboardingPageData(
    val icon: ImageVector,
    val titleRes: Int,
    val descRes: Int,
    val accentColor: Color = EmeraldPrimary,
    @param:androidx.annotation.StringRes val badgeRes: Int
)
