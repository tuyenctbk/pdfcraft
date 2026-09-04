package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.pdf.PageSizeFormat
import com.example.data.pdf.TextFontFamily
import com.example.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextToPdfScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val textContent by viewModel.textToPdfContent.collectAsState()
    val options by viewModel.textToPdfOptions.collectAsState()
    val outputName by viewModel.textToPdfOutputName.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()

    var showOptions by remember { mutableStateOf(false) }

    // ActivityResultContracts.OpenDocument launcher for document selection
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadTextFromUri(it) }
    }

    val wordCount = remember(textContent) {
        if (textContent.isBlank()) 0 else textContent.trim().split("\\s+".toRegex()).size
    }
    val charCount = remember(textContent) { textContent.length }
    val lineCount = remember(textContent) { if (textContent.isEmpty()) 0 else textContent.lines().size }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.tool_text_to_pdf_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = stringResource(R.string.tool_text_to_pdf_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.setActiveTool(null) },
                        modifier = Modifier.testTag("text_to_pdf_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_cancel)
                        )
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = { filePickerLauncher.launch(arrayOf("text/*", "text/plain", "text/markdown", "text/csv", "application/json", "*/*")) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 8.dp).testTag("import_text_file_button")
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.picker_browse_files), fontSize = 12.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Button(
                        onClick = { viewModel.executeTextToPdf() },
                        enabled = textContent.isNotBlank() && !isProcessing,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("convert_text_to_pdf_button")
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generating PDF...")
                        } else {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Convert to PDF (${lineCount} Lines)", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
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
            // Stats bar & Quick template
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
                        StatItem(label = "Words", value = "$wordCount")
                        StatItem(label = "Characters", value = "$charCount")
                        StatItem(label = "Lines", value = "$lineCount")
                        StatItem(label = "Paper", value = options.pageSize.name)
                    }
                }
            }

            // Quick Samples Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = {
                            viewModel.setTextToPdfContent(
                                """# Project Architecture Overview
## 1. Introduction
PDFCraft is an offline-first, private mobile document manipulation tool built with Jetpack Compose and modern PDFBox rendering.

## 2. Key Modules
- **Converter Engine**: Native Android StaticLayout and PrintDocumentAdapter
- **Security**: AES-128 cryptographic password locks & metadata editors
- **Local Persistence**: Zero-cloud Room database for instant history access

## 3. Performance Metrics
- Memory footprint: < 50MB
- Execution speed: Instantaneous page rendering""".trimIndent()
                            )
                            viewModel.setTextToPdfOutputName("Project_Overview.pdf")
                        },
                        label = { Text("Sample Notes") },
                        leadingIcon = { Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp)) }
                    )
                    AssistChip(
                        onClick = {
                            viewModel.setTextToPdfContent(
                                """Date,Product,Quantity,Unit Price,Total
2026-09-01,Cloud License,5,$240.00,$1200.00
2026-09-02,Support Plan,1,$500.00,$500.00
2026-09-03,Hardware Dongle,12,$35.00,$420.00
2026-09-04,SSL Certificate,2,$99.00,$198.00

TOTAL REVENUE: $2,318.00""".trimIndent()
                            )
                            viewModel.setTextToPdfOutputName("Invoice_Report.pdf")
                            viewModel.updateTextToPdfOptions(fontFamily = TextFontFamily.MONOSPACE)
                        },
                        label = { Text("Sample CSV") },
                        leadingIcon = { Icon(Icons.Default.Info, null, modifier = Modifier.size(16.dp)) }
                    )
                    if (textContent.isNotEmpty()) {
                        AssistChip(
                            onClick = { viewModel.setTextToPdfContent("") },
                            label = { Text(stringResource(R.string.action_clear)) },
                            leadingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
            }

            // Document Title & Output Name
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Document Header & File Name",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = options.title,
                            onValueChange = { viewModel.updateTextToPdfOptions(title = it) },
                            label = { Text("Page Header Title (Optional)") },
                            placeholder = { Text("e.g. Weekly Meeting Minutes") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = outputName,
                            onValueChange = { viewModel.setTextToPdfOutputName(it) },
                            label = { Text("Output PDF File Name") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().testTag("text_to_pdf_output_name_input")
                        )
                    }
                }
            }

            // Expandable Format Options
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showOptions = !showOptions },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Formatting & Page Settings",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Icon(
                                imageVector = if (showOptions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                        }

                        AnimatedVisibility(visible = showOptions) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                // Font Size Slider
                                Text(
                                    text = "Font Size: ${options.fontSize.toInt()} pt",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Slider(
                                    value = options.fontSize,
                                    onValueChange = { viewModel.updateTextToPdfOptions(fontSize = it) },
                                    valueRange = 9f..24f,
                                    steps = 15
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // Font Family Selection
                                Text(
                                    text = "Typeface",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    TextFontFamily.entries.forEach { font ->
                                        val isSelected = options.fontFamily == font
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { viewModel.updateTextToPdfOptions(fontFamily = font) },
                                            label = { Text(font.name) }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Paper Size
                                Text(
                                    text = "Page Format",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    PageSizeFormat.entries.forEach { size ->
                                        val isSelected = options.pageSize == size
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { viewModel.updateTextToPdfOptions(pageSize = size) },
                                            label = { Text(size.name) }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Switches
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Include Page Numbers Footer")
                                    Switch(
                                        checked = options.includePageNumbers,
                                        onCheckedChange = { viewModel.updateTextToPdfOptions(includePageNumbers = it) }
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Include Date in Header")
                                    Switch(
                                        checked = options.includeHeaderDate,
                                        onCheckedChange = { viewModel.updateTextToPdfOptions(includeHeaderDate = it) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Main Text Editor Area
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
                                text = "Text / Code Editor",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Supports raw text, Markdown & CSV",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = textContent,
                            onValueChange = { viewModel.setTextToPdfContent(it) },
                            placeholder = { Text("Type, paste or import your text, notes, logs, or code here...") },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = if (options.fontFamily == TextFontFamily.MONOSPACE) FontFamily.Monospace else FontFamily.Default,
                                lineHeight = 20.sp
                            ),
                            minLines = 10,
                            maxLines = 25,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("text_to_pdf_content_input")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
