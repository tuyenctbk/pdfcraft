package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.speech.tts.TextToSpeech
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.local.PdfOperationType
import com.example.data.pdf.PdfEngine
import com.example.ui.screens.PdfPreviewOperationMode
import com.example.ui.screens.PdfPreviewScreenDialog
import com.example.ui.theme.CharcoalBackground
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CharcoalCardBorder
import com.example.ui.theme.CharcoalHighlight
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ViewerReadingMode {
    DAY,
    NIGHT,
    SEPIA
}

@Composable
fun PdfViewerDialog(
    file: File,
    onDismiss: () -> Unit,
    onSendToTool: ((PdfOperationType) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var totalPages by remember { mutableIntStateOf(0) }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Reading eye-comfort mode
    var readingMode by remember { mutableStateOf(ViewerReadingMode.DAY) }

    // Zoom & Pan state
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }

    // Search state
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<PdfEngine.PdfSearchResult>>(emptyList()) }
    var currentMatchIndex by remember { mutableIntStateOf(0) }
    var isSearching by remember { mutableStateOf(false) }

    // Offline Text-To-Speech (Read Aloud) state
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var showThumbnailGridDialog by remember { mutableStateOf(false) }
    var activeFile by remember(file) { mutableStateOf(file) }

    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.getDefault()
                isTtsReady = true
            }
        }
        ttsEngine = engine
        onDispose {
            try {
                engine?.stop()
                engine?.shutdown()
            } catch (_: Throwable) {}
        }
    }

    LaunchedEffect(file, currentPageIndex) {
        isLoading = true
        zoomScale = 1f
        panOffsetX = 0f
        panOffsetY = 0f
        if (isSpeaking) {
            ttsEngine?.stop()
            isSpeaking = false
        }
        withContext(Dispatchers.IO) {
            try {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = android.graphics.pdf.PdfRenderer(pfd)
                totalPages = renderer.pageCount

                if (currentPageIndex in 0 until totalPages) {
                    val page = renderer.openPage(currentPageIndex)
                    val density = context.resources.displayMetrics.density
                    val width = (360 * density).toInt().coerceAtLeast(100)
                    val pw = if (page.width > 0) page.width else 100
                    val ph = if (page.height > 0) page.height else 100
                    val pageAspect = (ph.toFloat() / pw.toFloat()).takeIf { !it.isNaN() && !it.isInfinite() } ?: 1.414f
                    val height = (width * pageAspect).toInt().coerceIn(100, 2400)

                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bmp)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    currentBitmap = bmp
                }
                renderer.close()
                pfd.close()
            } catch (t: Throwable) {
                android.util.Log.e("PdfViewerDialog", "Error rendering page $currentPageIndex: ${t.localizedMessage}")
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            searchResults = emptyList()
            currentMatchIndex = 0
            isSearching = false
        } else {
            isSearching = true
            val results = PdfEngine.searchTextInPdf(file, searchQuery)
            searchResults = results
            currentMatchIndex = 0
            isSearching = false
            if (results.isNotEmpty()) {
                currentPageIndex = results[0].pageIndex
            }
        }
    }

    val pageColorFilter: ColorFilter? = remember(readingMode) {
        when (readingMode) {
            ViewerReadingMode.DAY -> null
            ViewerReadingMode.NIGHT -> ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        -0.88f, 0f, 0f, 0f, 245f,
                        0f, -0.88f, 0f, 0f, 245f,
                        0f, 0f, -0.85f, 0f, 248f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
            ViewerReadingMode.SEPIA -> ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        0.96f, 0.04f, 0f, 0f, 8f,
                        0.02f, 0.90f, 0.02f, 0f, 4f,
                        0f, 0.04f, 0.76f, 0f, -12f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = CharcoalBackground,
            modifier = Modifier.fillMaxSize().testTag("pdf_viewer_dialog")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Surface(
                    color = CharcoalCard,
                    border = BorderStroke(1.dp, CharcoalCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp).fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${PdfEngine.formatFileSize(file.length())} • ${stringResource(R.string.viewer_page_of_total, currentPageIndex + 1, totalPages.coerceAtLeast(1))}",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { showThumbnailGridDialog = true },
                                    modifier = Modifier.testTag("viewer_thumbnail_grid_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GridView,
                                        contentDescription = stringResource(R.string.action_preview),
                                        tint = EmeraldPrimary
                                    )
                                }

                                IconButton(
                                    onClick = { isSearchActive = !isSearchActive },
                                    modifier = Modifier.testTag("toggle_viewer_search")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = stringResource(R.string.preview_search_text_hint),
                                        tint = if (isSearchActive) EmeraldPrimary else TextPrimary
                                    )
                                }

                                IconButton(
                                    onClick = { printPdfFile(context, file) },
                                    modifier = Modifier.testTag("viewer_print_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Print,
                                        contentDescription = stringResource(R.string.viewer_print_pdf),
                                        tint = TextPrimary
                                    )
                                }

                                IconButton(
                                    onClick = { sharePdfFile(context, file) },
                                    modifier = Modifier.testTag("viewer_share_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = stringResource(R.string.viewer_share_pdf),
                                        tint = TextPrimary
                                    )
                                }

                                IconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.testTag("close_viewer_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.action_close),
                                        tint = TextPrimary
                                    )
                                }
                            }
                        }

                        // Reading Mode & Offline Voice Read Aloud Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = readingMode == ViewerReadingMode.DAY,
                                onClick = { readingMode = ViewerReadingMode.DAY },
                                label = { Text(stringResource(R.string.viewer_mode_day), fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.LightMode, contentDescription = null, modifier = Modifier.size(14.dp))
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldPrimary.copy(alpha = 0.2f),
                                    selectedLabelColor = EmeraldPrimary
                                )
                            )
                            FilterChip(
                                selected = readingMode == ViewerReadingMode.NIGHT,
                                onClick = { readingMode = ViewerReadingMode.NIGHT },
                                label = { Text(stringResource(R.string.viewer_mode_night), fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.DarkMode, contentDescription = null, modifier = Modifier.size(14.dp))
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldPrimary.copy(alpha = 0.2f),
                                    selectedLabelColor = EmeraldPrimary
                                )
                            )
                            FilterChip(
                                selected = readingMode == ViewerReadingMode.SEPIA,
                                onClick = { readingMode = ViewerReadingMode.SEPIA },
                                label = { Text(stringResource(R.string.viewer_mode_sepia), fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(14.dp))
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldPrimary.copy(alpha = 0.2f),
                                    selectedLabelColor = EmeraldPrimary
                                )
                            )

                            if (isTtsReady) {
                                FilterChip(
                                    selected = isSpeaking,
                                    onClick = {
                                        if (isSpeaking) {
                                            ttsEngine?.stop()
                                            isSpeaking = false
                                        } else {
                                            coroutineScope.launch {
                                                val pageText = extractSinglePageText(file, currentPageIndex + 1)
                                                if (pageText.isNotBlank()) {
                                                    isSpeaking = true
                                                    ttsEngine?.speak(
                                                        pageText.take(3800),
                                                        TextToSpeech.QUEUE_FLUSH,
                                                        null,
                                                        "pdf_page_${currentPageIndex}"
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = if (isSpeaking) stringResource(R.string.viewer_stop_reading)
                                            else stringResource(R.string.viewer_read_aloud),
                                            fontSize = 11.sp
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isSpeaking) Icons.AutoMirrored.Filled.VolumeOff
                                            else Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = EmeraldPrimary.copy(alpha = 0.25f),
                                        selectedLabelColor = EmeraldPrimary
                                    )
                                )
                            }
                        }

                        // Expandable Search Bar
                        AnimatedVisibility(visible = isSearchActive) {
                            Column(modifier = Modifier.padding(top = 10.dp)) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text(stringResource(R.string.preview_search_text_hint), fontSize = 12.sp, color = TextMuted) },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(18.dp)) },
                                    trailingIcon = if (searchQuery.isNotEmpty()) {
                                        {
                                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_clear), tint = TextMuted, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    } else null,
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = EmeraldPrimary,
                                        unfocusedBorderColor = CharcoalCardBorder,
                                        focusedContainerColor = CharcoalBackground,
                                        unfocusedContainerColor = CharcoalBackground,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    ),
                                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("pdf_search_input")
                                )

                                if (isSearching) {
                                    Text(stringResource(R.string.preview_searching), fontSize = 11.sp, color = EmeraldPrimary, modifier = Modifier.padding(top = 4.dp))
                                } else if (searchQuery.isNotBlank()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (searchResults.isEmpty()) {
                                            Text(stringResource(R.string.preview_no_matches, searchQuery), fontSize = 11.sp, color = TextMuted)
                                        } else {
                                            val currentMatch = searchResults.getOrNull(currentMatchIndex)
                                            Text(
                                                text = stringResource(
                                                    R.string.viewer_match_of_total,
                                                    currentMatchIndex + 1,
                                                    searchResults.size,
                                                    currentMatch?.pageNumber ?: 1
                                                ),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = EmeraldPrimary
                                            )

                                            Row {
                                                IconButton(
                                                    onClick = {
                                                        if (currentMatchIndex > 0) {
                                                            currentMatchIndex--
                                                            currentPageIndex = searchResults[currentMatchIndex].pageIndex
                                                        }
                                                    },
                                                    enabled = currentMatchIndex > 0,
                                                    modifier = Modifier.size(28.dp).testTag("prev_search_match")
                                                ) {
                                                    Icon(Icons.Default.ChevronLeft, contentDescription = stringResource(R.string.preview_action_previous), tint = if (currentMatchIndex > 0) EmeraldPrimary else TextMuted.copy(alpha = 0.3f))
                                                }

                                                IconButton(
                                                    onClick = {
                                                        if (currentMatchIndex < searchResults.size - 1) {
                                                            currentMatchIndex++
                                                            currentPageIndex = searchResults[currentMatchIndex].pageIndex
                                                        }
                                                    },
                                                    enabled = currentMatchIndex < searchResults.size - 1,
                                                    modifier = Modifier.size(28.dp).testTag("next_search_match")
                                                ) {
                                                    Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.preview_action_next), tint = if (currentMatchIndex < searchResults.size - 1) EmeraldPrimary else TextMuted.copy(alpha = 0.3f))
                                                }
                                            }
                                        }
                                    }

                                    val currentSnippet = searchResults.getOrNull(currentMatchIndex)?.snippet
                                    if (currentSnippet != null) {
                                        Text(
                                            text = currentSnippet,
                                            fontSize = 10.sp,
                                            color = TextMuted,
                                            maxLines = 2,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Page View Canvas with Gestures (Pinch to Zoom, Pan, Double-Tap)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds()
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = EmeraldPrimary)
                    } else if (currentBitmap != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clipToBounds()
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val newScale = (zoomScale * zoom).coerceIn(1f, 4.5f)
                                        zoomScale = newScale
                                        if (newScale > 1.01f) {
                                            val maxPan = 600f * (newScale - 1f)
                                            panOffsetX = (panOffsetX + pan.x).coerceIn(-maxPan, maxPan)
                                            panOffsetY = (panOffsetY + pan.y).coerceIn(-maxPan, maxPan)
                                        } else {
                                            panOffsetX = 0f
                                            panOffsetY = 0f
                                        }
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            if (zoomScale > 1.2f) {
                                                zoomScale = 1f
                                                panOffsetX = 0f
                                                panOffsetY = 0f
                                            } else {
                                                zoomScale = 2.5f
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = when (readingMode) {
                                        ViewerReadingMode.DAY -> Color.White
                                        ViewerReadingMode.NIGHT -> Color(0xFF14181F)
                                        ViewerReadingMode.SEPIA -> Color(0xFFF6EFE2)
                                    }
                                ),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, CharcoalCardBorder),
                                modifier = Modifier
                                    .graphicsLayer {
                                        scaleX = zoomScale
                                        scaleY = zoomScale
                                        translationX = panOffsetX
                                        translationY = panOffsetY
                                    }
                            ) {
                                Image(
                                    bitmap = currentBitmap!!.asImageBitmap(),
                                    contentDescription = stringResource(R.string.viewer_page_of_total, currentPageIndex + 1, totalPages.coerceAtLeast(1)),
                                    contentScale = ContentScale.Fit,
                                    colorFilter = pageColorFilter,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }

                        // Floating Zoom Indicator Badge (shown when zoomed)
                        if (zoomScale > 1.05f) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.75f),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.preview_double_tap_to_reset, (zoomScale * 100).toInt()),
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    } else {
                        Text(stringResource(R.string.preview_could_not_render), color = TextMuted)
                    }
                }

                // Bottom Controls: Page Scrubber, Page Switcher, Zoom Bar, and Send-to-Tool Shortcuts
                Surface(
                    color = CharcoalCard,
                    border = BorderStroke(1.dp, CharcoalCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        // Interactive Page Scrubber Slider for multi-page PDFs
                        if (totalPages > 1) {
                            Slider(
                                value = currentPageIndex.toFloat(),
                                onValueChange = { newIdx ->
                                    val target = newIdx.toInt().coerceIn(0, totalPages - 1)
                                    if (target != currentPageIndex) {
                                        currentPageIndex = target
                                    }
                                },
                                valueRange = 0f..(totalPages - 1).coerceAtLeast(1).toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = EmeraldPrimary,
                                    activeTrackColor = EmeraldPrimary,
                                    inactiveTrackColor = CharcoalCardBorder
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(24.dp)
                                    .testTag("viewer_page_slider")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { if (currentPageIndex > 0) currentPageIndex-- },
                                enabled = currentPageIndex > 0,
                                modifier = Modifier.testTag("viewer_prev_page")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronLeft,
                                    contentDescription = stringResource(R.string.preview_action_previous),
                                    tint = if (currentPageIndex > 0) EmeraldPrimary else TextMuted.copy(alpha = 0.3f)
                                )
                            }

                            Text(
                                text = stringResource(R.string.viewer_page_of_total, currentPageIndex + 1, totalPages.coerceAtLeast(1)),
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 14.sp
                            )

                            IconButton(
                                onClick = { if (currentPageIndex < totalPages - 1) currentPageIndex++ },
                                enabled = currentPageIndex < totalPages - 1,
                                modifier = Modifier.testTag("viewer_next_page")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = stringResource(R.string.preview_action_next),
                                    tint = if (currentPageIndex < totalPages - 1) EmeraldPrimary else TextMuted.copy(alpha = 0.3f)
                                )
                            }
                        }

                        // Zoom Controls Toolbar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    zoomScale = (zoomScale - 0.35f).coerceAtLeast(1f)
                                    if (zoomScale <= 1f) {
                                        panOffsetX = 0f
                                        panOffsetY = 0f
                                    }
                                },
                                enabled = zoomScale > 1f,
                                modifier = Modifier.size(36.dp).testTag("viewer_zoom_out")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ZoomOut,
                                    contentDescription = stringResource(R.string.preview_zoom_out),
                                    tint = if (zoomScale > 1f) EmeraldPrimary else TextMuted.copy(alpha = 0.3f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = CharcoalHighlight,
                                border = BorderStroke(1.dp, CharcoalCardBorder),
                                onClick = {
                                    zoomScale = 1f
                                    panOffsetX = 0f
                                    panOffsetY = 0f
                                },
                                modifier = Modifier.testTag("viewer_reset_zoom")
                            ) {
                                Text(
                                    text = "${(zoomScale * 100).toInt()}%",
                                    color = if (zoomScale > 1.05f) EmeraldPrimary else TextMuted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = {
                                    zoomScale = (zoomScale + 0.5f).coerceAtMost(4.5f)
                                },
                                enabled = zoomScale < 4.5f,
                                modifier = Modifier.size(36.dp).testTag("viewer_zoom_in")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ZoomIn,
                                    contentDescription = stringResource(R.string.preview_zoom_in),
                                    tint = if (zoomScale < 4.5f) EmeraldPrimary else TextMuted.copy(alpha = 0.3f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            if (zoomScale > 1.05f || panOffsetX != 0f || panOffsetY != 0f) {
                                Spacer(modifier = Modifier.width(10.dp))
                                IconButton(
                                    onClick = {
                                        zoomScale = 1f
                                        panOffsetX = 0f
                                        panOffsetY = 0f
                                    },
                                    modifier = Modifier.size(36.dp).testTag("viewer_reset_all")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RestartAlt,
                                        contentDescription = stringResource(R.string.preview_reset_zoom),
                                        tint = EmeraldPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Direct "Edit This PDF" Tool Shortcuts
                        if (onSendToTool != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AssistChip(
                                    onClick = { onSendToTool(PdfOperationType.SIGN) },
                                    label = { Text(stringResource(R.string.viewer_send_to_sign), fontSize = 11.sp, color = TextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.Draw, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(14.dp)) },
                                    border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = CharcoalCardBorder)
                                )
                                AssistChip(
                                    onClick = { onSendToTool(PdfOperationType.COMPRESS) },
                                    label = { Text(stringResource(R.string.viewer_send_to_compress), fontSize = 11.sp, color = TextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.Compress, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(14.dp)) },
                                    border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = CharcoalCardBorder)
                                )
                                AssistChip(
                                    onClick = { onSendToTool(PdfOperationType.ROTATE) },
                                    label = { Text(stringResource(R.string.viewer_send_to_rotate), fontSize = 11.sp, color = TextPrimary) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(14.dp)) },
                                    border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = CharcoalCardBorder)
                                )
                                AssistChip(
                                    onClick = { onSendToTool(PdfOperationType.PDF_TO_TEXT) },
                                    label = { Text(stringResource(R.string.viewer_send_to_text), fontSize = 11.sp, color = TextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.Description, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(14.dp)) },
                                    border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = CharcoalCardBorder)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showThumbnailGridDialog) {
        PdfPreviewScreenDialog(
            file = activeFile,
            documentTitle = activeFile.name,
            operationMode = PdfPreviewOperationMode.GENERAL,
            initialPageIndex = currentPageIndex,
            initialGridMode = true,
            onPageSelected = { selectedPage ->
                currentPageIndex = selectedPage
            },
            onModifiedPdfSaved = { savedFile ->
                activeFile = savedFile
                currentPageIndex = 0
                showThumbnailGridDialog = false
            },
            onDismissRequest = { showThumbnailGridDialog = false }
        )
    }
}

