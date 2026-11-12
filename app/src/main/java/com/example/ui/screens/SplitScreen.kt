package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.pdf.PdfEngine
import com.example.ui.MainViewModel
import com.example.ui.SplitMode
import com.example.ui.components.PdfDocumentPickerCard
import com.example.ui.components.PdfDocumentPreviewCard
import java.io.File

@Composable
fun SplitScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val sourceUri by viewModel.splitSourceUri.collectAsState()
    val sourceFile by viewModel.splitSourceFile.collectAsState()
    val totalPages by viewModel.splitTotalPages.collectAsState()
    val selectedPages by viewModel.splitSelectedPages.collectAsState()
    val pageRotations by viewModel.splitPageRotations.collectAsState()
    val deletedPages by viewModel.splitDeletedPages.collectAsState()
    val thumbnails by viewModel.splitThumbnails.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val processingMessage by viewModel.processingMessage.collectAsState()
    val splitMode by viewModel.splitMode.collectAsState()

    var showPreview by remember { mutableStateOf(false) }
    var previewInitialPage by remember { mutableIntStateOf(0) }
    var previewStartInGrid by remember { mutableStateOf(true) }
    var showAdvancedControls by remember { mutableStateOf(true) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.loadPdfForSplit(uri)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
                .align(Alignment.TopCenter)
        ) {
            // Document Selector Header
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { viewModel.setActiveTool(null) },
                                modifier = Modifier.size(36.dp).testTag("split_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.action_cancel),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.tool_split_title),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = sourceFile?.let { "${it.name} ($totalPages pages • ${PdfEngine.formatFileSize(it.length())})" }
                                        ?: stringResource(R.string.tool_split_desc),
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (sourceUri != null || sourceFile != null) {
                                OutlinedButton(
                                    onClick = { filePickerLauncher.launch(arrayOf("application/pdf")) },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .height(36.dp)
                                        .testTag("change_split_pdf_button")
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
                                        .size(36.dp)
                                        .testTag("preview_split_source_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GridView,
                                        contentDescription = stringResource(R.string.action_view_pdf),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { showAdvancedControls = !showAdvancedControls },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("toggle_split_controls_button")
                                ) {
                                    Icon(
                                        imageVector = if (showAdvancedControls) Icons.Default.ExpandLess else Icons.Default.Tune,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Preset Selection Filters & Quick Rotate/Delete Actions
                    if (totalPages > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .clickable { viewModel.selectAllSplitPages() }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("select_all_split_pages"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(stringResource(R.string.action_select_all), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .clickable { viewModel.clearSplitPageSelection() }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("clear_split_pages"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(stringResource(R.string.action_clear), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .clickable { viewModel.selectOddSplitPages() }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("select_odd_split_pages"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(stringResource(R.string.split_odd_pages), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .clickable { viewModel.selectEvenSplitPages() }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("select_even_split_pages"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(stringResource(R.string.split_even_pages), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                            }

                            if (selectedPages.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .clickable { viewModel.rotateSelectedSplitPages(90) }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                        .testTag("rotate_selected_split_pages"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("+90°", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.errorContainer)
                                        .clickable { viewModel.deleteSelectedSplitPages() }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                        .testTag("delete_selected_split_pages"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = stringResource(R.string.action_delete),
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(visible = showAdvancedControls) {
                            Column {
                                Spacer(modifier = Modifier.height(8.dp))

                                var customRangeText by remember { mutableStateOf("") }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = customRangeText,
                                        onValueChange = { customRangeText = it },
                                        placeholder = { Text(stringResource(R.string.split_page_range_hint), fontSize = 12.sp) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("custom_split_range_input")
                                    )

                                    Button(
                                        onClick = {
                                            viewModel.selectPageRange(customRangeText)
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.height(48.dp).testTag("apply_split_range_button")
                                    ) {
                                        Text(stringResource(R.string.split_apply_range), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val isCombine = splitMode == SplitMode.COMBINE_SELECTED
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isCombine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer)
                                            .border(
                                                width = 1.dp,
                                                color = if (isCombine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable { viewModel.setSplitMode(SplitMode.COMBINE_SELECTED) }
                                            .padding(vertical = 8.dp, horizontal = 8.dp)
                                            .testTag("split_mode_combine"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "Combine Selected",
                                                fontSize = 12.sp,
                                                color = if (isCombine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Extract to 1 single PDF",
                                                fontSize = 10.sp,
                                                color = if (isCombine) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    val isSeparate = splitMode == SplitMode.SEPARATE_FILES
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSeparate) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer)
                                            .border(
                                                width = 1.dp,
                                                color = if (isSeparate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable { viewModel.setSplitMode(SplitMode.SEPARATE_FILES) }
                                            .padding(vertical = 8.dp, horizontal = 8.dp)
                                            .testTag("split_mode_separate"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "Individual Files",
                                                fontSize = 12.sp,
                                                color = if (isSeparate) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Extract each to its own PDF",
                                                fontSize = 10.sp,
                                                color = if (isSeparate) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Loading indicator
            AnimatedVisibility(visible = isProcessing, enter = fadeIn(), exit = fadeOut()) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = processingMessage, color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Empty state or Page Selection Grid
            if (sourceFile == null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    PdfDocumentPickerCard(
                        selectedFile = null,
                        onFileSelected = { file ->
                            viewModel.loadPdfForSplit(android.net.Uri.fromFile(file))
                        },
                        title = stringResource(R.string.tool_split_title),
                        subtitle = stringResource(R.string.tool_split_desc),
                        icon = Icons.Default.ContentCut,
                        testTagPrefix = "split_pdf_picker"
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    // Compact Interactive PdfRenderer Inspector Banner
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp)
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
                                        text = stringResource(R.string.preview_document_live),
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "$totalPages pages • Tap for thumbnail grid, rotate, or delete",
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

                    // Page Selection Grid
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 130.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        items(totalPages) { pageIdx ->
                            val isSelected = selectedPages.contains(pageIdx)
                            val isDeleted = deletedPages.contains(pageIdx)
                            val angle = pageRotations[pageIdx] ?: 0
                            val bmp = thumbnails.getOrNull(pageIdx)

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = when {
                                        isDeleted -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                                        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                        else -> MaterialTheme.colorScheme.surface
                                    }
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = if (isSelected || isDeleted) 2.dp else 1.dp,
                                    color = when {
                                        isDeleted -> MaterialTheme.colorScheme.error
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.outlineVariant
                                    }
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleSplitPageSelection(pageIdx) }
                                    .testTag("split_page_item_$pageIdx")
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "P.${pageIdx + 1}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when {
                                                isDeleted -> MaterialTheme.colorScheme.error
                                                isSelected -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.onSurface
                                            }
                                        )

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = { viewModel.rotateSingleSplitPage(pageIdx, 90) },
                                                modifier = Modifier
                                                    .size(26.dp)
                                                    .testTag("split_rotate_page_$pageIdx")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.RotateRight,
                                                    contentDescription = stringResource(R.string.tool_rotate_title),
                                                    tint = if (angle % 360 != 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }

                                            IconButton(
                                                onClick = { viewModel.toggleDeleteSplitPage(pageIdx) },
                                                modifier = Modifier
                                                    .size(26.dp)
                                                    .testTag("split_delete_page_$pageIdx")
                                            ) {
                                                Icon(
                                                    imageVector = if (isDeleted) Icons.Default.RestoreFromTrash else Icons.Default.DeleteOutline,
                                                    contentDescription = stringResource(R.string.action_delete),
                                                    tint = if (isDeleted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    previewInitialPage = pageIdx
                                                    previewStartInGrid = false
                                                    showPreview = true
                                                },
                                                modifier = Modifier
                                                    .size(26.dp)
                                                    .testTag("split_inspect_page_$pageIdx")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ZoomIn,
                                                    contentDescription = stringResource(R.string.action_preview),
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }

                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = { viewModel.toggleSplitPageSelection(pageIdx) },
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = MaterialTheme.colorScheme.primary,
                                                    checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                                                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                ),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(0.72f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color.White),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (bmp != null) {
                                            Image(
                                                bitmap = bmp.asImageBitmap(),
                                                contentDescription = "Page ${pageIdx + 1}",
                                                contentScale = ContentScale.Fit,
                                                alpha = if (isDeleted) 0.35f else 1f,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .rotate((angle % 360).toFloat())
                                            )
                                        } else {
                                            Icon(imageVector = Icons.Default.Description, contentDescription = null, tint = Color.Gray)
                                        }

                                        if (angle % 360 != 0) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(4.dp),
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(4.dp)
                                            ) {
                                                Text(
                                                    text = "${angle % 360}°",
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }

                                        if (isDeleted) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
                                                shape = RoundedCornerShape(6.dp)
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
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Action Bar
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .padding(end = 8.dp)
                            ) {
                                Text(
                                    text = "${selectedPages.size} of $totalPages pages selected",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (splitMode == SplitMode.COMBINE_SELECTED) {
                                        "Creates 1 extracted PDF document"
                                    } else {
                                        "Creates ${selectedPages.size} separate PDF documents"
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Button(
                                onClick = { viewModel.executeSplit() },
                                enabled = !isProcessing && selectedPages.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(48.dp).testTag("execute_split_button")
                            ) {
                                Icon(imageVector = Icons.Default.ContentCut, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (splitMode == SplitMode.COMBINE_SELECTED) "Export Combined PDF" else "Export Individual Pages",
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPreview && (sourceFile != null || sourceUri != null)) {
        PdfPreviewScreenDialog(
            file = sourceFile,
            uri = sourceUri,
            documentTitle = sourceFile?.name ?: stringResource(R.string.tool_split_title),
            operationMode = PdfPreviewOperationMode.SPLIT,
            initialPageIndex = previewInitialPage,
            initialGridMode = previewStartInGrid,
            selectedPages = selectedPages,
            onTogglePageSelection = { viewModel.toggleSplitPageSelection(it) },
            onSelectAllPages = { viewModel.selectAllSplitPages() },
            onClearPageSelection = { viewModel.clearSplitPageSelection() },
            onSelectOddPages = { viewModel.selectOddSplitPages() },
            onSelectEvenPages = { viewModel.selectEvenSplitPages() },
            pageRotations = pageRotations,
            onRotateSinglePage = { pageIdx, deg -> viewModel.rotateSingleSplitPage(pageIdx, deg) },
            onRotateSelectedPages = { deg -> viewModel.rotateSelectedSplitPages(deg) },
            onResetRotations = { viewModel.resetSplitPageRotations() },
            deletedPages = deletedPages,
            onToggleDeletePage = { viewModel.toggleDeleteSplitPage(it) },
            onDeleteSelectedPages = { viewModel.deleteSelectedSplitPages() },
            onRestoreAllDeletedPages = { viewModel.restoreAllDeletedSplitPages() },
            onExecutePrimaryAction = { viewModel.executeSplit() },
            onDismissRequest = { showPreview = false }
        )
    }
}
