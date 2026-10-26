package com.example.ui.components

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.pdf.PdfEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Data class representing a selected document with its metadata.
 */
data class SelectedDocument(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String = "application/pdf"
)

/**
 * Helper function to retrieve metadata for a selected URI.
 */
fun getDocumentMetadata(context: Context, uri: Uri): SelectedDocument {
    var name = "document.pdf"
    var size = 0L
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) {
                    name = cursor.getString(nameIndex) ?: name
                }
                if (sizeIndex != -1) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    val mime = context.contentResolver.getType(uri) ?: "application/pdf"
    return SelectedDocument(uri = uri, name = name, sizeBytes = size, mimeType = mime)
}

/**
 * Primary Compose UI Component using ActivityResultContracts.OpenDocument
 * to allow users to select PDF files from device storage.
 */
@Composable
fun PdfDocumentPickerCard(
    selectedFile: File?,
    onFileSelected: (File) -> Unit,
    modifier: Modifier = Modifier,
    onFileCleared: (() -> Unit)? = null,
    title: String = stringResource(R.string.picker_select_pdf_title),
    subtitle: String = stringResource(R.string.picker_select_pdf_subtitle),
    icon: ImageVector = Icons.Default.PictureAsPdf,
    mimeTypes: Array<String> = arrayOf("application/pdf"),
    buttonText: String = stringResource(R.string.picker_browse_files),
    showChangeButton: Boolean = true,
    testTagPrefix: String = "pdf_picker"
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isResolving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // ActivityResultContracts.OpenDocument contract launcher
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isResolving = true
            errorMessage = null
            scope.launch {
                try {
                    val localFile = withContext(Dispatchers.IO) {
                        PdfEngine.getLocalFileFromUri(context, uri)
                    }
                    if (localFile.exists()) {
                        onFileSelected(localFile)
                    } else {
                        errorMessage = context.getString(R.string.picker_error_reading_file)
                    }
                } catch (e: Exception) {
                    errorMessage = e.localizedMessage ?: context.getString(R.string.picker_error_reading_file)
                } finally {
                    isResolving = false
                }
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selectedFile != null)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        border = BorderStroke(
            1.dp,
            if (selectedFile != null)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            else
                MaterialTheme.colorScheme.outlineVariant
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("${testTagPrefix}_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (selectedFile == null) {
                // Empty state - Prompt to choose file
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isResolving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(26.dp),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        errorMessage = null
                        openDocumentLauncher.launch(mimeTypes)
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    modifier = Modifier.testTag("${testTagPrefix}_open_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = buttonText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        val sample = getOrCreateDefaultSamplePdf(context)
                        if (sample.exists()) {
                            onFileSelected(sample)
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("${testTagPrefix}_default_sample_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Open Default Sample PDF",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            } else {
                // Selected file state
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedFile.name,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "PDF",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = PdfEngine.formatFileSize(selectedFile.length()),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (showChangeButton) {
                        FilledTonalButton(
                            onClick = { openDocumentLauncher.launch(mimeTypes) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("${testTagPrefix}_change_button")
                        ) {
                            Text(stringResource(R.string.action_change), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (onFileCleared != null) {
                        IconButton(
                            onClick = onFileCleared,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("${testTagPrefix}_clear_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.action_remove),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Error display
            AnimatedVisibility(visible = errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

/**
 * Multi-Document Picker Component using ActivityResultContracts.OpenMultipleDocuments
 * to allow users to select multiple PDF documents at once.
 */
@Composable
fun PdfMultipleDocumentPickerCard(
    selectedFiles: List<File>,
    onFilesAdded: (List<File>) -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.picker_select_multiple_pdfs_title),
    subtitle: String = stringResource(R.string.picker_select_multiple_pdfs_subtitle),
    mimeTypes: Array<String> = arrayOf("application/pdf"),
    buttonText: String = stringResource(R.string.picker_add_pdfs),
    testTagPrefix: String = "pdf_multi_picker"
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isResolving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // ActivityResultContracts.OpenMultipleDocuments contract launcher
    val openMultipleDocumentsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            isResolving = true
            errorMessage = null
            scope.launch {
                try {
                    val newFiles = mutableListOf<File>()
                    for (uri in uris) {
                        val file = withContext(Dispatchers.IO) {
                            PdfEngine.getLocalFileFromUri(context, uri)
                        }
                        if (file.exists()) {
                            newFiles.add(file)
                        }
                    }
                    if (newFiles.isNotEmpty()) {
                        onFilesAdded(newFiles)
                    } else {
                        errorMessage = context.getString(R.string.picker_error_reading_file)
                    }
                } catch (e: Exception) {
                    errorMessage = e.localizedMessage ?: context.getString(R.string.picker_error_reading_file)
                } finally {
                    isResolving = false
                }
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("${testTagPrefix}_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isResolving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.NoteAdd,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    errorMessage = null
                    openMultipleDocumentsLauncher.launch(mimeTypes)
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp),
                modifier = Modifier.testTag("${testTagPrefix}_open_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = buttonText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            AnimatedVisibility(visible = errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}

/**
 * Reusable Button that launches OpenDocument contract on click.
 */
@Composable
fun PdfDocumentPickerButton(
    onFileSelected: (File) -> Unit,
    modifier: Modifier = Modifier,
    text: String = stringResource(R.string.picker_browse_files),
    icon: ImageVector = Icons.Default.FolderOpen,
    mimeTypes: Array<String> = arrayOf("application/pdf"),
    enabled: Boolean = true,
    testTag: String = "pdf_picker_button"
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val localFile = withContext(Dispatchers.IO) {
                    PdfEngine.getLocalFileFromUri(context, uri)
                }
                if (localFile.exists()) {
                    onFileSelected(localFile)
                }
            }
        }
    }

    Button(
        onClick = { openDocumentLauncher.launch(mimeTypes) },
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        modifier = modifier.testTag(testTag)
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, fontWeight = FontWeight.SemiBold)
    }
}

fun getOrCreateDefaultSamplePdf(context: Context): File {
    val file = File(context.cacheDir, "Default_Sample_Document.pdf")
    if (!file.exists()) {
        try {
            val doc = android.graphics.pdf.PdfDocument()
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawColor(android.graphics.Color.WHITE)
            
            val paintTitle = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#0F172A")
                textSize = 24f
                isFakeBoldText = true
            }
            val paintBody = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#334155")
                textSize = 14f
            }
            
            canvas.drawText("PDFCraft Default Sample Document", 40f, 60f, paintTitle)
            canvas.drawText("This is the default sample PDF provided for quick testing and editing.", 40f, 100f, paintBody)
            canvas.drawText("You can view, merge, split, rotate, or compress this document instantly.", 40f, 130f, paintBody)
            
            doc.finishPage(page)
            java.io.FileOutputStream(file).use { doc.writeTo(it) }
            doc.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    return file
}
