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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.pdf.PdfEngine
import com.example.ui.MainViewModel
import com.example.ui.SplitMode
import com.example.ui.components.PdfBoxPreviewDialog
import com.example.ui.components.PdfDocumentPickerCard
import com.example.ui.components.PdfDocumentPreviewCard

@Composable
fun SplitScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val sourceUri by viewModel.splitSourceUri.collectAsState()
    val sourceFile by viewModel.splitSourceFile.collectAsState()
    val totalPages by viewModel.splitTotalPages.collectAsState()
    val selectedPages by viewModel.splitSelectedPages.collectAsState()
    val thumbnails by viewModel.splitThumbnails.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val processingMessage by viewModel.processingMessage.collectAsState()
    val splitMode by viewModel.splitMode.collectAsState()

    var showPreview by remember { mutableStateOf(false) }

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
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.tool_split_title),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = sourceFile?.let { "${it.name} (${totalPages} pages • ${PdfEngine.formatFileSize(it.length())})" }
                                    ?: stringResource(R.string.tool_split_desc),
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (sourceUri != null) {
                                IconButton(
                                    onClick = { showPreview = true },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .testTag("preview_split_source_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Visibility,
                                        contentDescription = stringResource(R.string.action_view_pdf),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    // Preset Selection Filters
                    if (totalPages > 0) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                                Text("Odd Pages", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
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
                                Text("Even Pages", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        var customRangeText by remember { mutableStateOf("") }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = customRangeText,
                                onValueChange = { customRangeText = it },
                                placeholder = { Text("e.g. 1-3, 5, 8-10", fontSize = 12.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("custom_split_range_input")
                            )

                            Button(
                                onClick = {
                                    viewModel.selectPageRange(customRangeText)
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(50.dp).testTag("apply_split_range_button")
                            ) {
                                Text("Apply Range", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Split Mode",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))

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
                                    .padding(vertical = 10.dp)
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
                                    .padding(vertical = 10.dp)
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
                    PdfDocumentPreviewCard(
                        file = sourceFile,
                        uri = sourceUri,
                        title = "Document Live Preview",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )

                    // Page Selection Grid
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 130.dp),
                        contentPadding = PaddingValues(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        items(totalPages) { pageIdx ->
                            val isSelected = selectedPages.contains(pageIdx)
                            val bmp = thumbnails.getOrNull(pageIdx)

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
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
                                            text = "Page ${pageIdx + 1}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )

                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { viewModel.toggleSplitPageSelection(pageIdx) },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = MaterialTheme.colorScheme.primary,
                                                checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                                                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    }

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
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(imageVector = Icons.Default.Description, contentDescription = null, tint = Color.Gray)
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
                            Column {
                                Text(
                                    text = "${selectedPages.size} of $totalPages pages selected",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                )
                                Text(
                                    text = if (splitMode == SplitMode.COMBINE_SELECTED) {
                                        "Creates 1 extracted PDF document"
                                    } else {
                                        "Creates ${selectedPages.size} separate PDF documents"
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPreview && sourceUri != null) {
        PdfBoxPreviewDialog(
            pdfUri = sourceUri!!,
            documentTitle = sourceFile?.name ?: "Split Source PDF",
            onDismissRequest = { showPreview = false }
        )
    }
}
