package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.PdfOperationType
import com.example.data.pdf.PdfMetadata
import com.example.ui.MainViewModel
import java.io.File

@Composable
fun DocumentInfoScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit = { viewModel.setActiveTool(null) },
    modifier: Modifier = Modifier
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val sourceFile by viewModel.docInfoSourceFile.collectAsState()
    val sourceUri by viewModel.docInfoSourceUri.collectAsState()
    val metadata by viewModel.docInfoMetadata.collectAsState()
    val thumbnail by viewModel.docInfoThumbnail.collectAsState()
    val isLoading by viewModel.isLoadingDocInfo.collectAsState()
    val recentFiles by viewModel.recentFilesState.collectAsState()

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadPdfForDocInfo(it) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top App Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("btn_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.tool_doc_info_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = metadata?.fileName ?: stringResource(R.string.doc_info_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // File picker action
                IconButton(
                    onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                    modifier = Modifier.testTag("btn_change_file")
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = stringResource(R.string.action_select_pdf),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Copy report action if metadata is present
                if (metadata != null) {
                    IconButton(
                        onClick = {
                            metadata?.let { meta ->
                                copyReportToClipboard(context, meta)
                                viewModel.showUserMessage(context.getString(R.string.doc_info_copied_msg))
                            }
                        },
                        modifier = Modifier.testTag("btn_copy_info_top")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.doc_info_copy_report),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    sourceFile?.let { file ->
                        IconButton(
                            onClick = { viewModel.sharePdfFile(context, file) },
                            modifier = Modifier.testTag("btn_share_pdf_top")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(R.string.doc_info_share_pdf),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // Body Content
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = stringResource(R.string.doc_info_analyzing),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            metadata == null -> {
                // Empty state: prompt to select document
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    contentPadding = PaddingValues(top = 28.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    item {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Article,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                    }

                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.doc_info_inspect_title),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = stringResource(R.string.doc_info_inspect_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }

                    item {
                        Button(
                            onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("btn_select_pdf")
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.picker_select_pdf_title), fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (recentFiles.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.hub_recent_files),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        items(recentFiles.take(5)) { recent ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.openDocumentInfoScreen(recent.filePath)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = recent.fileName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = stringResource(R.string.common_pages_and_size, recent.pageCount, recent.fileSizeFormatted),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                val currentMeta = metadata!!
                val currentFile = sourceFile

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Hero Card: Thumbnail + Key Badges + Action Buttons
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    // Thumbnail preview or fallback
                                    Box(
                                        modifier = Modifier
                                            .width(96.dp)
                                            .height(132.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .padding(1.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (thumbnail != null) {
                                            Image(
                                                bitmap = thumbnail!!.asImageBitmap(),
                                                contentDescription = stringResource(R.string.doc_info_first_page_thumb),
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(7.dp)),
                                                contentScale = ContentScale.Fit
                                            )
                                        } else {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Description,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(36.dp)
                                                )
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    text = "PDF",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }

                                    // File info & badges
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = currentMeta.fileName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            BadgePill(text = currentMeta.fileSizeFormatted, isAccent = false)
                                            BadgePill(
                                                text = if (currentMeta.pageCount == 1) stringResource(R.string.common_page_single) else stringResource(R.string.common_pages_count, currentMeta.pageCount),
                                                isAccent = true
                                            )
                                        }

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            val cleanVer = (currentMeta.pdfVersion ?: "1.7").removePrefix("PDF ").removePrefix("v")
                                            BadgePill(text = "PDF $cleanVer", isAccent = false)
                                            if (currentMeta.pageSizeFormat != null) {
                                                BadgePill(text = currentMeta.pageSizeFormat, isAccent = false)
                                            }
                                        }

                                        // Security badge
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.padding(top = 2.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (currentMeta.isEncrypted) Icons.Default.Lock else Icons.Default.LockOpen,
                                                contentDescription = null,
                                                tint = if (currentMeta.isEncrypted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = if (currentMeta.isEncrypted) stringResource(R.string.doc_info_password_protected) else stringResource(R.string.doc_info_standard_decrypted),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (currentMeta.isEncrypted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                // Action Buttons Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (currentFile != null) {
                                        FilledTonalButton(
                                            onClick = { viewModel.openInViewer(currentFile.absolutePath) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("btn_open_viewer"),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                                        ) {
                                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(stringResource(R.string.doc_info_viewer), style = MaterialTheme.typography.labelMedium)
                                        }

                                        FilledTonalButton(
                                            onClick = {
                                                val editUri = sourceUri ?: Uri.fromFile(currentFile)
                                                viewModel.setActiveTool(PdfOperationType.METADATA)
                                                viewModel.loadPdfForMetadata(editUri)
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("btn_edit_metadata"),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(stringResource(R.string.action_edit), style = MaterialTheme.typography.labelMedium)
                                        }

                                        FilledTonalButton(
                                            onClick = { viewModel.sharePdfFile(context, currentFile) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("btn_share_pdf"),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                                        ) {
                                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(stringResource(R.string.action_share), style = MaterialTheme.typography.labelMedium)
                                        }

                                        FilledTonalButton(
                                            onClick = { viewModel.saveFileToDownloadsExplicitly(context, currentFile) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("btn_save_downloads"),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(stringResource(R.string.action_save_short), style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Section 1: Document Attributes
                    item {
                        MetadataSectionCard(
                            title = stringResource(R.string.doc_info_section_attributes),
                            icon = Icons.Default.TextFields
                        ) {
                            MetadataRow(label = stringResource(R.string.doc_info_label_title), value = currentMeta.title ?: stringResource(R.string.doc_info_not_set))
                            MetadataRow(label = stringResource(R.string.doc_info_label_author), value = currentMeta.author ?: stringResource(R.string.doc_info_unknown_not_set))
                            MetadataRow(label = stringResource(R.string.doc_info_label_subject), value = currentMeta.subject ?: stringResource(R.string.doc_info_not_set))
                            MetadataRow(label = stringResource(R.string.doc_info_label_keywords), value = currentMeta.keywords ?: stringResource(R.string.doc_info_not_set), isLast = true)
                        }
                    }

                    // Section 2: Layout & Technical Specs
                    item {
                        MetadataSectionCard(
                            title = stringResource(R.string.doc_info_section_layout),
                            icon = Icons.Default.Straighten
                        ) {
                            MetadataRow(label = stringResource(R.string.doc_info_label_page_count), value = stringResource(R.string.common_pages_count, currentMeta.pageCount))
                            MetadataRow(label = stringResource(R.string.doc_info_label_paper_format), value = currentMeta.pageSizeFormat ?: stringResource(R.string.doc_info_standard))
                            MetadataRow(label = stringResource(R.string.doc_info_label_dimensions), value = currentMeta.pageDimensions ?: stringResource(R.string.doc_info_standard_dimensions))
                            val cleanVer = (currentMeta.pdfVersion ?: "1.7").removePrefix("PDF ").removePrefix("v")
                            MetadataRow(label = stringResource(R.string.doc_info_label_pdf_version), value = "v$cleanVer", isMono = true)
                            MetadataRow(label = stringResource(R.string.doc_info_label_file_size), value = stringResource(R.string.doc_info_size_bytes, currentMeta.fileSizeFormatted, currentMeta.fileSizeBytes), isMono = true, isLast = true)
                        }
                    }

                    // Section 3: Creation History
                    item {
                        MetadataSectionCard(
                            title = stringResource(R.string.doc_info_section_history),
                            icon = Icons.Default.CalendarToday
                        ) {
                            MetadataRow(label = stringResource(R.string.doc_info_label_created), value = currentMeta.creationDateFormatted ?: stringResource(R.string.doc_info_not_recorded))
                            MetadataRow(label = stringResource(R.string.doc_info_label_modified), value = currentMeta.modDateFormatted ?: stringResource(R.string.doc_info_not_recorded))
                            MetadataRow(label = stringResource(R.string.doc_info_label_authoring_tool), value = currentMeta.creator ?: stringResource(R.string.doc_info_not_specified))
                            MetadataRow(label = stringResource(R.string.doc_info_label_producer), value = currentMeta.producer ?: stringResource(R.string.doc_info_not_specified))
                            MetadataRow(label = stringResource(R.string.doc_info_label_storage), value = currentMeta.filePath, isMono = true, isLast = true)
                        }
                    }

                    // Section 4: Security & Permissions
                    item {
                        MetadataSectionCard(
                            title = stringResource(R.string.doc_info_section_security),
                            icon = Icons.Default.Security
                        ) {
                            PermissionStatusRow(
                                label = stringResource(R.string.doc_info_label_encryption),
                                statusText = if (currentMeta.isEncrypted) stringResource(R.string.doc_info_encrypted) else stringResource(R.string.doc_info_no_password),
                                isPositive = !currentMeta.isEncrypted
                            )
                            PermissionStatusRow(
                                label = stringResource(R.string.doc_info_label_printing),
                                statusText = if (currentMeta.canPrint) stringResource(R.string.doc_info_allowed_hq) else stringResource(R.string.doc_info_restricted),
                                isPositive = currentMeta.canPrint
                            )
                            PermissionStatusRow(
                                label = stringResource(R.string.doc_info_label_copying),
                                statusText = if (currentMeta.canCopyContent) stringResource(R.string.doc_info_allowed) else stringResource(R.string.doc_info_restricted),
                                isPositive = currentMeta.canCopyContent
                            )
                            PermissionStatusRow(
                                label = stringResource(R.string.doc_info_label_modifications),
                                statusText = if (currentMeta.canModify) stringResource(R.string.doc_info_allowed) else stringResource(R.string.doc_info_restricted),
                                isPositive = currentMeta.canModify,
                                isLast = true
                            )
                        }
                    }

                    // Bottom Action Buttons: Copy Full Report & Pick Another
                    item {
                        Spacer(Modifier.height(4.dp))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    copyReportToClipboard(context, currentMeta)
                                    viewModel.showUserMessage(context.getString(R.string.doc_info_full_report_copied))
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("btn_copy_info")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.doc_info_copy_full_report), fontWeight = FontWeight.SemiBold)
                            }

                            OutlinedButton(
                                onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("btn_inspect_another")
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.doc_info_inspect_another), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            content()
        }
    }
}

@Composable
private fun MetadataRow(
    label: String,
    value: String,
    isMono: Boolean = false,
    isLast: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(min = 100.dp, max = 130.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = value,
                style = if (isMono) MaterialTheme.typography.bodySmall.copy(fontFamily = com.example.ui.theme.JetBrainsMonoFamily) else MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }
        if (!isLast) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }
    }
}

@Composable
private fun PermissionStatusRow(
    label: String,
    statusText: String,
    isPositive: Boolean,
    isLast: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(min = 100.dp, max = 130.dp)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (isPositive) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        }
        if (!isLast) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }
    }
}

@Composable
private fun BadgePill(text: String, isAccent: Boolean) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isAccent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (isAccent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

private fun copyReportToClipboard(context: Context, meta: PdfMetadata) {
    val report = buildString {
        appendLine("========================================")
        appendLine("PDF DOCUMENT INFORMATION REPORT")
        appendLine("========================================")
        appendLine("File Name: ${meta.fileName}")
        appendLine("File Size: ${meta.fileSizeFormatted} (${meta.fileSizeBytes} bytes)")
        appendLine("Page Count: ${meta.pageCount}")
        appendLine("Paper Format: ${meta.pageSizeFormat ?: "Standard"}")
        appendLine("Page Dimensions: ${meta.pageDimensions ?: "Standard"}")
        appendLine("PDF Version: ${meta.pdfVersion ?: "1.7"}")
        appendLine()
        appendLine("--- Document Attributes ---")
        appendLine("Title: ${meta.title ?: "None"}")
        appendLine("Author: ${meta.author ?: "Unknown"}")
        appendLine("Subject: ${meta.subject ?: "None"}")
        appendLine("Keywords: ${meta.keywords ?: "None"}")
        appendLine()
        appendLine("--- Creation History ---")
        appendLine("Created: ${meta.creationDateFormatted ?: "Unknown"}")
        appendLine("Modified: ${meta.modDateFormatted ?: "Unknown"}")
        appendLine("Authoring App: ${meta.creator ?: "Unknown"}")
        appendLine("PDF Producer: ${meta.producer ?: "Unknown"}")
        appendLine("Storage Path: ${meta.filePath}")
        appendLine()
        appendLine("--- Security & Permissions ---")
        appendLine("Encrypted: ${if (meta.isEncrypted) "Yes" else "No"}")
        appendLine("Can Print: ${if (meta.canPrint) "Yes" else "No"}")
        appendLine("Can Copy Content: ${if (meta.canCopyContent) "Yes" else "No"}")
        appendLine("Can Modify: ${if (meta.canModify) "Yes" else "No"}")
        appendLine("========================================")
        appendLine("Generated by PDFCraft on Android")
    }

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = ClipData.newPlainText("PDF Metadata Report", report)
    clipboard?.setPrimaryClip(clip)
}
