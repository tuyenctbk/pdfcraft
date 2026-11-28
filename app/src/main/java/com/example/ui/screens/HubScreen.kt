package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.PdfOperationType
import com.example.data.pdf.PdfEngine
import com.example.ui.MainViewModel
import com.example.ui.components.DEFAULT_COACHMARK_STEPS
import com.example.ui.components.ToolCoachmarkOverlay
import com.example.ui.components.bounceClickable
import com.example.ui.components.staggeredEntrance
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalIsDarkTheme

data class HubActionItem(
    val titleRes: Int,
    val subtitleRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val badgeRes: Int? = null,
    val op: PdfOperationType,
    val accentColor: Color = Color(0xFF10B981)
)

@Composable
fun HubScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val history by viewModel.historyState.collectAsStateWithLifecycle()
    val recentFiles by viewModel.recentFilesState.collectAsStateWithLifecycle()
    val showDonationBanner by viewModel.showHomeDonationBanner.collectAsStateWithLifecycle()
    val isSupporter by viewModel.isSupporter.collectAsStateWithLifecycle()
    val showToolCoachmark by viewModel.showToolCoachmark.collectAsStateWithLifecycle()

    val isDark = LocalIsDarkTheme.current
    val listState = rememberLazyListState()
    var coachmarkStepIndex by remember { mutableIntStateOf(0) }
    var rootLayoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val toolCardBounds = remember { mutableStateMapOf<PdfOperationType, Rect>() }

    val openPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.openUriInViewer(it) }
    }

    val totalSavedBytes = remember(history) {
        history.sumOf { (it.originalSizeBytes - it.resultSizeBytes).coerceAtLeast(0) }
    }
    val animatedProcessedCount by animateIntAsState(
        targetValue = history.size,
        animationSpec = tween(durationMillis = 480),
        label = "processed_files_count"
    )

    val quickActionItems = remember {
        listOf(
            HubActionItem(R.string.hub_action_merge_title, R.string.hub_action_merge_desc, Icons.Default.Merge, R.string.hub_badge_popular, PdfOperationType.MERGE, Color(0xFF10B981)),
            HubActionItem(R.string.hub_action_compress_title, R.string.hub_action_compress_desc, Icons.Default.Compress, R.string.hub_badge_popular, PdfOperationType.COMPRESS, Color(0xFF34D399)),
            HubActionItem(R.string.hub_action_text_to_pdf_title, R.string.hub_action_text_to_pdf_desc, Icons.Default.TextFields, R.string.hub_badge_new, PdfOperationType.TEXT_TO_PDF, Color(0xFF38BDF8)),
            HubActionItem(R.string.hub_action_pdf_to_images_title, R.string.hub_action_pdf_to_images_desc, Icons.Default.PhotoLibrary, R.string.hub_badge_new, PdfOperationType.PDF_TO_IMAGES, Color(0xFFEC4899)),
            HubActionItem(R.string.hub_action_images_to_pdf_title, R.string.hub_action_images_to_pdf_desc, Icons.Default.AddPhotoAlternate, null, PdfOperationType.IMAGE_TO_PDF, Color(0xFFA78BFA)),
            HubActionItem(R.string.hub_action_page_numbers_title, R.string.hub_action_page_numbers_desc, Icons.Default.FormatListNumbered, null, PdfOperationType.PAGE_NUMBER, Color(0xFFF59E0B)),
            HubActionItem(R.string.hub_action_split_title, R.string.hub_action_split_desc, Icons.Default.ContentCut, null, PdfOperationType.SPLIT, Color(0xFF3B82F6)),
            HubActionItem(R.string.hub_action_rotate_title, R.string.hub_action_rotate_desc, Icons.AutoMirrored.Filled.RotateRight, null, PdfOperationType.ROTATE, Color(0xFF8B5CF6))
        )
    }

    LaunchedEffect(showToolCoachmark, coachmarkStepIndex) {
        if (showToolCoachmark) {
            val targetOp = DEFAULT_COACHMARK_STEPS.getOrNull(coachmarkStepIndex)?.targetOperation
            if (targetOp == PdfOperationType.SPLIT || targetOp == PdfOperationType.ROTATE) {
                listState.animateScrollToItem(index = 3)
            } else {
                listState.animateScrollToItem(index = 0)
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onGloballyPositioned { rootLayoutCoordinates = it }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Welcome Hero Banner with Studio Gradient Atmosphere
            item {
                val heroGradient = if (isDark) {
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF064E3B),
                            Color(0xFF0A2E24),
                            Color(0xFF101916)
                        )
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFD1FAE5),
                            Color(0xFFECFDF5),
                            Color(0xFFF0FDF4)
                        )
                    )
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.42f else 0.30f)),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 4.dp else 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .staggeredEntrance(0)
                        .clip(RoundedCornerShape(24.dp))
                        .background(heroGradient)
                        .testTag("hero_banner")
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.hub_hero_title),
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                                Text(
                                    text = stringResource(R.string.hub_hero_subtitle),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Live Stats Metric Pills with crisp Monospace numbers
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.65f else 0.85f),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = stringResource(R.string.hub_metric_processed),
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(R.string.hub_files_count, animatedProcessedCount),
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.65f else 0.85f),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = stringResource(R.string.hub_metric_saved),
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = PdfEngine.formatFileSize(totalSavedBytes),
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.65f else 0.85f),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = stringResource(R.string.hub_metric_privacy),
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(R.string.hub_privacy_value),
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = { openPdfLauncher.launch(arrayOf("application/pdf")) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("hub_open_pdf_reader_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.hub_open_pdf_reader_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            // Buy Me a Coffee Support Banner (Shown conditionally and adaptively only to satisfied users)
            if (showDonationBanner && !isSupporter) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, Color(0xFFD97706).copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("hub_coffee_banner")
                    ) {
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            val isNarrow = maxWidth < 340.dp

                            if (isNarrow) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { viewModel.openDonationDialog() }
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = Color(0xFFD97706).copy(alpha = 0.15f),
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Default.LocalCafe,
                                                        contentDescription = null,
                                                        tint = Color(0xFFD97706),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = stringResource(R.string.support_hub_compact_title),
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.dismissHomeDonationBanner() },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = stringResource(R.string.support_hub_dismiss),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = stringResource(R.string.support_hub_compact_subtitle),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    FilledTonalButton(
                                        onClick = { viewModel.openDonationDialog() },
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = Color(0xFFD97706).copy(alpha = 0.15f),
                                            contentColor = Color(0xFFD97706)
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Favorite,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.support_hub_btn_tip),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { viewModel.openDonationDialog() }
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = Color(0xFFD97706).copy(alpha = 0.15f),
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.LocalCafe,
                                                    contentDescription = null,
                                                    tint = Color(0xFFD97706),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stringResource(R.string.support_hub_compact_title),
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = stringResource(R.string.support_hub_compact_subtitle),
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontSize = 11.sp
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        FilledTonalButton(
                                            onClick = { viewModel.openDonationDialog() },
                                            colors = ButtonDefaults.filledTonalButtonColors(
                                                containerColor = Color(0xFFD97706).copy(alpha = 0.15f),
                                                contentColor = Color(0xFFD97706)
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Favorite,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = stringResource(R.string.support_hub_btn_tip),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        IconButton(
                                            onClick = { viewModel.dismissHomeDonationBanner() },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = stringResource(R.string.support_hub_dismiss),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
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

            // Quick Actions Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Text(
                            text = stringResource(R.string.hub_quick_actions),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                coachmarkStepIndex = 0
                                viewModel.startToolCoachmark()
                            },
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("hub_replay_tour_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                                contentDescription = stringResource(R.string.hub_feature_guide),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = { viewModel.selectTab(1) },
                        modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 32.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.hub_view_all_tools),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            // Adaptive Quick Action Grid
            val columnCount = when {
                maxWidth >= 720.dp -> 4
                maxWidth >= 440.dp -> 3
                else -> 2
            }

            itemsIndexed(quickActionItems.chunked(columnCount)) { rowIndex, chunk ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .staggeredEntrance(rowIndex + 1),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    chunk.forEach { item ->
                        QuickActionCard(
                            title = stringResource(item.titleRes),
                            subtitle = stringResource(item.subtitleRes),
                            icon = item.icon,
                            badge = item.badgeRes?.let { stringResource(it) },
                            accentColor = item.accentColor,
                            modifier = Modifier
                                .weight(1f)
                                .onGloballyPositioned { coordinates ->
                                    rootLayoutCoordinates?.let { root ->
                                        if (coordinates.isAttached && root.isAttached) {
                                            toolCardBounds[item.op] = root.localBoundingBoxOf(coordinates)
                                        }
                                    }
                                },
                            onClick = { viewModel.setActiveTool(item.op) }
                        )
                    }
                    val emptySlots = columnCount - chunk.size
                    for (i in 0 until emptySlots) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            // Recent Files Section
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = CircleShape,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.hub_recent_files),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (recentFiles.isNotEmpty()) {
                            TextButton(
                                onClick = { viewModel.clearRecentFiles() },
                                modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 32.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.hub_clear_recent),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    if (recentFiles.isEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().testTag("quick_access_empty")
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.hub_no_recent_title),
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                    Text(
                                        text = stringResource(R.string.hub_no_recent_desc),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            recentFiles.take(5).forEachIndexed { index, item ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .bounceClickable { viewModel.openInViewer(item.filePath) }
                                        .testTag("quick_access_item_$index")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.PictureAsPdf,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.fileName,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = if (item.pageCount > 0) {
                                                    "${item.fileSizeFormatted.ifBlank { "PDF" }} • ${stringResource(R.string.common_pages_count, item.pageCount)}"
                                                } else {
                                                    item.fileSizeFormatted.ifBlank { "PDF" }
                                                },
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontSize = 11.sp
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Row {
                                            IconButton(onClick = { viewModel.openDocumentInfoScreen(item.filePath) }) {
                                                Icon(
                                                    imageVector = Icons.Default.Info,
                                                    contentDescription = stringResource(R.string.tool_doc_info_title),
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            IconButton(onClick = { viewModel.openInViewer(item.filePath) }) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                                    contentDescription = stringResource(R.string.hub_open_viewer),
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
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
        }

        if (showToolCoachmark) {
            val currentTargetOp = DEFAULT_COACHMARK_STEPS.getOrNull(coachmarkStepIndex)?.targetOperation
            val targetRect = currentTargetOp?.let { toolCardBounds[it] }

            ToolCoachmarkOverlay(
                currentStepIndex = coachmarkStepIndex,
                targetBoundsInParent = targetRect,
                onStepChange = { coachmarkStepIndex = it },
                onTryTool = { op ->
                    viewModel.completeToolCoachmark()
                    viewModel.setActiveTool(op)
                },
                onDismiss = {
                    viewModel.completeToolCoachmark()
                }
            )
        }
    }
}

@Composable
fun QuickActionCard(
    title: String,
    subtitle: String = "",
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badge: String? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accentColor.copy(alpha = if (isDark) 0.28f else 0.22f)),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 2.dp else 1.dp),
        modifier = modifier
            .defaultMinSize(minHeight = 108.dp)
            .clip(RoundedCornerShape(18.dp))
            .bounceClickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(13.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = accentColor.copy(alpha = if (isDark) 0.16f else 0.12f),
                    border = BorderStroke(0.5.dp, accentColor.copy(alpha = 0.32f)),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (badge != null) {
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = accentColor.copy(alpha = if (isDark) 0.18f else 0.13f)
                    ) {
                        Text(
                            text = badge,
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
