package com.example.ui.components

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import com.example.R
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.pdf.PdfEngine
import com.example.ui.theme.CharcoalBackground
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CharcoalCardBorder
import com.example.ui.theme.CharcoalHighlight
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults

@Composable
fun PdfViewerDialog(
    file: File,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var totalPages by remember { mutableIntStateOf(0) }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }

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

    LaunchedEffect(file, currentPageIndex) {
        isLoading = true
        zoomScale = 1f
        panOffsetX = 0f
        panOffsetY = 0f
        withContext(Dispatchers.IO) {
            try {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = android.graphics.pdf.PdfRenderer(pfd)
                totalPages = renderer.pageCount

                if (currentPageIndex in 0 until totalPages) {
                    val page = renderer.openPage(currentPageIndex)
                    val density = context.resources.displayMetrics.density
                    val width = (320 * density).toInt().coerceAtLeast(100)
                    val pw = if (page.width > 0) page.width else 100
                    val ph = if (page.height > 0) page.height else 100
                    val pageAspect = (ph.toFloat() / pw.toFloat()).takeIf { !it.isNaN() && !it.isInfinite() } ?: 1.414f
                    val height = (width * pageAspect).toInt().coerceIn(100, 2000)

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
                    border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalCardBorder),
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
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                                )
                                Text(
                                    text = "${PdfEngine.formatFileSize(file.length())} • ${totalPages} pages",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { isSearchActive = !isSearchActive },
                                    modifier = Modifier.testTag("toggle_viewer_search")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search Text",
                                        tint = if (isSearchActive) EmeraldPrimary else TextPrimary
                                    )
                                }

                                IconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.testTag("close_viewer_button")
                                ) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextPrimary)
                                }
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
                                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(14.dp))
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
                                                text = "Match ${currentMatchIndex + 1} of ${searchResults.size} (Page ${currentMatch?.pageNumber})",
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
                                                    Icon(Icons.Default.ChevronLeft, contentDescription = "Prev Match", tint = if (currentMatchIndex > 0) EmeraldPrimary else TextMuted.copy(alpha = 0.3f))
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
                                                    Icon(Icons.Default.ChevronRight, contentDescription = "Next Match", tint = if (currentMatchIndex < searchResults.size - 1) EmeraldPrimary else TextMuted.copy(alpha = 0.3f))
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
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalCardBorder),
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
                                    contentDescription = "Page ${currentPageIndex + 1}",
                                    contentScale = ContentScale.Fit,
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

                // Page Switcher & Zoom Navigation Bar
                Surface(
                    color = CharcoalCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
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
                                    contentDescription = "Previous Page",
                                    tint = if (currentPageIndex > 0) EmeraldPrimary else TextMuted.copy(alpha = 0.3f)
                                )
                            }

                            Text(
                                text = "Page ${currentPageIndex + 1} of $totalPages",
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
                                    contentDescription = "Next Page",
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
                                border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalCardBorder),
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
                    }
                }
            }
        }
    }
}
