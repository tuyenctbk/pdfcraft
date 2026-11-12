package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.pdf.PdfEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Defines the operational context in which [PdfPreviewScreen] is used before executing a PDF operation.
 */
enum class PdfPreviewOperationMode {
    GENERAL,
    SPLIT,
    ROTATE
}

/**
 * Holds lightweight per-page dimension metadata read from [PdfRenderer.Page].
 */
data class PdfPageDimension(
    val pageIndex: Int,
    val widthPt: Int,
    val heightPt: Int
) {
    val aspectRatio: Float
        get() = if (widthPt > 0 && heightPt > 0) {
            (widthPt.toFloat() / heightPt.toFloat()).coerceIn(0.45f, 2.2f)
        } else {
            0.707f // Standard A4 portrait ratio
        }
}

/**
 * Thread-safe wrapper around Android's native [PdfRenderer] and [ParcelFileDescriptor].
 * Guarantees that only one [PdfRenderer.Page] is opened and rendered at a time via [mutex].
 */
class PdfRendererController(val file: File) {
    private val mutex = Mutex()
    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var isClosed = false

    private val thumbnailCache = object : LruCache<Int, Bitmap>(48) {}
    private val highResCache = object : LruCache<String, Bitmap>(8) {}
    private val pageDimensions = mutableMapOf<Int, PdfPageDimension>()

    var pageCount: Int = 0
        private set

    suspend fun initialize(): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (isClosed) return@withLock 0
            if (renderer == null) {
                val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val pdfRenderer = PdfRenderer(descriptor)
                pfd = descriptor
                renderer = pdfRenderer
                pageCount = pdfRenderer.pageCount
            }
            pageCount
        }
    }

    fun getCachedThumbnail(pageIndex: Int): Bitmap? = thumbnailCache.get(pageIndex)

    fun getCachedDimension(pageIndex: Int): PdfPageDimension? = pageDimensions[pageIndex]

    suspend fun renderPage(
        pageIndex: Int,
        targetWidthPx: Int,
        highRes: Boolean = false
    ): Pair<Bitmap?, PdfPageDimension?> = withContext(Dispatchers.IO) {
        val cacheKey = "$pageIndex@$targetWidthPx"
        if (highRes) {
            highResCache.get(cacheKey)?.let { cached ->
                return@withContext cached to pageDimensions[pageIndex]
            }
        } else {
            thumbnailCache.get(pageIndex)?.let { cached ->
                return@withContext cached to pageDimensions[pageIndex]
            }
        }

        mutex.withLock {
            val activeRenderer = renderer
            if (isClosed || activeRenderer == null || pageIndex !in 0 until pageCount) {
                return@withLock null to null
            }
            var page: PdfRenderer.Page? = null
            try {
                page = activeRenderer.openPage(pageIndex)
                val pw = page.width.coerceAtLeast(1)
                val ph = page.height.coerceAtLeast(1)
                val dim = PdfPageDimension(pageIndex = pageIndex, widthPt = pw, heightPt = ph)
                pageDimensions[pageIndex] = dim

                val widthPx = targetWidthPx.coerceIn(120, 2400)
                val heightPx = (widthPx * (ph.toFloat() / pw.toFloat())).toInt().coerceIn(120, 3200)

                val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                if (highRes) {
                    highResCache.put(cacheKey, bitmap)
                } else {
                    thumbnailCache.put(pageIndex, bitmap)
                }
                bitmap to dim
            } catch (_: Throwable) {
                null to pageDimensions[pageIndex]
            } finally {
                try {
                    page?.close()
                } catch (_: Throwable) {}
            }
        }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (isClosed) return@withLock
            isClosed = true
            try {
                renderer?.close()
            } catch (_: Throwable) {}
            try {
                pfd?.close()
            } catch (_: Throwable) {}
            renderer = null
            pfd = null
        }
    }
}

/**
 * Full-screen dialog wrapper around [PdfPreviewScreen] so any tool screen (`SplitScreen`, `RotateScreen`,
 * `PdfDocumentPreviewCard`, `PdfViewerDialog`, etc.) can open the native [PdfRenderer] thumbnail grid and
 * page inspector to quickly navigate pages and select specific pages for rotation or deletion.
 */
@Composable
fun PdfPreviewScreenDialog(
    file: File? = null,
    uri: Uri? = null,
    documentTitle: String? = null,
    operationMode: PdfPreviewOperationMode = PdfPreviewOperationMode.GENERAL,
    initialPageIndex: Int = 0,
    initialGridMode: Boolean = true,
    selectedPages: Set<Int> = emptySet(),
    onTogglePageSelection: ((Int) -> Unit)? = null,
    onSelectAllPages: (() -> Unit)? = null,
    onClearPageSelection: (() -> Unit)? = null,
    onSelectOddPages: (() -> Unit)? = null,
    onSelectEvenPages: (() -> Unit)? = null,
    pageRotations: Map<Int, Int> = emptyMap(),
    onRotateSinglePage: ((Int, Int) -> Unit)? = null,
    onRotateSelectedPages: ((Int) -> Unit)? = null,
    onResetRotations: (() -> Unit)? = null,
    deletedPages: Set<Int> = emptySet(),
    onToggleDeletePage: ((Int) -> Unit)? = null,
    onDeleteSelectedPages: (() -> Unit)? = null,
    onRestoreAllDeletedPages: (() -> Unit)? = null,
    onModifiedPdfSaved: ((File) -> Unit)? = null,
    onPageSelected: ((Int) -> Unit)? = null,
    onExecutePrimaryAction: (() -> Unit)? = null,
    onDismissRequest: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxSize()
                .testTag("pdf_renderer_preview_dialog")
        ) {
            PdfPreviewScreen(
                file = file,
                uri = uri,
                documentTitle = documentTitle,
                operationMode = operationMode,
                initialPageIndex = initialPageIndex,
                initialGridMode = initialGridMode,
                selectedPages = selectedPages,
                onTogglePageSelection = onTogglePageSelection,
                onSelectAllPages = onSelectAllPages,
                onClearPageSelection = onClearPageSelection,
                onSelectOddPages = onSelectOddPages,
                onSelectEvenPages = onSelectEvenPages,
                pageRotations = pageRotations,
                onRotateSinglePage = onRotateSinglePage,
                onRotateSelectedPages = onRotateSelectedPages,
                onResetRotations = onResetRotations,
                deletedPages = deletedPages,
                onToggleDeletePage = onToggleDeletePage,
                onDeleteSelectedPages = onDeleteSelectedPages,
                onRestoreAllDeletedPages = onRestoreAllDeletedPages,
                onModifiedPdfSaved = onModifiedPdfSaved,
                onPageSelected = onPageSelected,
                onExecutePrimaryAction = onExecutePrimaryAction,
                onNavigateBack = onDismissRequest
            )
        }
    }
}

