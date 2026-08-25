package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.PdfOperationType
import com.example.ui.MainViewModel

enum class ToolCategory(val title: String) {
    ALL("All Tools"),
    CONVERT("Convert & Merge"),
    PAGES("Page Tools"),
    SECURITY("Security"),
    OPTIMIZE("Optimize")
}

data class ToolItemData(
    val titleRes: Int,
    val descRes: Int,
    val icon: ImageVector,
    val operationType: PdfOperationType,
    val category: ToolCategory,
    val isNew: Boolean = false,
    val isPopular: Boolean = false,
    val iconTint: Color = Color(0xFF10B981),
    val containerColor: Color = Color(0xFF064E3B)
)

val ALL_PDF_TOOLS = listOf(
    ToolItemData(
        titleRes = R.string.tool_merge_title,
        descRes = R.string.tool_merge_desc,
        icon = Icons.Default.Merge,
        operationType = PdfOperationType.MERGE,
        category = ToolCategory.CONVERT,
        isPopular = true,
        iconTint = Color(0xFF10B981),
        containerColor = Color(0xFF064E3B)
    ),
    ToolItemData(
        titleRes = R.string.tool_split_title,
        descRes = R.string.tool_split_desc,
        icon = Icons.Default.ContentCut,
        operationType = PdfOperationType.SPLIT,
        category = ToolCategory.PAGES,
        iconTint = Color(0xFF38BDF8),
        containerColor = Color(0xFF075985)
    ),
    ToolItemData(
        titleRes = R.string.tool_image_to_pdf_title,
        descRes = R.string.tool_image_to_pdf_desc,
        icon = Icons.Default.Image,
        operationType = PdfOperationType.IMAGE_TO_PDF,
        category = ToolCategory.CONVERT,
        isNew = true,
        iconTint = Color(0xFFA78BFA),
        containerColor = Color(0xFF4C1D95)
    ),
    ToolItemData(
        titleRes = R.string.tool_page_number_title,
        descRes = R.string.tool_page_number_desc,
        icon = Icons.Default.TextFields,
        operationType = PdfOperationType.PAGE_NUMBER,
        category = ToolCategory.PAGES,
        isNew = true,
        iconTint = Color(0xFFFBBF24),
        containerColor = Color(0xFF78350F)
    ),
    ToolItemData(
        titleRes = R.string.tool_compress_title,
        descRes = R.string.tool_compress_desc,
        icon = Icons.Default.Compress,
        operationType = PdfOperationType.COMPRESS,
        category = ToolCategory.OPTIMIZE,
        isPopular = true,
        iconTint = Color(0xFF34D399),
        containerColor = Color(0xFF065F46)
    ),
    ToolItemData(
        titleRes = R.string.tool_rotate_title,
        descRes = R.string.tool_rotate_desc,
        icon = Icons.Default.RotateRight,
        operationType = PdfOperationType.ROTATE,
        category = ToolCategory.PAGES,
        iconTint = Color(0xFF60A5FA),
        containerColor = Color(0xFF1E3A8A)
    ),
    ToolItemData(
        titleRes = R.string.tool_watermark_title,
        descRes = R.string.tool_watermark_desc,
        icon = Icons.Default.TextFields,
        operationType = PdfOperationType.WATERMARK,
        category = ToolCategory.SECURITY,
        iconTint = Color(0xFFF472B6),
        containerColor = Color(0xFF831843)
    ),
    ToolItemData(
        titleRes = R.string.tool_sign_title,
        descRes = R.string.tool_sign_desc,
        icon = Icons.Default.Draw,
        operationType = PdfOperationType.SIGN,
        category = ToolCategory.SECURITY,
        iconTint = Color(0xFFF87171),
        containerColor = Color(0xFF7F1D1D)
    ),
    ToolItemData(
        titleRes = R.string.tool_encrypt_title,
        descRes = R.string.tool_encrypt_desc,
        icon = Icons.Default.Lock,
        operationType = PdfOperationType.ENCRYPT,
        category = ToolCategory.SECURITY,
        iconTint = Color(0xFFFB923C),
        containerColor = Color(0xFF7C2D12)
    ),
    ToolItemData(
        titleRes = R.string.tool_metadata_title,
        descRes = R.string.tool_metadata_desc,
        icon = Icons.Default.Edit,
        operationType = PdfOperationType.METADATA,
        category = ToolCategory.OPTIMIZE,
        iconTint = Color(0xFF2DD4BF),
        containerColor = Color(0xFF134E4A)
    )
)

@Composable
fun ToolsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(ToolCategory.ALL) }

    val filteredTools = remember(searchQuery, selectedCategory) {
        ALL_PDF_TOOLS.filter { tool ->
            val matchesCategory = selectedCategory == ToolCategory.ALL || tool.category == selectedCategory
            val matchesSearch = searchQuery.isBlank() ||
                    tool.operationType.name.contains(searchQuery, ignoreCase = true) ||
                    tool.category.name.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
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
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "PDF Tool Suite",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                    Text(
                        text = "10 high-performance tools • 100% on-device",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "OFFLINE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search tools (e.g. merge, compress, sign)...", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tools_search_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Category Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(ToolCategory.entries) { category ->
                    val isSelected = selectedCategory == category
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .clickable { selectedCategory = category }
                            .testTag("tool_category_${category.name}")
                    ) {
                        Text(
                            text = category.title,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tool Grid
            if (filteredTools.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No tools match '$searchQuery'",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Try searching for a different keyword or choose another category",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredTools, key = { it.operationType.name }) { tool ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(168.dp)
                                .clickable { viewModel.setActiveTool(tool.operationType) }
                                .testTag("tool_card_${tool.operationType.name}")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = tool.icon,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }

                                    if (tool.isNew) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.tertiaryContainer,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "NEW",
                                                color = MaterialTheme.colorScheme.tertiary,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    } else if (tool.isPopular) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "POPULAR",
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Column {
                                    Text(
                                        text = stringResource(tool.titleRes),
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = stringResource(tool.descRes),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp
                                        ),
                                        maxLines = 2
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