private suspend fun extractSinglePageText(file: File, pageNumber1Based: Int): String = withContext(Dispatchers.IO) {
    try {
        PDDocument.load(file).use { doc ->
            val stripper = PDFTextStripper().apply {
                startPage = pageNumber1Based
                endPage = pageNumber1Based
            }
            stripper.getText(doc).trim()
        }
    } catch (_: Throwable) {
        ""
    }
}

private fun sharePdfFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, file.name).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (t: Throwable) {
        android.util.Log.e("PdfViewerDialog", "Share error: ${t.localizedMessage}")
    }
}

private fun printPdfFile(context: Context, file: File) {
    try {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val jobName = "PDFCraft_${file.nameWithoutExtension}"
        printManager.print(
            jobName,
            object : PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes?,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback?,
                    extras: Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback?.onLayoutCancelled()
                        return
                    }
                    val info = PrintDocumentInfo.Builder(file.name)
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                        .build()
                    callback?.onLayoutFinished(info, true)
                }

                override fun onWrite(
                    pages: Array<out PageRange>?,
                    destination: ParcelFileDescriptor?,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback?
                ) {
                    if (destination == null) {
                        callback?.onWriteFailed("No destination")
                        return
                    }
                    try {
                        FileInputStream(file).use { input ->
                            FileOutputStream(destination.fileDescriptor).use { output ->
                                input.copyTo(output)
                            }
                        }
                        callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        callback?.onWriteFailed(e.localizedMessage)
                    }
                }
            },
            null
        )
    } catch (t: Throwable) {
        android.util.Log.e("PdfViewerDialog", "Print error: ${t.localizedMessage}")
    }
}
