package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.data.pdf.PdfEngine
import com.example.ui.MainViewModel
import com.example.ui.components.PdfDocumentPickerCard
import com.example.ui.components.PdfDocumentPreviewCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RotateScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    val rotateSourceUri by viewModel.rotateSourceUri.collectAsState()
    val rotateSourceFile by viewModel.rotateSourceFile.collectAsState()
    val pageCount by viewModel.rotatePageCount.collectAsState()
    val selectedPages by viewModel.rotateSelectedPages.collectAsState()
    val pageRotations by viewModel.rotatePageRotations.collectAsState()
    val deletedPages by viewModel.rotateDeletedPages.collectAsState()
    val outputName by viewModel.rotateOutputName.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val processingMessage by viewModel.processingMessage.collectAsState()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.loadPdfForRotate(uri)
        }
    }

    var showPreview by remember { mutableStateOf(false) }
    var previewInitialPage by remember { mutableIntStateOf(0) }
    var previewStartInGrid by remember { mutableStateOf(true) }

    // Cache thumbnails for page grid display using a single PdfRenderer session
    var pageThumbnails by remember(rotateSourceFile, pageCount) {
        mutableStateOf<Map<Int, Bitmap>>(emptyMap())
    }

    LaunchedEffect(rotateSourceFile, pageCount) {
        val pdfFile = rotateSourceFile ?: return@LaunchedEffect
        if (pageCount <= 0 || !pdfFile.exists()) return@LaunchedEffect

        withContext(Dispatchers.IO) {
            val map = mutableMapOf<Int, Bitmap>()
            var pfd: android.os.ParcelFileDescriptor? = null
            var renderer: android.graphics.pdf.PdfRenderer? = null
            try {
                pfd = android.os.ParcelFileDescriptor.open(pdfFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = android.graphics.pdf.PdfRenderer(pfd)
                val density = context.resources.displayMetrics.density
                val targetWidthPx = (140 * density).toInt().coerceAtLeast(100)
                val total = renderer.pageCount.coerceAtMost(pageCount)
                for (i in 0 until total) {
                    var page: android.graphics.pdf.PdfRenderer.Page? = null
                    try {
                        page = renderer.openPage(i)
                        val pw = page.width.coerceAtLeast(1)
                        val ph = page.height.coerceAtLeast(1)
                        val targetHeightPx = (targetWidthPx * (ph.toFloat() / pw.toFloat())).toInt().coerceAtLeast(100)
                        val bmp = Bitmap.createBitmap(targetWidthPx, targetHeightPx, Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bmp)
                        canvas.drawColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        map[i] = bmp
                    } catch (_: Throwable) {
                    } finally {
                        try { page?.close() } catch (_: Throwable) {}
                    }
                }
            } catch (_: Throwable) {
            } finally {
                try { renderer?.close() } catch (_: Throwable) {}
                try { pfd?.close() } catch (_: Throwable) {}
            }
            withContext(Dispatchers.Main) {
                pageThumbnails = map
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.rotate_screen_title), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Text(stringResource(R.string.rotate_screen_subtitle), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("rotate_screen_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_cancel))
                    }
                },
                actions = {
                    if (rotateSourceFile != null || rotateSourceUri != null) {
                        OutlinedButton(
                            onClick = { filePickerLauncher.launch(arrayOf("application/pdf")) },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .height(36.dp)
                                .testTag("change_rotate_pdf_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = stringResource(R.string.action_change),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.action_change),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        FilledTonalIconButton(
                            onClick = {
                                previewInitialPage = 0
                                previewStartInGrid = true
                                showPreview = true
                            },
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(36.dp)
                                .testTag("preview_rotate_source_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.GridView,
                                contentDescription = stringResource(R.string.action_view_pdf),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp)
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (rotateSourceFile == null) {
                    PdfDocumentPickerCard(
                        selectedFile = null,
                        onFileSelected = { file ->
                            viewModel.loadPdfForRotate(android.net.Uri.fromFile(file))
                        },
                        title = stringResource(R.string.rotate_select_pdf_title),
                        subtitle = stringResource(R.string.rotate_select_pdf_desc),
                        icon = Icons.Default.RotateRight,
                        testTagPrefix = "rotate_pdf_picker",
                        modifier = Modifier.padding(top = 16.dp)
                    )
                } else {
                    // Compact Interactive PdfRenderer Inspector Banner
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                previewInitialPage = 0
                                previewStartInGrid = true
                                showPreview = true
                            }
                            .testTag("builtin_pdf_preview_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GridView,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = rotateSourceFile?.name ?: stringResource(R.string.preview_document_live),
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "$pageCount pages • ${PdfEngine.formatFileSize(rotateSourceFile?.length() ?: 0L)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            FilledTonalButton(
                                onClick = {
                                    previewInitialPage = 0
                                    previewStartInGrid = true
                                    showPreview = true
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("fullscreen_preview_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GridView,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.action_preview),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Quick Batch Rotation & Deletion Action Bar
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.rotate_selected_count, selectedPages.size, pageCount),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f, fill = false),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = { viewModel.selectAllRotatePages() },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 32.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.rotate_select_all),
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                    TextButton(
                                        onClick = { viewModel.clearRotatePageSelection() },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 32.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.rotate_clear_selection),
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }

                            // 90, 180, 270 degree rotation + delete buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { viewModel.rotateSelectedPagesBy(90) },
                                    enabled = selectedPages.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("rotate_90_button"),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.RotateRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("+90°", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1, softWrap = false)
                                }

                                Button(
                                    onClick = { viewModel.rotateSelectedPagesBy(180) },
                                    enabled = selectedPages.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("rotate_180_button"),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.ScreenRotation, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("+180°", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1, softWrap = false)
                                }

                                Button(
                                    onClick = { viewModel.rotateSelectedPagesBy(270) },
                                    enabled = selectedPages.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("rotate_270_button"),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.RotateLeft, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("-90°", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1, softWrap = false)
                                }

                                Button(
                                    onClick = { viewModel.deleteSelectedRotatePages() },
                                    enabled = selectedPages.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                    modifier = Modifier
                                        .testTag("rotate_delete_selected_button"),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = stringResource(R.string.action_delete),
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                if (pageRotations.isNotEmpty() && pageRotations.any { it.value % 360 != 0 }) {
                                    IconButton(
                                        onClick = { viewModel.resetRotatePageRotations() },
                                        modifier = Modifier
                                            .size(34.dp)
                                            .testTag("reset_rotations_button")
                                    ) {
                                        Icon(Icons.Default.RestartAlt, contentDescription = stringResource(R.string.rotate_reset_rotations), tint = MaterialTheme.colorScheme.error)
                                    }
                                }

                                if (deletedPages.isNotEmpty()) {
                                    IconButton(
                                        onClick = { viewModel.restoreAllDeletedRotatePages() },
                                        modifier = Modifier
                                            .size(34.dp)
                                            .testTag("restore_deleted_rotate_pages_button")
                                    ) {
                                        Icon(Icons.Default.RestoreFromTrash, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }

                    // Page Thumbnails Grid with orientation & deletion indicators
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 110.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        itemsIndexed(List(pageCount) { it }) { pageIdx, _ ->
                            val isSelected = selectedPages.contains(pageIdx)
                            val isDeleted = deletedPages.contains(pageIdx)
                            val angle = pageRotations[pageIdx] ?: 0
                            val bmp = pageThumbnails[pageIdx]

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = when {
                                        isDeleted -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                                        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                        else -> MaterialTheme.colorScheme.surface
                                    }
                                ),
                                border = BorderStroke(
                                    if (isSelected || isDeleted) 2.dp else 1.dp,
                                    when {
                                        isDeleted -> MaterialTheme.colorScheme.error
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.outlineVariant
                                    }
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(145.dp)
                                    .clickable { viewModel.toggleSelectRotatePage(pageIdx) }
                                    .testTag("page_rotate_item_$pageIdx")
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    // Thumbnail Image rotated according to angle
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "Page ${pageIdx + 1}",
                                            contentScale = ContentScale.Fit,
                                            alpha = if (isDeleted) 0.35f else 1f,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(12.dp)
                                                .rotate(angle.toFloat())
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Page ${pageIdx + 1}",
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // Checkbox selection badge
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { viewModel.toggleSelectRotatePage(pageIdx) },
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(2.dp)
                                    )

                                    // Angle degree badge
                                    Surface(
                                        color = if (angle % 360 != 0) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                    ) {
                                        Text(
                                            text = "${angle % 360}°",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    if (isDeleted) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.align(Alignment.Center)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.action_delete).uppercase(),
                                                color = MaterialTheme.colorScheme.onError,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }

                                    // Bottom action row: Inspect, Delete/Restore, Rotate
                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .padding(horizontal = 6.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            shape = CircleShape,
                                            modifier = Modifier
                                                .clickable {
                                                    previewInitialPage = pageIdx
                                                    previewStartInGrid = false
                                                    showPreview = true
                                                }
                                                .testTag("inspect_rotate_page_$pageIdx")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ZoomIn,
                                                contentDescription = stringResource(R.string.action_preview),
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier
                                                    .padding(5.dp)
                                                    .size(15.dp)
                                            )
                                        }

                                        Surface(
                                            color = if (isDeleted) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
                                            shape = CircleShape,
                                            modifier = Modifier
                                                .clickable { viewModel.toggleDeleteRotatePage(pageIdx) }
                                                .testTag("delete_rotate_page_$pageIdx")
                                        ) {
                                            Icon(
                                                imageVector = if (isDeleted) Icons.Default.RestoreFromTrash else Icons.Default.DeleteOutline,
                                                contentDescription = stringResource(R.string.action_delete),
                                                tint = if (isDeleted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier
                                                    .padding(5.dp)
                                                    .size(15.dp)
                                            )
                                        }

                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = CircleShape,
                                            modifier = Modifier
                                                .clickable { viewModel.rotateSinglePage(pageIdx, 90) }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.RotateRight,
                                                contentDescription = "Rotate page ${pageIdx + 1}",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier
                                                    .padding(5.dp)
                                                    .size(15.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Output File Settings & Save Action
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = outputName,
                                onValueChange = { viewModel.setRotateOutputName(it) },
                                label = { Text(stringResource(R.string.rotate_output_name_label)) },
                                leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("rotate_output_name_field")
                            )

                            Button(
                                onClick = { viewModel.executePdfRotate() },
                                enabled = !isProcessing,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("save_rotated_pdf_button")
                            ) {
                                if (isProcessing) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(processingMessage.ifEmpty { stringResource(R.string.action_processing) }, fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.rotate_save_button), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPreview && (rotateSourceFile != null || rotateSourceUri != null)) {
        PdfPreviewScreenDialog(
            file = rotateSourceFile,
            uri = rotateSourceUri,
            documentTitle = rotateSourceFile?.name ?: stringResource(R.string.rotate_screen_title),
            operationMode = PdfPreviewOperationMode.ROTATE,
            initialPageIndex = previewInitialPage,
            initialGridMode = previewStartInGrid,
            selectedPages = selectedPages,
            onTogglePageSelection = { viewModel.toggleSelectRotatePage(it) },
            onSelectAllPages = { viewModel.selectAllRotatePages() },
            onClearPageSelection = { viewModel.clearRotatePageSelection() },
            pageRotations = pageRotations,
            onRotateSinglePage = { pageIdx, deg -> viewModel.rotateSinglePage(pageIdx, deg) },
            onRotateSelectedPages = { deg -> viewModel.rotateSelectedPagesBy(deg) },
            onResetRotations = { viewModel.resetRotatePageRotations() },
            deletedPages = deletedPages,
            onToggleDeletePage = { viewModel.toggleDeleteRotatePage(it) },
            onDeleteSelectedPages = { viewModel.deleteSelectedRotatePages() },
            onRestoreAllDeletedPages = { viewModel.restoreAllDeletedRotatePages() },
            onExecutePrimaryAction = { viewModel.executePdfRotate() },
            onDismissRequest = { showPreview = false }
        )
    }
}
