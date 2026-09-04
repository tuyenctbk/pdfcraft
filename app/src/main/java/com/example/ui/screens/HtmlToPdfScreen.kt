package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HtmlToPdfScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val htmlContent by viewModel.htmlContent.collectAsState()
    val htmlTitle by viewModel.htmlDocTitle.collectAsState()
    val outputName by viewModel.htmlOutputName.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadHtmlFromUri(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.tool_html_to_pdf_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = stringResource(R.string.tool_html_to_pdf_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.setActiveTool(null) },
                        modifier = Modifier.testTag("html_to_pdf_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_cancel)
                        )
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = { filePickerLauncher.launch(arrayOf("text/html", "application/xhtml+xml", "text/plain", "*/*")) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 8.dp).testTag("import_html_file_button")
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.picker_browse_files), fontSize = 12.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
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
                        onClick = { viewModel.executeHtmlToPdf() },
                        enabled = htmlContent.isNotBlank() && !isProcessing,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("convert_html_to_pdf_button")
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Converting HTML...")
                        } else {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Convert HTML to PDF", fontWeight = FontWeight.Bold)
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
            // Quick HTML Templates
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = {
                            viewModel.setHtmlContent(
                                """<!DOCTYPE html>
<html>
<head>
<style>
body { font-family: 'Helvetica', sans-serif; margin: 20px; color: #1e293b; }
h1 { color: #0f766e; border-bottom: 2px solid #0f766e; padding-bottom: 8px; }
table { width: 100%; border-collapse: collapse; margin-top: 15px; }
th, td { border: 1px solid #cbd5e1; padding: 8px; text-align: left; }
th { background-color: #f1f5f9; }
</style>
</head>
<body>
<h1>Financial Summary Report</h1>
<p>Generated by PDFCraft Offline Suite.</p>
<table>
<tr><th>Quarter</th><th>Revenue</th><th>Growth</th></tr>
<tr><td>Q1 2026</td><td>$145,000</td><td>+12%</td></tr>
<tr><td>Q2 2026</td><td>$182,000</td><td>+25%</td></tr>
</table>
</body>
</html>""".trimIndent()
                            )
                            viewModel.setHtmlDocTitle("Financial Report")
                            viewModel.setHtmlOutputName("Financial_Report.pdf")
                        },
                        label = { Text("Report HTML") },
                        leadingIcon = { Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp)) }
                    )
                    AssistChip(
                        onClick = {
                            viewModel.setHtmlContent(
                                """<!DOCTYPE html>
<html>
<head>
<style>
body { font-family: Georgia, serif; line-height: 1.7; padding: 25px; }
h2 { color: #831843; }
blockquote { font-style: italic; border-left: 4px solid #f472b6; padding-left: 12px; margin: 15px 0; }
</style>
</head>
<body>
<h2>Executive Brief</h2>
<p>Mobile device document workflows require strict on-device data isolation to maintain privacy and enterprise compliance.</p>
<blockquote>"Privacy is not an afterthought; it is a structural architectural foundation."</blockquote>
</body>
</html>""".trimIndent()
                            )
                            viewModel.setHtmlDocTitle("Executive Brief")
                            viewModel.setHtmlOutputName("Executive_Brief.pdf")
                        },
                        label = { Text("Article HTML") },
                        leadingIcon = { Icon(Icons.Default.Description, null, modifier = Modifier.size(16.dp)) }
                    )
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
                            text = "PDF Output Settings",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = htmlTitle,
                            onValueChange = { viewModel.setHtmlDocTitle(it) },
                            label = { Text("Document Header Title") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = outputName,
                            onValueChange = { viewModel.setHtmlOutputName(it) },
                            label = { Text("Output PDF File Name") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().testTag("html_to_pdf_output_name_input")
                        )
                    }
                }
            }

            // HTML Source Code Editor
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
                                text = "HTML / CSS Source Code",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${htmlContent.length} chars",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = htmlContent,
                            onValueChange = { viewModel.setHtmlContent(it) },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            ),
                            minLines = 14,
                            maxLines = 30,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("html_code_editor")
                        )
                    }
                }
            }
        }
    }
}