/**
 * Dedicated PDF Preview Screen powered by Android's native [PdfRenderer].
 * Features an interactive Thumbnail Grid View for rapid page navigation, multi-page selection,
 * per-page/batch rotation, and per-page/batch deletion, paired with a High-Resolution Single-Page Inspector.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPreviewScreen(
    file: File? = null,
    uri: Uri? = null,
    documentTitle: String? = null,
    operationMode: PdfPreviewOperationMode = PdfPreviewOperationMode.GENERAL,
    initialPageIndex: Int = 0,
    initialGridMode: Boolean = true,
    selectedPages: Set<Int> = emptySet(),
    onTogglePageSelection: ((Int) -> Unit)? = null,
    onSelectAllPages: (() -> Unit)? = null,
    onClearPageSelection: (() -> Unit)? = null,
    onSelectOddPages: (() -> Unit)? = null,
    onSelectEvenPages: (() -> Unit)? = null,
    pageRotations: Map<Int, Int> = emptyMap(),
    onRotateSinglePage: ((Int, Int) -> Unit)? = null,
    onRotateSelectedPages: ((Int) -> Unit)? = null,
    onResetRotations: (() -> Unit)? = null,
    deletedPages: Set<Int> = emptySet(),
    onToggleDeletePage: ((Int) -> Unit)? = null,
    onDeleteSelectedPages: (() -> Unit)? = null,
    onRestoreAllDeletedPages: (() -> Unit)? = null,
    onModifiedPdfSaved: ((File) -> Unit)? = null,
    onPageSelected: ((Int) -> Unit)? = null,
    onExecutePrimaryAction: (() -> Unit)? = null,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val coroutineScope = rememberCoroutineScope()

    var resolvedFile by remember(file, uri) { mutableStateOf(file) }
    var isLoadingDocument by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var totalPages by remember { mutableIntStateOf(0) }

    // View mode: true = Thumbnail Grid View, false = Single-Page High-Res Inspector
    var isGridMode by remember { mutableStateOf(initialGridMode) }
    var inspectedPageIndex by remember { mutableIntStateOf(initialPageIndex.coerceAtLeast(0)) }

    // Grid column count: 0 = Adaptive, 2 = 2 columns, 3 = 3 columns, 4 = 4 columns
    var gridColumnCount by remember { mutableIntStateOf(0) }

    // Tap behavior in grid: false = tap thumbnail to navigate/inspect, true = tap anywhere on card to select
    var isTapToSelectMode by remember {
        mutableStateOf(operationMode == PdfPreviewOperationMode.SPLIT || operationMode == PdfPreviewOperationMode.ROTATE)
    }

    // Internal fallback state so GENERAL preview mode also supports multi-page selection, rotation, and deletion
    var localSelectedPages by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var localRotations by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var localDeletedPages by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var isSavingModifiedPdf by remember { mutableStateOf(false) }
    var statusBannerMessage by remember { mutableStateOf<String?>(null) }

    val activeSelectedPages = if (onTogglePageSelection != null) selectedPages else localSelectedPages
    val activeRotations = if (onRotateSinglePage != null) pageRotations else localRotations
    val activeDeletedPages = if (onToggleDeletePage != null) deletedPages else localDeletedPages

    val handleToggleSelectPage: (Int) -> Unit = { pageIdx ->
        if (onTogglePageSelection != null) {
            onTogglePageSelection(pageIdx)
        } else if (!activeDeletedPages.contains(pageIdx)) {
            localSelectedPages = if (localSelectedPages.contains(pageIdx)) {
                localSelectedPages - pageIdx
            } else {
                localSelectedPages + pageIdx
            }
        }
    }

    val handleSelectAllPages: () -> Unit = {
        if (onSelectAllPages != null) {
            onSelectAllPages()
        } else {
            localSelectedPages = (0 until totalPages).filterNot { activeDeletedPages.contains(it) }.toSet()
        }
    }

    val handleClearSelection: () -> Unit = {
        if (onClearPageSelection != null) {
            onClearPageSelection()
        } else {
            localSelectedPages = emptySet()
        }
    }

    val handleInvertSelection: () -> Unit = {
        if (onTogglePageSelection != null) {
            for (i in 0 until totalPages) {
                if (!activeDeletedPages.contains(i)) {
                    onTogglePageSelection(i)
                }
            }
        } else {
            val allValid = (0 until totalPages).filterNot { activeDeletedPages.contains(it) }.toSet()
            localSelectedPages = allValid - localSelectedPages
        }
    }

    val handleSelectOddPages: () -> Unit = {
        if (onSelectOddPages != null) {
            onSelectOddPages()
        } else {
            localSelectedPages = (0 until totalPages)
                .filter { it % 2 == 0 && !activeDeletedPages.contains(it) }
                .toSet()
        }
    }

    val handleSelectEvenPages: () -> Unit = {
        if (onSelectEvenPages != null) {
            onSelectEvenPages()
        } else {
            localSelectedPages = (0 until totalPages)
                .filter { it % 2 != 0 && !activeDeletedPages.contains(it) }
                .toSet()
        }
    }

    val handleRotatePage: (Int, Int) -> Unit = { pageIdx, deltaDegrees ->
        if (onRotateSinglePage != null) {
            onRotateSinglePage(pageIdx, deltaDegrees)
        } else {
            val current = localRotations[pageIdx] ?: 0
            val next = (current + deltaDegrees) % 360
            localRotations = localRotations.toMutableMap().apply {
                put(pageIdx, if (next < 0) next + 360 else next)
            }
        }
    }

    val handleRotateSelectedPages: (Int) -> Unit = { deltaDegrees ->
        val targets = if (activeSelectedPages.isNotEmpty()) {
            activeSelectedPages
        } else if (!isGridMode) {
            setOf(inspectedPageIndex)
        } else {
            emptySet()
        }
        if (onRotateSelectedPages != null && activeSelectedPages.isNotEmpty()) {
            onRotateSelectedPages(deltaDegrees)
        } else {
            targets.forEach { idx -> handleRotatePage(idx, deltaDegrees) }
        }
    }

    val handleResetRotations: () -> Unit = {
        if (onResetRotations != null) {
            onResetRotations()
        } else {
            localRotations = emptyMap()
        }
    }

    val handleToggleDeletePage: (Int) -> Unit = { pageIdx ->
        if (onToggleDeletePage != null) {
            onToggleDeletePage(pageIdx)
        } else {
            if (localDeletedPages.contains(pageIdx)) {
                localDeletedPages = localDeletedPages - pageIdx
            } else if (localDeletedPages.size < (totalPages - 1).coerceAtLeast(0)) {
                localDeletedPages = localDeletedPages + pageIdx
                localSelectedPages = localSelectedPages - pageIdx
            } else {
                statusBannerMessage = "At least 1 page must remain in the document."
            }
        }
    }

    val handleDeleteSelectedPages: () -> Unit = {
        if (onDeleteSelectedPages != null) {
            onDeleteSelectedPages()
        } else if (activeSelectedPages.isNotEmpty()) {
            val combined = localDeletedPages + activeSelectedPages
            if (combined.size >= totalPages && totalPages > 0) {
                val keepFirst = (0 until totalPages).firstOrNull { !localDeletedPages.contains(it) } ?: 0
                localDeletedPages = combined - keepFirst
                localSelectedPages = emptySet()
                statusBannerMessage = "Removed ${localDeletedPages.size} pages (kept Page ${keepFirst + 1})."
            } else {
                localDeletedPages = combined
                localSelectedPages = emptySet()
                statusBannerMessage = "Marked ${activeSelectedPages.size} page(s) for deletion."
            }
        }
    }

    val handleRestoreAllDeletedPages: () -> Unit = {
        if (onRestoreAllDeletedPages != null) {
            onRestoreAllDeletedPages()
        } else {
            localDeletedPages = emptySet()
            statusBannerMessage = null
        }
    }

    // Resolve URI to local File if needed
    LaunchedEffect(file, uri) {
        if (file != null && file.exists()) {
            resolvedFile = file
        } else if (uri != null) {
            isLoadingDocument = true
            loadError = null
            try {
                resolvedFile = PdfEngine.getLocalFileFromUri(context, uri)
            } catch (e: Exception) {
                loadError = e.localizedMessage
                isLoadingDocument = false
            }
        } else {
            isLoadingDocument = false
        }
    }

    val rendererController = remember(resolvedFile?.absolutePath) {
        resolvedFile?.takeIf { it.exists() }?.let { PdfRendererController(it) }
    }

    DisposableEffect(rendererController) {
        onDispose {
            if (rendererController != null) {
                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                    rendererController.close()
                }
            }
        }
    }

    LaunchedEffect(rendererController) {
        val controller = rendererController ?: return@LaunchedEffect
        isLoadingDocument = true
        loadError = null
        try {
            val count = controller.initialize()
            totalPages = count
            if (inspectedPageIndex >= count && count > 0) {
                inspectedPageIndex = 0
            }
        } catch (e: Exception) {
            loadError = e.localizedMessage
        } finally {
            isLoadingDocument = false
        }
    }

    BackHandler {
        if (!isGridMode && initialGridMode) {
            isGridMode = true
        } else {
            onNavigateBack()
        }
    }

    val rotatedPageCount = activeRotations.count { it.value % 360 != 0 }
    val deletedPageCount = activeDeletedPages.size
    val hasGeneralModifications = rotatedPageCount > 0 || deletedPageCount > 0

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("pdf_preview_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = documentTitle ?: resolvedFile?.name ?: stringResource(R.string.preview_document_live),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val subtitleText = if (totalPages > 0) {
                            val sizeStr = PdfEngine.formatFileSize(resolvedFile?.length() ?: 0L)
                            if (isGridMode) {
                                val activeCount = (totalPages - deletedPageCount).coerceAtLeast(1)
                                "${stringResource(R.string.preview_total_pages_label, activeCount)} • $sizeStr"
                            } else {
                                "${stringResource(R.string.viewer_page_of_total, inspectedPageIndex + 1, totalPages)} • $sizeStr"
                            }
                        } else {
                            stringResource(R.string.action_rendering)
                        }
                        Text(
                            text = subtitleText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (!isGridMode && initialGridMode) {
                                isGridMode = true
                            } else {
                                onNavigateBack()
                            }
                        },
                        modifier = Modifier.testTag("pdf_preview_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_close)
                        )
                    }
                },
                actions = {
                    if (totalPages > 0) {
                        // Grid Column Density Switcher when in Grid Mode
                        if (isGridMode) {
                            OutlinedButton(
                                onClick = {
                                    gridColumnCount = when (gridColumnCount) {
                                        0 -> 2
                                        2 -> 3
                                        3 -> 4
                                        else -> 0
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .height(36.dp)
                                    .testTag("pdf_preview_grid_columns_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Apps,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (gridColumnCount == 0) "Auto" else "${gridColumnCount}×",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Toggle between Thumbnail Grid View and Single-Page High-Res Inspector
                        FilledTonalIconButton(
                            onClick = { isGridMode = !isGridMode },
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .testTag("pdf_preview_toggle_mode_button")
                        ) {
                            Icon(
                                imageVector = if (isGridMode) Icons.Default.ZoomIn else Icons.Default.GridView,
                                contentDescription = if (isGridMode) {
                                    stringResource(R.string.preview_zoom_in)
                                } else {
                                    stringResource(R.string.action_preview)
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (totalPages > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            when (operationMode) {
                                PdfPreviewOperationMode.SPLIT -> {
                                    Text(
                                        text = stringResource(R.string.recent_selected_count, activeSelectedPages.size, totalPages),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val subInfo = buildList {
                                        if (rotatedPageCount > 0) add("$rotatedPageCount rotated")
                                        if (deletedPageCount > 0) add("$deletedPageCount deleted")
                                        if (isEmpty()) add(stringResource(R.string.tool_split_desc))
                                    }.joinToString(" • ")
                                    Text(
                                        text = subInfo,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                PdfPreviewOperationMode.ROTATE -> {
                                    Text(
                                        text = stringResource(R.string.rotate_selected_count, activeSelectedPages.size, totalPages),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val subInfo = buildList {
                                        if (rotatedPageCount > 0) add("$rotatedPageCount rotated")
                                        if (deletedPageCount > 0) add("$deletedPageCount deleted")
                                        if (isEmpty()) add(stringResource(R.string.rotate_screen_subtitle))
                                    }.joinToString(" • ")
                                    Text(
                                        text = subInfo,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                PdfPreviewOperationMode.GENERAL -> {
                                    val keptPages = (totalPages - deletedPageCount).coerceAtLeast(1)
                                    Text(
                                        text = if (activeSelectedPages.isNotEmpty()) {
                                            stringResource(R.string.recent_selected_count, activeSelectedPages.size, totalPages)
                                        } else {
                                            stringResource(R.string.preview_total_pages_label, keptPages)
                                        },
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val subInfo = buildList {
                                        if (rotatedPageCount > 0) add("$rotatedPageCount rotated")
                                        if (deletedPageCount > 0) add("$deletedPageCount removed")
                                        if (isEmpty()) add(resolvedFile?.name ?: "")
                                    }.joinToString(" • ")
                                    Text(
                                        text = subInfo,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (onExecutePrimaryAction != null &&
                                (operationMode == PdfPreviewOperationMode.SPLIT || operationMode == PdfPreviewOperationMode.ROTATE)
                            ) {
                                OutlinedButton(
                                    onClick = onNavigateBack,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.height(48.dp).testTag("pdf_preview_done_button")
                                ) {
                                    Text(stringResource(R.string.action_done), fontWeight = FontWeight.Bold)
                                }

                                val canExecute = when (operationMode) {
                                    PdfPreviewOperationMode.SPLIT -> activeSelectedPages.isNotEmpty()
                                    PdfPreviewOperationMode.ROTATE -> rotatedPageCount > 0 || deletedPageCount > 0
                                    else -> true
                                }

                                Button(
                                    onClick = {
                                        onNavigateBack()
                                        onExecutePrimaryAction()
                                    },
                                    enabled = canExecute,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.height(48.dp).testTag("pdf_preview_execute_button")
                                ) {
                                    Icon(
                                        imageVector = if (operationMode == PdfPreviewOperationMode.SPLIT) {
                                            Icons.Default.ContentCut
                                        } else {
                                            Icons.Default.RotateRight
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (operationMode == PdfPreviewOperationMode.SPLIT) {
                                            stringResource(R.string.action_split)
                                        } else {
                                            stringResource(R.string.action_save)
                                        },
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                if (hasGeneralModifications && resolvedFile != null) {
                                    OutlinedButton(
                                        onClick = onNavigateBack,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.height(48.dp).testTag("pdf_preview_done_button")
                                    ) {
                                        Text(stringResource(R.string.action_close), fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = {
                                            val source = resolvedFile ?: return@Button
                                            coroutineScope.launch {
                                                isSavingModifiedPdf = true
                                                try {
                                                    val outName = "Edited_${source.name}"
                                                    val savedFile = PdfEngine.applyPageRotationsAndDeletions(
                                                        context = context,
                                                        sourceFile = source,
                                                        pageRotations = activeRotations,
                                                        deletedPages = activeDeletedPages,
                                                        outputName = outName
                                                    )
                                                    resolvedFile = savedFile
                                                    localRotations = emptyMap()
                                                    localDeletedPages = emptySet()
                                                    localSelectedPages = emptySet()
                                                    statusBannerMessage = "Saved ${savedFile.name} (${PdfEngine.formatFileSize(savedFile.length())})"
                                                    onModifiedPdfSaved?.invoke(savedFile)
                                                } catch (e: Exception) {
                                                    statusBannerMessage = e.localizedMessage
                                                } finally {
                                                    isSavingModifiedPdf = false
                                                }
                                            }
                                        },
                                        enabled = !isSavingModifiedPdf,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.height(48.dp).testTag("pdf_preview_save_modified_button")
                                    ) {
                                        if (isSavingModifiedPdf) {
                                            CircularProgressIndicator(
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        } else {
                                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            onPageSelected?.invoke(inspectedPageIndex)
                                            onNavigateBack()
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.height(48.dp).testTag("pdf_preview_done_button")
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(stringResource(R.string.action_done), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Optional status feedback banner when pages are deleted, restored, or saved
            AnimatedVisibility(visible = statusBannerMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = statusBannerMessage ?: "",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { statusBannerMessage = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.action_close),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Always-available Page Selection, Rotation & Deletion Action Toolbar
            if (totalPages > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pdf_preview_action_toolbar")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Row 1: Selection Mode & Quick Selection Filter Chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isGridMode) {
                                FilterChip(
                                    selected = isTapToSelectMode,
                                    onClick = { isTapToSelectMode = !isTapToSelectMode },
                                    label = {
                                        Text(
                                            text = if (isTapToSelectMode) {
                                                "${activeSelectedPages.size}/$totalPages"
                                            } else {
                                                stringResource(R.string.action_select_all)
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isTapToSelectMode) Icons.Default.CheckBox else Icons.Default.TouchApp,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    },
                                    modifier = Modifier.testTag("preview_multiselect_mode_chip")
                                )
                            }

                            AssistChip(
                                onClick = handleSelectAllPages,
                                label = { Text(stringResource(R.string.action_select_all), fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(15.dp))
                                },
                                modifier = Modifier.testTag("preview_select_all_chip")
                            )

                            AssistChip(
                                onClick = handleClearSelection,
                                label = { Text(stringResource(R.string.action_clear), fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(15.dp))
                                },
                                modifier = Modifier.testTag("preview_clear_selection_chip")
                            )

                            AssistChip(
                                onClick = handleInvertSelection,
                                label = { Text("⇄", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                modifier = Modifier.testTag("preview_invert_selection_chip")
                            )

                            AssistChip(
                                onClick = handleSelectOddPages,
                                label = { Text(stringResource(R.string.split_odd_pages), fontSize = 11.sp) },
                                modifier = Modifier.testTag("preview_odd_pages_chip")
                            )

                            AssistChip(
                                onClick = handleSelectEvenPages,
                                label = { Text(stringResource(R.string.split_even_pages), fontSize = 11.sp) },
                                modifier = Modifier.testTag("preview_even_pages_chip")
                            )
                        }

                        // Row 2: Batch Rotation (+90°, +180°, -90°) & Page Deletion / Restore Controls
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val hasTargetPages = activeSelectedPages.isNotEmpty() || !isGridMode

                            FilledTonalButton(
                                onClick = { handleRotateSelectedPages(90) },
                                enabled = hasTargetPages,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("preview_rotate_90_btn")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("+90°", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            FilledTonalButton(
                                onClick = { handleRotateSelectedPages(180) },
                                enabled = hasTargetPages,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("preview_rotate_180_btn")
                            ) {
                                Icon(Icons.Default.ScreenRotation, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("+180°", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            FilledTonalButton(
                                onClick = { handleRotateSelectedPages(270) },
                                enabled = hasTargetPages,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("preview_rotate_270_btn")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.RotateLeft, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("-90°", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            // Batch Delete Selected Pages (or current page in inspector)
                            Button(
                                onClick = {
                                    if (activeSelectedPages.isNotEmpty()) {
                                        handleDeleteSelectedPages()
                                    } else if (!isGridMode) {
                                        handleToggleDeletePage(inspectedPageIndex)
                                    }
                                },
                                enabled = hasTargetPages && totalPages > 1,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("preview_delete_selected_btn")
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (activeSelectedPages.isNotEmpty()) {
                                        "${stringResource(R.string.action_delete)} (${activeSelectedPages.size})"
                                    } else {
                                        stringResource(R.string.action_delete)
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Reset Rotations Button
                            if (rotatedPageCount > 0) {
                                OutlinedButton(
                                    onClick = handleResetRotations,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .height(34.dp)
                                        .testTag("preview_reset_rotations_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RestartAlt,
                                        contentDescription = stringResource(R.string.rotate_reset_rotations),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("0°", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Restore Deleted Pages Button
                            if (deletedPageCount > 0) {
                                OutlinedButton(
                                    onClick = handleRestoreAllDeletedPages,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .height(34.dp)
                                        .testTag("preview_restore_deleted_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RestoreFromTrash,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "+$deletedPageCount",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Main Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    isLoadingDocument -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.action_rendering),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    loadError != null || rendererController == null || totalPages <= 0 -> {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = loadError ?: stringResource(R.string.preview_unable_render),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    isGridMode -> {
                        PdfRendererPageGrid(
                            controller = rendererController,
                            totalPages = totalPages,
                            density = density,
                            gridColumnCount = gridColumnCount,
                            isTapToSelectMode = isTapToSelectMode,
                            currentInspectedPageIndex = inspectedPageIndex,
                            selectedPages = activeSelectedPages,
                            onTogglePageSelection = handleToggleSelectPage,
                            pageRotations = activeRotations,
                            onRotateSinglePage = handleRotatePage,
                            deletedPages = activeDeletedPages,
                            onToggleDeletePage = handleToggleDeletePage,
                            onInspectPage = { pageIdx ->
                                inspectedPageIndex = pageIdx
                                onPageSelected?.invoke(pageIdx)
                                isGridMode = false
                            }
                        )
                    }
                    else -> {
                        PdfRendererSinglePageInspector(
                            controller = rendererController,
                            totalPages = totalPages,
                            currentPageIndex = inspectedPageIndex,
                            onPageChange = { newIdx ->
                                inspectedPageIndex = newIdx
                                onPageSelected?.invoke(newIdx)
                            },
                            density = density,
                            isSelected = activeSelectedPages.contains(inspectedPageIndex),
                            onToggleSelection = { handleToggleSelectPage(inspectedPageIndex) },
                            rotationDegrees = activeRotations[inspectedPageIndex] ?: 0,
                            onRotateCurrentPage = { delta -> handleRotatePage(inspectedPageIndex, delta) },
                            isDeleted = activeDeletedPages.contains(inspectedPageIndex),
                            onToggleDeleteCurrentPage = if (totalPages > 1) {
                                { handleToggleDeletePage(inspectedPageIndex) }
                            } else null,
                            onOpenThumbnailGrid = { isGridMode = true }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfRendererPageGrid(
    controller: PdfRendererController,
    totalPages: Int,
    density: Float,
    gridColumnCount: Int,
    isTapToSelectMode: Boolean,
    currentInspectedPageIndex: Int,
    selectedPages: Set<Int>,
    onTogglePageSelection: (Int) -> Unit,
    pageRotations: Map<Int, Int>,
    onRotateSinglePage: (Int, Int) -> Unit,
    deletedPages: Set<Int>,
    onToggleDeletePage: (Int) -> Unit,
    onInspectPage: (Int) -> Unit
) {
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()

    val gridCells = remember(gridColumnCount) {
        if (gridColumnCount in 2..4) {
            GridCells.Fixed(gridColumnCount)
        } else {
            GridCells.Adaptive(minSize = 148.dp)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Quick Page Jump Strip for instant navigation across multi-page documents
        if (totalPages > 1) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pdf_preview_quick_jump_bar")
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(totalPages) { idx ->
                        val isSelected = selectedPages.contains(idx)
                        val isDeleted = deletedPages.contains(idx)
                        val isRotated = (pageRotations[idx] ?: 0) % 360 != 0
                        val isCurrent = idx == currentInspectedPageIndex

                        Surface(
                            color = when {
                                isDeleted -> MaterialTheme.colorScheme.errorContainer
                                isSelected -> MaterialTheme.colorScheme.primary
                                isCurrent -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = if (isRotated) {
                                BorderStroke(1.5.dp, MaterialTheme.colorScheme.tertiary)
                            } else null,
                            modifier = Modifier
                                .clickable {
                                    coroutineScope.launch {
                                        gridState.animateScrollToItem(idx)
                                    }
                                }
                                .testTag("pdf_preview_jump_pill_$idx")
                        ) {
                            Text(
                                text = "${idx + 1}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                textDecoration = if (isDeleted) TextDecoration.LineThrough else TextDecoration.None,
                                color = when {
                                    isDeleted -> MaterialTheme.colorScheme.onErrorContainer
                                    isSelected -> MaterialTheme.colorScheme.onPrimary
                                    isCurrent -> MaterialTheme.colorScheme.onPrimaryContainer
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        LazyVerticalGrid(
            state = gridState,
            columns = gridCells,
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("pdf_renderer_page_grid")
        ) {
            items(
                count = totalPages,
                key = { pageIdx -> "pdf_page_grid_$pageIdx" }
            ) { pageIdx ->
                var thumbnail by remember(controller, pageIdx) {
                    mutableStateOf(controller.getCachedThumbnail(pageIdx))
                }
                var dimension by remember(controller, pageIdx) {
                    mutableStateOf(controller.getCachedDimension(pageIdx))
                }

                LaunchedEffect(controller, pageIdx) {
                    if (thumbnail == null) {
                        val targetWidthPx = (180 * density).toInt()
                        val (bmp, dim) = controller.renderPage(
                            pageIndex = pageIdx,
                            targetWidthPx = targetWidthPx,
                            highRes = false
                        )
                        thumbnail = bmp
                        dimension = dim
                    }
                }

                val isSelected = selectedPages.contains(pageIdx)
                val isDeleted = deletedPages.contains(pageIdx)
                val rotationAngle = pageRotations[pageIdx] ?: 0
                val animatedRotation by animateFloatAsState(
                    targetValue = rotationAngle.toFloat(),
                    label = "gridPageRotation_$pageIdx"
                )

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            isDeleted -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f)
                            isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            else -> MaterialTheme.colorScheme.surface
                        }
                    ),
                    border = BorderStroke(
                        width = if (isSelected || isDeleted) 2.dp else 1.dp,
                        color = when {
                            isDeleted -> MaterialTheme.colorScheme.error
                            isSelected -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outlineVariant
                        }
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (isTapToSelectMode) {
                                onTogglePageSelection(pageIdx)
                            } else {
                                onInspectPage(pageIdx)
                            }
                        }
                        .testTag("pdf_preview_grid_item_$pageIdx")
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        // Card Header: Page badge, dimension info, selection checkbox
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTogglePageSelection(pageIdx) },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Surface(
                                    color = when {
                                        isDeleted -> MaterialTheme.colorScheme.error
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.secondaryContainer
                                    },
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "#${pageIdx + 1}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        textDecoration = if (isDeleted) TextDecoration.LineThrough else TextDecoration.None,
                                        color = when {
                                            isDeleted -> MaterialTheme.colorScheme.onError
                                            isSelected -> MaterialTheme.colorScheme.onPrimary
                                            else -> MaterialTheme.colorScheme.onSecondaryContainer
                                        },
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }

                                if (gridColumnCount <= 2) {
                                    dimension?.let { dim ->
                                        Text(
                                            text = "${dim.widthPt}×${dim.heightPt}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            Checkbox(
                                checked = isSelected && !isDeleted,
                                enabled = !isDeleted,
                                onCheckedChange = { onTogglePageSelection(pageIdx) },
                                modifier = Modifier
                                    .size(24.dp)
                                    .testTag("pdf_preview_checkbox_$pageIdx")
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Rendered Page Canvas Box (Tapping thumbnail navigates to inspect page or selects in select mode)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(dimension?.aspectRatio ?: 0.72f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                .clickable {
                                    if (isTapToSelectMode) {
                                        onTogglePageSelection(pageIdx)
                                    } else {
                                        onInspectPage(pageIdx)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            val bmp = thumbnail
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = stringResource(R.string.viewer_page_of_total, pageIdx + 1, totalPages),
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(4.dp)
                                        .alpha(if (isDeleted) 0.35f else 1f)
                                        .rotate(animatedRotation)
                                )
                            } else {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Rotation degree badge if rotated
                            if (rotationAngle % 360 != 0) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                ) {
                                    Text(
                                        text = "${rotationAngle % 360}°",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // Deleted page overlay
                            if (isDeleted) {
                                Surface(
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.88f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .clickable { onToggleDeletePage(pageIdx) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.RestoreFromTrash,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onError,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(R.string.action_delete),
                                            color = MaterialTheme.colorScheme.onError,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Card Footer: Quick Rotate, Delete/Restore, and Full-Page Navigate/Inspect buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                // 1-Tap Rotate Page (+90°)
                                FilledTonalIconButton(
                                    onClick = { onRotateSinglePage(pageIdx, 90) },
                                    enabled = !isDeleted,
                                    modifier = Modifier
                                        .size(30.dp)
                                        .testTag("pdf_preview_rotate_page_$pageIdx")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.RotateRight,
                                        contentDescription = stringResource(R.string.tool_rotate_title),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }

                                // 1-Tap Delete / Restore Page
                                if (totalPages > 1) {
                                    FilledTonalIconButton(
                                        onClick = { onToggleDeletePage(pageIdx) },
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = if (isDeleted) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
                                            },
                                            contentColor = if (isDeleted) {
                                                MaterialTheme.colorScheme.onError
                                            } else {
                                                MaterialTheme.colorScheme.error
                                            }
                                        ),
                                        modifier = Modifier
                                            .size(30.dp)
                                            .testTag("pdf_preview_delete_page_$pageIdx")
                                    ) {
                                        Icon(
                                            imageVector = if (isDeleted) Icons.Default.RestoreFromTrash else Icons.Default.DeleteOutline,
                                            contentDescription = stringResource(R.string.action_delete),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }

                            // 1-Tap Navigate / Inspect Page
                            FilledTonalIconButton(
                                onClick = { onInspectPage(pageIdx) },
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("pdf_preview_inspect_page_$pageIdx")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ZoomIn,
                                    contentDescription = stringResource(R.string.action_view),
                                    tint = MaterialTheme.colorScheme.primary,
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

@Composable
private fun PdfRendererSinglePageInspector(
    controller: PdfRendererController,
    totalPages: Int,
    currentPageIndex: Int,
    onPageChange: (Int) -> Unit,
    density: Float,
    isSelected: Boolean,
    onToggleSelection: (() -> Unit)?,
    rotationDegrees: Int,
    onRotateCurrentPage: (Int) -> Unit,
    isDeleted: Boolean,
    onToggleDeleteCurrentPage: (() -> Unit)?,
    onOpenThumbnailGrid: () -> Unit
) {
    var highResBitmap by remember(controller, currentPageIndex) { mutableStateOf<Bitmap?>(null) }
    var pageDim by remember(controller, currentPageIndex) {
        mutableStateOf(controller.getCachedDimension(currentPageIndex))
    }
    var isRenderingHighRes by remember(controller, currentPageIndex) { mutableStateOf(true) }

    var zoomScale by remember(currentPageIndex) { mutableFloatStateOf(1f) }
    var panX by remember(currentPageIndex) { mutableFloatStateOf(0f) }
    var panY by remember(currentPageIndex) { mutableFloatStateOf(0f) }

    val animatedRotation by animateFloatAsState(
        targetValue = rotationDegrees.toFloat(),
        label = "inspectorRotation"
    )

    LaunchedEffect(controller, currentPageIndex) {
        isRenderingHighRes = true
        val targetWidthPx = (480 * density).toInt()
        val (bmp, dim) = controller.renderPage(
            pageIndex = currentPageIndex,
            targetWidthPx = targetWidthPx,
            highRes = true
        )
        highResBitmap = bmp
        pageDim = dim
        isRenderingHighRes = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("pdf_renderer_single_page_inspector")
    ) {
        // High-Res Interactive Canvas
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF14181D))
                .clipToBounds()
                .pointerInput(currentPageIndex) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (zoomScale > 1.05f) {
                                zoomScale = 1f
                                panX = 0f
                                panY = 0f
                            } else {
                                zoomScale = 2.25f
                            }
                        }
                    )
                }
                .pointerInput(currentPageIndex) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (zoomScale * zoom).coerceIn(1f, 4f)
                        zoomScale = newScale
                        if (newScale > 1f) {
                            panX += pan.x
                            panY += pan.y
                        } else {
                            panX = 0f
                            panY = 0f
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val displayBitmap = highResBitmap ?: controller.getCachedThumbnail(currentPageIndex)
            if (displayBitmap != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                    modifier = Modifier
                        .padding(16.dp)
                        .graphicsLayer(
                            scaleX = zoomScale,
                            scaleY = zoomScale,
                            translationX = panX,
                            translationY = panY,
                            rotationZ = animatedRotation
                        )
                ) {
                    Image(
                        bitmap = displayBitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.viewer_page_of_total, currentPageIndex + 1, totalPages),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(4.dp)
                            .alpha(if (isDeleted) 0.35f else 1f)
                    )
                }
            } else if (isRenderingHighRes) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            } else {
                Text(
                    text = stringResource(R.string.preview_unable_render),
                    color = Color.White
                )
            }

            // Top-left selection toggle chip
            if (onToggleSelection != null) {
                Surface(
                    color = when {
                        isDeleted -> MaterialTheme.colorScheme.error
                        isSelected -> MaterialTheme.colorScheme.primary
                        else -> Color.Black.copy(alpha = 0.72f)
                    },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .clickable { onToggleSelection() }
                        .testTag("inspector_toggle_page_selection")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when {
                                isDeleted -> Icons.Default.DeleteForever
                                isSelected -> Icons.Default.CheckBox
                                else -> Icons.Default.CheckBoxOutlineBlank
                            },
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.viewer_page_of_total, currentPageIndex + 1, totalPages),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Top-right button to return to Thumbnail Grid View
            Surface(
                color = Color.Black.copy(alpha = 0.72f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clickable { onOpenThumbnailGrid() }
                    .testTag("inspector_open_grid_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.GridView,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_preview),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Floating Zoom, Rotation & Delete Controls on bottom-end
            Surface(
                color = Color.Black.copy(alpha = 0.78f),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    IconButton(
                        onClick = { zoomScale = (zoomScale - 0.25f).coerceAtLeast(1f) },
                        modifier = Modifier.size(32.dp).testTag("inspector_zoom_out")
                    ) {
                        Icon(Icons.Default.ZoomOut, contentDescription = stringResource(R.string.preview_zoom_out), tint = Color.White, modifier = Modifier.size(18.dp))
                    }

                    Text(
                        text = "${(zoomScale * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .clickable {
                                zoomScale = 1f
                                panX = 0f
                                panY = 0f
                            }
                    )

                    IconButton(
                        onClick = { zoomScale = (zoomScale + 0.25f).coerceAtMost(4f) },
                        modifier = Modifier.size(32.dp).testTag("inspector_zoom_in")
                    ) {
                        Icon(Icons.Default.ZoomIn, contentDescription = stringResource(R.string.preview_zoom_in), tint = Color.White, modifier = Modifier.size(18.dp))
                    }

                    IconButton(
                        onClick = { onRotateCurrentPage(90) },
                        modifier = Modifier.size(32.dp).testTag("inspector_rotate_page")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.RotateRight,
                            contentDescription = stringResource(R.string.tool_rotate_title),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (onToggleDeleteCurrentPage != null) {
                        IconButton(
                            onClick = onToggleDeleteCurrentPage,
                            modifier = Modifier.size(32.dp).testTag("inspector_delete_page")
                        ) {
                            Icon(
                                imageVector = if (isDeleted) Icons.Default.RestoreFromTrash else Icons.Default.DeleteOutline,
                                contentDescription = stringResource(R.string.action_delete),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Previous / Next Page Navigation + Page Scrubber Strip
        Surface(
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = { if (currentPageIndex > 0) onPageChange(currentPageIndex - 1) },
                        enabled = currentPageIndex > 0,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("inspector_prev_page_button")
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.preview_action_previous), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.viewer_page_of_total, currentPageIndex + 1, totalPages),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        pageDim?.let { dim ->
                            Text(
                                text = "${dim.widthPt} × ${dim.heightPt} pt • ${rotationDegrees % 360}°",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = { if (currentPageIndex < totalPages - 1) onPageChange(currentPageIndex + 1) },
                        enabled = currentPageIndex < totalPages - 1,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("inspector_next_page_button")
                    ) {
                        Text(stringResource(R.string.preview_action_next), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }

                if (totalPages > 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(totalPages) { idx ->
                            val isCurrent = idx == currentPageIndex
                            var thumb by remember(controller, idx) {
                                mutableStateOf(controller.getCachedThumbnail(idx))
                            }
                            LaunchedEffect(controller, idx) {
                                if (thumb == null) {
                                    val (bmp, _) = controller.renderPage(idx, (80 * density).toInt(), highRes = false)
                                    thumb = bmp
                                }
                            }

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                border = BorderStroke(
                                    width = if (isCurrent) 2.dp else 1.dp,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .width(52.dp)
                                    .height(68.dp)
                                    .clickable { onPageChange(idx) }
                                    .testTag("inspector_thumb_$idx")
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    if (thumb != null) {
                                        Image(
                                            bitmap = thumb!!.asImageBitmap(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize().padding(2.dp)
                                        )
                                    }
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.65f),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(2.dp)
                                    ) {
                                        Text(
                                            text = "${idx + 1}",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
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
