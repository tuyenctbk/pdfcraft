package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.MainViewModel
import com.example.ui.components.PdfDocumentPickerCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToTextScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sourceFile by viewModel.pdfToTextSourceFile.collectAsState()
    val extractedText by viewModel.extractedPdfText.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()

    val wordCount = remember(extractedText) {
        if (extractedText.isBlank()) 0 else extractedText.trim().split("\\s+".toRegex()).size
    }
    val charCount = remember(extractedText) { extractedText.length }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.tool_pdf_to_text_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = stringResource(R.string.tool_pdf_to_text_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.setActiveTool(null) },
                        modifier = Modifier.testTag("pdf_to_text_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_cancel)
                        )
                    }
                },
                actions = {
                    if (extractedText.isNotBlank()) {
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("PDF Text", extractedText)
                                clipboard.setPrimaryClip(clip)
                                viewModel.showUserMessage(context.getString(R.string.action_copy) + " ✓")
                            },
                            modifier = Modifier.testTag("copy_extracted_text_button")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.action_copy))
                        }
                        IconButton(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, extractedText)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.action_share)))
                            },
                            modifier = Modifier.testTag("share_extracted_text_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.action_share))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                end = 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Source Document Selector via ActivityResultContracts.OpenDocument
            item {
                PdfDocumentPickerCard(
                    selectedFile = sourceFile,
                    onFileSelected = { file ->
                        viewModel.loadPdfForTextExtraction(android.net.Uri.fromFile(file))
                    },
                    onFileCleared = {
                        viewModel.clearPdfToText()
                    },
                    title = stringResource(R.string.tool_pdf_to_text_title),
                    subtitle = stringResource(R.string.tool_pdf_to_text_desc),
                    testTagPrefix = "pdf_to_text_picker"
                )
            }

            // Stats Card if text is extracted
            if (extractedText.isNotBlank()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$wordCount", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text("Words", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$charCount", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text("Characters", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${extractedText.lines().size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text("Lines", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // Extracted Text View / Edit Box
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Extracted Document Content",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            if (extractedText.isNotEmpty()) {
                                TextButton(
                                    onClick = {
                                        viewModel.setTextToPdfContent(extractedText)
                                        viewModel.setActiveTool(com.example.data.local.PdfOperationType.TEXT_TO_PDF)
                                    }
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Convert to PDF", fontSize = 12.sp)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        if (isProcessing) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator()
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("Extracting text with PDFBox...")
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = extractedText,
                                onValueChange = { viewModel.setExtractedPdfText(it) },
                                placeholder = { Text("Extracted text from the selected PDF will appear here for viewing or editing...") },
                                minLines = 14,
                                maxLines = 30,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("extracted_text_editor")
                            )
                        }
                    }
                }
            }
        }
    }
}
