package com.example.ui.components

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.data.local.RecentFileEntity
import com.example.data.pdf.PdfEngine
import com.example.ui.MainViewModel
import java.io.File

import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentFilesSection(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val recentFiles by viewModel.recentFilesState.collectAsState()
    val searchQuery by viewModel.recentSearchQuery.collectAsState()
    val selectedPaths by viewModel.selectedRecentPaths.collectAsState()
    val isMultiSelectMode by viewModel.isRecentMultiSelectMode.collectAsState()

    if (recentFiles.isEmpty() && searchQuery.isBlank()) {
        return
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .testTag("recent_files_section")
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            // Header Row with Title & Search / Multi-select Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = if (isMultiSelectMode) {
                                stringResource(R.string.recent_selected_count, selectedPaths.size, recentFiles.size)
                            } else {
                                stringResource(R.string.recent_history_title)
                            },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.recent_history_subtitle),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isMultiSelectMode) {
                        IconButton(
                            onClick = { viewModel.selectAllRecentFiles(recentFiles) },
                            modifier = Modifier.size(32.dp).testTag("select_all_recent_button")
                        ) {
                            Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.action_select_all), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }

                        if (selectedPaths.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.deleteSelectedRecentFiles() },
                                modifier = Modifier.size(32.dp).testTag("delete_selected_recent_button")
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            }
                        }

                        IconButton(
                            onClick = { viewModel.toggleRecentMultiSelectMode() },
                            modifier = Modifier.size(32.dp).testTag("exit_multi_select_button")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                    } else {
                        TextButton(
                            onClick = { viewModel.toggleRecentMultiSelectMode() },
                            modifier = Modifier
                                .defaultMinSize(minWidth = 1.dp, minHeight = 32.dp)
                                .testTag("enable_multi_select_button"),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.action_select),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        TextButton(
                            onClick = { viewModel.clearRecentFiles() },
                            modifier = Modifier
                                .defaultMinSize(minWidth = 1.dp, minHeight = 32.dp)
                                .testTag("clear_recent_files_button"),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.action_clear_all),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Search Bar for filtering recent files by name/metadata
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setRecentSearchQuery(it) },
                placeholder = { Text(stringResource(R.string.recent_search_hint), fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                trailingIcon = if (searchQuery.isNotBlank()) {
                    {
                        IconButton(onClick = { viewModel.setRecentSearchQuery("") }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search", modifier = Modifier.size(14.dp))
                        }
                    }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .height(48.dp)
                    .testTag("recent_files_search_bar")
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (recentFiles.isEmpty()) {
                Text(
                    text = if (searchQuery.isNotBlank()) stringResource(R.string.recent_no_match, searchQuery) else stringResource(R.string.hub_empty_recent),
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(recentFiles, key = { it.filePath }) { item ->
                        RecentFileCard(
                            item = item,
                            isMultiSelectMode = isMultiSelectMode,
                            isSelected = selectedPaths.contains(item.filePath),
                            onToggleSelection = { viewModel.toggleRecentFileSelection(item.filePath) },
                            onInspectMetadata = {
                                if (isMultiSelectMode) {
                                    viewModel.toggleRecentFileSelection(item.filePath)
                                } else {
                                    viewModel.inspectFileMetadata(item.filePath)
                                }
                            },
                            onOpenViewer = { viewModel.openInViewer(item.filePath) },
                            onRemove = { viewModel.removeRecentFile(item.filePath) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PdfThumbnailPreview(
    filePath: String,
    modifier: Modifier = Modifier
) {
    var thumbnailBitmap by remember(filePath) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isLoading by remember(filePath) { mutableStateOf(true) }

    LaunchedEffect(filePath) {
        isLoading = true
        val file = File(filePath)
        if (file.exists()) {
            thumbnailBitmap = PdfEngine.generateFirstPageThumbnail(file, widthPx = 220)
        } else {
            thumbnailBitmap = null
        }
        isLoading = false
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        } else if (thumbnailBitmap != null) {
            Image(
                bitmap = thumbnailBitmap!!.asImageBitmap(),
                contentDescription = "PDF Page 1 Preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
            )

            // Page 1 Tag
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                shape = RoundedCornerShape(topStart = 6.dp),
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                Text(
                    text = "Page 1",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "No Preview",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun RecentFileCard(
    item: RecentFileEntity,
    isMultiSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelection: () -> Unit = {},
    onInspectMetadata: () -> Unit,
    onOpenViewer: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fileExists = File(item.filePath).exists()
    val timeAgo = DateUtils.getRelativeTimeSpanString(
        item.lastAccessedTimestamp,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS
    ).toString()

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .width(200.dp)
            .clickable(enabled = fileExists) {
                if (isMultiSelectMode) {
                    onToggleSelection()
                } else {
                    onInspectMetadata()
                }
            }
            .testTag("recent_file_card_${item.filePath.hashCode()}")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header: Title & Remove/Checkbox
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isMultiSelectMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleSelection() },
                            modifier = Modifier.size(24.dp).testTag("recent_checkbox_${item.filePath.hashCode()}")
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    } else {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = if (fileExists) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = item.fileName,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!isMultiSelectMode) {
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier
                            .size(22.dp)
                            .testTag("remove_recent_${item.filePath.hashCode()}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Page 1 Thumbnail Preview Generator
            PdfThumbnailPreview(
                filePath = item.filePath,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Footer info and action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (item.fileSizeFormatted.isNotBlank()) {
                        Text(
                            text = if (item.pageCount > 0) "${item.pageCount} pgs • ${item.fileSizeFormatted}" else item.fileSizeFormatted,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = timeAgo,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (fileExists) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onInspectMetadata,
                            modifier = Modifier
                                .size(24.dp)
                                .testTag("inspect_recent_${item.filePath.hashCode()}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Inspect Document Metadata",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        IconButton(
                            onClick = onOpenViewer,
                            modifier = Modifier
                                .size(24.dp)
                                .testTag("view_recent_${item.filePath.hashCode()}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = "Quick View PDF",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
