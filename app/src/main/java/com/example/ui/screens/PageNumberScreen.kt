package com.example.ui.screens
import com.example.R
import androidx.compose.ui.res.stringResource

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.pdf.PageNumberPosition
import com.example.data.pdf.PdfEngine
import com.example.ui.MainViewModel

@Composable
fun PageNumberScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sourceFile by viewModel.pageNumberSourceFile.collectAsState()
    val sourceUri by viewModel.pageNumberSourceUri.collectAsState()
    val options by viewModel.pageNumberOptions.collectAsState()
    val outputName by viewModel.pageNumberOutputName.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadPdfForPageNumber(it) }
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
                .padding(16.dp)
        ) {
            // Header Bar
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.setActiveTool(null) }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Add Page Numbers",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = "Overlay page numbers on PDF headers or footers",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Document Selection Card
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Select Target PDF Document",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            if (sourceFile == null) {
                                OutlinedButton(
                                    onClick = { filePickerLauncher.launch("application/pdf") },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("select_page_number_pdf_button")
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.action_choose_pdf_file), fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(sourceFile!!.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                            Text(PdfEngine.formatFileSize(sourceFile!!.length()), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }

                                    TextButton(onClick = { filePickerLauncher.launch("application/pdf") }) {
                                        Text(stringResource(R.string.action_change), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                if (sourceFile != null) {
                    // Pattern Options
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(stringResource(R.string.page_number_pattern_title), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                Text(stringResource(R.string.page_number_pattern_desc), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(12.dp))

                                val presetPatterns = listOf("Page {P} of {N}", "Page {P}", "{P} / {N}", "- {P} -")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    presetPatterns.forEach { pattern ->
                                        val selected = options.formatPattern == pattern
                                        FilterChip(
                                            selected = selected,
                                            onClick = { viewModel.updatePageNumberOptions(formatPattern = pattern) },
                                            label = { Text(pattern, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = options.formatPattern,
                                    onValueChange = { viewModel.updatePageNumberOptions(formatPattern = it) },
                                    label = { Text(stringResource(R.string.page_number_custom_pattern)) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("page_number_pattern_input")
                                )
                            }
                        }
                    }

                    // Position Selector
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(stringResource(R.string.page_number_position), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                Spacer(modifier = Modifier.height(12.dp))

                                val positions = listOf(
                                    PageNumberPosition.FOOTER_CENTER to "Footer Center",
                                    PageNumberPosition.FOOTER_LEFT to "Footer Left",
                                    PageNumberPosition.FOOTER_RIGHT to "Footer Right",
                                    PageNumberPosition.HEADER_CENTER to "Header Center",
                                    PageNumberPosition.HEADER_LEFT to "Header Left",
                                    PageNumberPosition.HEADER_RIGHT to "Header Right"
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    positions.chunked(3).forEach { row ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                            row.forEach { (pos, label) ->
                                                val isSelected = options.position == pos
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                                    border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clickable { viewModel.updatePageNumberOptions(position = pos) }
                                                ) {
                                                    Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = label,
                                                            fontSize = 11.sp,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Output Settings & Action
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                OutlinedTextField(
                                    value = outputName,
                                    onValueChange = { viewModel.setPageNumberOutputName(it) },
                                    label = { Text(stringResource(R.string.output_file_name_label)) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("page_number_output_name_input")
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = { viewModel.executeAddPageNumbers() },
                                    enabled = !isProcessing && sourceFile != null,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("execute_add_page_numbers_button")
                                ) {
                                    Icon(Icons.Default.Numbers, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.page_number_apply_button), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
