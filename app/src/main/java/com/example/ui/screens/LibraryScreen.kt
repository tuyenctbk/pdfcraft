package com.example.ui.screens
import com.example.R
import androidx.compose.ui.res.stringResource

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.local.PdfOperationType
import com.example.data.local.PdfProjectEntity
import com.example.data.pdf.PdfEngine
import com.example.ui.MainViewModel
import com.example.ui.components.RecentFilesSection
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton

import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults

import androidx.compose.material3.Checkbox
import androidx.compose.material.icons.filled.DriveFileRenameOutline

enum class LibrarySortOption(val title: String) {
    DATE_CREATED("Date Created"),
    FILE_SIZE("File Size"),
    ALPHABETICAL("Alphabetical")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val historyList by viewModel.historyState.collectAsState()
    val selectedProjectIds by viewModel.selectedLibraryProjectIds.collectAsState()
    val isMultiSelectMode by viewModel.isLibraryMultiSelectMode.collectAsState()

    var filterType by remember { mutableStateOf<PdfOperationType?>(null) }
    var showOnlyFavorites by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(LibrarySortOption.DATE_CREATED) }
    var isAscending by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    var projectToDelete by remember { mutableStateOf<PdfProjectEntity?>(null) }
    var projectToRename by remember { mutableStateOf<PdfProjectEntity?>(null) }
    var projectContextMenu by remember { mutableStateOf<PdfProjectEntity?>(null) }
    var newFileNameInput by remember { mutableStateOf("") }

    var showBulkRenameDialog by remember { mutableStateOf(false) }
    var bulkRenamePatternInput by remember { mutableStateOf("Document_{N}") }

    val filteredList = historyList.filter { project ->
        val matchesSearch = searchQuery.isBlank() ||
                project.title.contains(searchQuery, ignoreCase = true) ||
                project.outputFilePath.contains(searchQuery, ignoreCase = true) ||
                project.summaryDetails.contains(searchQuery, ignoreCase = true)
        val matchesType = (filterType == null || project.operationType == filterType)
        val matchesFav = (!showOnlyFavorites || project.isFavorite)

        matchesSearch && matchesType && matchesFav
    }

    val sortedList = remember(filteredList, sortOption, isAscending) {
        when (sortOption) {
            LibrarySortOption.DATE_CREATED -> if (isAscending) filteredList.sortedBy { it.timestamp } else filteredList.sortedByDescending { it.timestamp }
            LibrarySortOption.FILE_SIZE -> if (isAscending) filteredList.sortedBy { it.resultSizeBytes } else filteredList.sortedByDescending { it.resultSizeBytes }
            LibrarySortOption.ALPHABETICAL -> if (isAscending) filteredList.sortedBy { it.title.lowercase() } else filteredList.sortedByDescending { it.title.lowercase() }
        }
    }

    val totalSavedBytes = historyList.sumOf { (it.originalSizeBytes - it.resultSizeBytes).coerceAtLeast(0) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isWide = maxWidth > 600.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
                .align(Alignment.TopCenter)
        ) {
            // Statistics Banner
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Project Library & Activity Log",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(stringResource(R.string.library_total_processed), fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text("${historyList.size} Files", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(stringResource(R.string.library_space_saved), fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text(PdfEngine.formatFileSize(totalSavedBytes), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(stringResource(R.string.library_cloud_cost), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.library_cloud_cost_val), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Real-Time Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.library_search_by_title), fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(20.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search", modifier = Modifier.size(20.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("library_search_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                RecentFilesSection(viewModel = viewModel)

                Spacer(modifier = Modifier.height(12.dp))

                // Multi-Select & Bulk Rename Toolbar Bar
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isMultiSelectMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isMultiSelectMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { viewModel.toggleLibraryMultiSelectMode() },
                                modifier = Modifier.size(28.dp).testTag("toggle_library_multiselect")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DriveFileRenameOutline,
                                    contentDescription = "Multi-Select",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isMultiSelectMode) "${selectedProjectIds.size} Selected" else "Bulk Rename Mode",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isMultiSelectMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (isMultiSelectMode) {
                                TextButton(
                                    onClick = { viewModel.selectAllLibraryProjects(sortedList) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(stringResource(R.string.action_select_all), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = { showBulkRenameDialog = true },
                                    enabled = selectedProjectIds.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(32.dp).testTag("trigger_bulk_rename_button")
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.library_bulk_rename, selectedProjectIds.size), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                IconButton(
                                    onClick = { viewModel.clearLibraryProjectSelection() },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                }
                            } else {
                                TextButton(
                                    onClick = { viewModel.toggleLibraryMultiSelectMode() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.testTag("enter_bulk_rename_mode")
                                ) {
                                    Text(stringResource(R.string.library_select_files_to_rename), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Sorting Menu & Filter Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Filters & Sorting",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    )

                    Box {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .clickable { showSortMenu = true }
                                .testTag("sort_menu_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sort,
                                    contentDescription = "Sort Options",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${sortOption.title}${if (isAscending) " ↑" else " ↓"}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            LibrarySortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = option.title + if (sortOption == option) (if (isAscending) " (Ascending)" else " (Descending)") else "",
                                            fontWeight = if (sortOption == option) FontWeight.Bold else FontWeight.Normal,
                                            color = if (sortOption == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        if (sortOption == option) {
                                            isAscending = !isAscending
                                        } else {
                                            sortOption = option
                                            isAscending = false
                                        }
                                        showSortMenu = false
                                    },
                                    modifier = Modifier.testTag("sort_option_${option.name}")
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Filter Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (filterType == null && !showOnlyFavorites) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                filterType = null
                                showOnlyFavorites = false
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("filter_all"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("All", fontSize = 11.sp, color = if (filterType == null && !showOnlyFavorites) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (showOnlyFavorites) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { showOnlyFavorites = !showOnlyFavorites }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("filter_favorites"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Favorites", fontSize = 11.sp, color = if (showOnlyFavorites) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    }

                    PdfOperationType.entries.forEach { opType ->
                        val isSel = filterType == opType && !showOnlyFavorites
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    filterType = opType
                                    showOnlyFavorites = false
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .testTag("filter_${opType.name}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(opType.name, fontSize = 11.sp, color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // List View or Empty State
        if (sortedList.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.FolderSpecial, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (searchQuery.isNotBlank()) "No Matching Documents" else "No Projects Recorded",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = if (searchQuery.isNotBlank()) "No PDF documents found matching '$searchQuery'. Try clearing your search query." else "Your processed documents, merges, splits, and extractions will be securely stored here on local Room DB.",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                items(sortedList, key = { it.id }) { item ->
                    val isSelected = selectedProjectIds.contains(item.id)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItemPlacement()
                            .combinedClickable(
                                onClick = {
                                    if (isMultiSelectMode) {
                                        viewModel.toggleLibraryProjectSelection(item.id)
                                    } else {
                                        viewModel.openInViewer(item.outputFilePath)
                                    }
                                },
                                onLongClick = {
                                    if (!isMultiSelectMode) {
                                        viewModel.toggleLibraryMultiSelectMode()
                                        viewModel.toggleLibraryProjectSelection(item.id)
                                    } else {
                                        projectContextMenu = item
                                    }
                                }
                            )
                            .testTag("history_item_${item.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isMultiSelectMode) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { viewModel.toggleLibraryProjectSelection(item.id) },
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }

                            // Icon Badge
                            val iconVal = when (item.operationType) {
                                PdfOperationType.MERGE -> Icons.Default.Merge
                                PdfOperationType.SPLIT -> Icons.Default.ContentCut
                                PdfOperationType.COMPRESS -> Icons.Default.Compress
                                PdfOperationType.EXTRACT -> Icons.Default.Image
                                PdfOperationType.ENCRYPT -> Icons.Default.Lock
                                PdfOperationType.WATERMARK -> Icons.Default.TextFields
                                PdfOperationType.SIGN -> Icons.Default.Draw
                                PdfOperationType.METADATA -> Icons.Default.Edit
                                PdfOperationType.ROTATE -> Icons.Default.Refresh
                                PdfOperationType.IMAGE_TO_PDF -> Icons.Default.Image
                                PdfOperationType.PAGE_NUMBER -> Icons.Default.TextFields
                                PdfOperationType.TEXT_TO_PDF -> Icons.Default.TextFields
                                PdfOperationType.PDF_TO_TEXT -> Icons.Default.TextFields
                                PdfOperationType.PDF_TO_IMAGES -> Icons.Default.Image
                                PdfOperationType.HTML_TO_PDF -> Icons.Default.Edit
                            }
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(imageVector = iconVal, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.summaryDetails.ifEmpty { "${item.pageCount} pages • ${PdfEngine.formatFileSize(item.resultSizeBytes)}" },
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.US).format(Date(item.timestamp)),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                IconButton(
                                    onClick = { viewModel.toggleFavorite(item.id, item.isFavorite) },
                                    modifier = Modifier.testTag("fav_${item.id}")
                                ) {
                                    Icon(
                                        imageVector = if (item.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                        contentDescription = "Favorite",
                                        tint = if (item.isFavorite) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.openInViewer(item.outputFilePath) },
                                    modifier = Modifier.testTag("open_viewer_${item.id}")
                                ) {
                                    Icon(imageVector = Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "View PDF", tint = MaterialTheme.colorScheme.primary)
                                }

                                IconButton(
                                    onClick = {
                                        val file = File(item.outputFilePath)
                                        if (file.exists()) {
                                            try {
                                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = when (item.operationType) {
                                                        PdfOperationType.EXTRACT, PdfOperationType.PDF_TO_IMAGES -> "image/*"
                                                        PdfOperationType.PDF_TO_TEXT -> "text/plain"
                                                        else -> "application/pdf"
                                                    }
                                                    putExtra(Intent.EXTRA_STREAM, uri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "Share ${file.name}"))
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                    },
                                    modifier = Modifier.testTag("share_${item.id}")
                                ) {
                                    Icon(imageVector = Icons.Default.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                IconButton(
                                    onClick = { projectToDelete = item },
                                    modifier = Modifier.testTag("delete_item_${item.id}")
                                ) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    projectToDelete?.let { project ->
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.library_delete_dialog_title)) },
            text = {
                Text("Are you sure you want to permanently delete '${project.title}' from storage? This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteProjectWithFile(project.id, project.outputFilePath)
                        projectToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.library_delete_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rename File Dialog
    projectToRename?.let { project ->
        AlertDialog(
            onDismissRequest = { projectToRename = null },
            icon = { Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.library_rename_dialog_title)) },
            text = {
                Column {
                    Text(
                        text = "Enter a new filename for this processed PDF document:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newFileNameInput,
                        onValueChange = { newFileNameInput = it },
                        label = { Text(stringResource(R.string.library_new_file_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("rename_input_field")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFileNameInput.isNotBlank()) {
                            viewModel.renameProjectFile(project.id, project.outputFilePath, newFileNameInput.trim())
                            projectToRename = null
                        }
                    },
                    enabled = newFileNameInput.isNotBlank()
                ) {
                    Text(stringResource(R.string.action_rename))
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Long-Press Context Menu Dialog
    projectContextMenu?.let { project ->
        AlertDialog(
            onDismissRequest = { projectContextMenu = null },
            title = { Text(project.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            val p = project
                            projectContextMenu = null
                            newFileNameInput = File(p.outputFilePath).nameWithoutExtension
                            projectToRename = p
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(stringResource(R.string.library_menu_rename))
                        }
                    }

                    TextButton(
                        onClick = {
                            val p = project
                            projectContextMenu = null
                            viewModel.openInViewer(p.outputFilePath)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(stringResource(R.string.library_menu_quick_view))
                        }
                    }

                    TextButton(
                        onClick = {
                            val p = project
                            projectContextMenu = null
                            viewModel.inspectFileMetadata(p.outputFilePath)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(stringResource(R.string.library_menu_metadata))
                        }
                    }

                    TextButton(
                        onClick = {
                            val p = project
                            projectContextMenu = null
                            val file = File(p.outputFilePath)
                            if (file.exists()) {
                                try {
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = when (p.operationType) {
                                            PdfOperationType.EXTRACT, PdfOperationType.PDF_TO_IMAGES -> "image/*"
                                            PdfOperationType.PDF_TO_TEXT -> "text/plain"
                                            else -> "application/pdf"
                                        }
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share ${file.name}"))
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(stringResource(R.string.library_menu_share))
                        }
                    }

                    TextButton(
                        onClick = {
                            val p = project
                            projectContextMenu = null
                            projectToDelete = p
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(stringResource(R.string.library_menu_delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { projectContextMenu = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Bulk Rename Dialog
    if (showBulkRenameDialog) {
        AlertDialog(
            onDismissRequest = { showBulkRenameDialog = false },
            icon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.library_bulk_rename_title, selectedProjectIds.size)) },
            text = {
                Column {
                    Text(
                        "Enter pattern or prefix for selected files. Use {N} for sequential numbers (1, 2, 3...) or {DATE} for current date.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = bulkRenamePatternInput,
                        onValueChange = { bulkRenamePatternInput = it },
                        label = { Text(stringResource(R.string.library_pattern_prefix)) },
                        placeholder = { Text(stringResource(R.string.library_pattern_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("bulk_rename_pattern_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Preview: ${bulkRenamePatternInput.replace("{N}", "1").replace("{DATE}", "20260824")}.pdf",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val ids = selectedProjectIds.toList()
                        showBulkRenameDialog = false
                        viewModel.bulkRenameProjects(ids, bulkRenamePatternInput)
                    },
                    enabled = bulkRenamePatternInput.isNotBlank() && selectedProjectIds.isNotEmpty(),
                    modifier = Modifier.testTag("confirm_bulk_rename_button")
                ) {
                    Text(stringResource(R.string.library_bulk_rename_title, selectedProjectIds.size))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
}
