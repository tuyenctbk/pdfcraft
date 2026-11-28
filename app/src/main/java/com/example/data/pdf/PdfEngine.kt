package com.example.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PdfPageSourceItem(
    val id: String,
    val sourceTitle: String,
    val sourceUri: Uri,
    val originalPageIndex: Int,
    var rotationDegrees: Int = 0
)

data class CompressionPreset(
    val id: String,
    val title: String,
    val description: String,
    @param:androidx.annotation.StringRes val titleRes: Int,
    @param:androidx.annotation.StringRes val descRes: Int,
    val targetDpi: Int,
    val jpegQuality: Int,
    val estimatedSavings: String
)

data class PdfMetadata(
    val fileName: String,
    val filePath: String,
    val fileSizeFormatted: String,
    val fileSizeBytes: Long,
    val title: String?,
    val author: String?,
    val subject: String?,
    val keywords: String?,
    val creator: String?,
    val producer: String?,
    val pageCount: Int,
    val creationDateFormatted: String?,
    val modDateFormatted: String?,
    val isEncrypted: Boolean,
    val pdfVersion: String? = "1.7",
    val pageSizeFormat: String? = null,
    val pageDimensions: String? = null,
    val canPrint: Boolean = true,
    val canCopyContent: Boolean = true,
    val canModify: Boolean = true
)

object PdfEngine {
    private const val TAG = "PdfEngine"
    private val thumbnailCache = LruCache<String, Bitmap>(35)

    val COMPRESSION_PRESETS = listOf(
        CompressionPreset(
            id = "max_quality",
            title = "Max Quality",
            description = "High resolution for printing (300 DPI)",
            titleRes = com.example.R.string.compress_quality_max,
            descRes = com.example.R.string.compress_preset_max_desc,
            targetDpi = 240,
            jpegQuality = 90,
            estimatedSavings = "~15-30%"
        ),
        CompressionPreset(
            id = "balanced",
            title = "Balanced",
            description = "Ideal for sharing and email (150 DPI)",
            titleRes = com.example.R.string.compress_quality_balanced,
            descRes = com.example.R.string.compress_preset_balanced_desc,
            targetDpi = 140,
            jpegQuality = 70,
            estimatedSavings = "~50-65%"
        ),
        CompressionPreset(
            id = "compact",
            title = "Compact / Web",
            description = "Maximum size reduction (100 DPI)",
            titleRes = com.example.R.string.compress_quality_compact,
            descRes = com.example.R.string.compress_preset_compact_desc,
            targetDpi = 96,
            jpegQuality = 45,
            estimatedSavings = "~75-85%"
        )
    )

    /**
     * Sanitizes a user-provided output filename so it cannot contain path traversal segments
     * or illegal filesystem characters and always ends cleanly with [extension].
     */
    fun sanitizeOutputFileName(
        rawName: String,
        fallbackBase: String = "PDFCraft_Output",
        extension: String = ".pdf"
    ): String {
        val ext = if (extension.isBlank()) "" else if (extension.startsWith(".")) extension else ".$extension"
        val baseOnly = rawName.replace('\\', '/').substringAfterLast('/').trim()
        val strippedExt = if (ext.isNotEmpty() && baseOnly.endsWith(ext, ignoreCase = true)) {
            baseOnly.dropLast(ext.length)
        } else {
            baseOnly
        }
        val cleaned = strippedExt
            .replace(Regex("[^a-zA-Z0-9._\\- ()]"), "_")
            .replace(Regex("\\.{2,}"), "_")
            .trim('.', ' ', '_')
            .take(120)
        val safeBase = if (cleaned.isBlank() || cleaned == "." || cleaned == "..") fallbackBase else cleaned
        return "$safeBase$ext"
    }

    /**
     * Safely reads the page count of a local PDF file using PdfRenderer with PDFBox fallback.
     */
    suspend fun getPageCount(file: File): Int = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext 0
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            if (renderer.pageCount > 0) return@withContext renderer.pageCount
        } catch (_: Throwable) {
        } finally {
            try { renderer?.close() } catch (_: Throwable) {}
            try { pfd?.close() } catch (_: Throwable) {}
        }
        try {
            com.tom_roush.pdfbox.pdmodel.PDDocument.load(file).use { doc ->
                return@withContext doc.numberOfPages
            }
        } catch (_: Throwable) {
            0
        }
    }

    /**
     * Copies a Uri content stream to a temporary local cache file while preserving file name and extension.
     * Uses a per-URI hash directory to prevent filename collisions when multiple files share the same display name.
     */
    suspend fun getLocalFileFromUri(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
        if (uri.scheme == "file" && uri.path != null) {
            return@withContext File(uri.path!!)
        }
        val uriHash = uri.toString().hashCode().toUInt().toString(16)
        val cacheDir = File(File(context.cacheDir, "imported_pdfs"), uriHash).apply { mkdirs() }
        var resolvedName: String? = null
        try {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1) {
                        resolvedName = cursor.getString(nameIdx)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving display name for URI", e)
        }

        val sanitizedName = resolvedName
            ?.replace('\\', '/')
            ?.substringAfterLast('/')
            ?.replace(Regex("[^a-zA-Z0-9._\\- ()]"), "_")
            ?.replace(Regex("\\.{2,}"), "_")
            ?.trim()
            ?.takeIf { it.isNotBlank() && it != "." && it != ".." }
            ?: "doc_${System.currentTimeMillis()}_${(1000..9999).random()}.pdf"

        val tempFile = File(cacheDir, sanitizedName)
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw java.io.IOException("Unable to open input stream for URI: $uri")
        inputStream.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        tempFile
    }

    /**
     * Renders a specific page thumbnail from PDF File.
     */
    suspend fun renderPageThumbnail(
        context: Context,
        file: File,
        pageIndex: Int,
        targetWidthDp: Int = 220
    ): Bitmap? = withContext(Dispatchers.IO) {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null
        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null

            page = renderer.openPage(pageIndex)
            val density = context.resources.displayMetrics.density
            val targetWidthPx = (targetWidthDp * density).toInt().coerceAtLeast(60)
            val pw = page.width.coerceAtLeast(1)
            val ph = page.height.coerceAtLeast(1)
            val aspectRatio = (ph.toFloat() / pw.toFloat()).coerceIn(0.25f, 4.0f)
            val targetHeightPx = (targetWidthPx * aspectRatio).toInt().coerceAtLeast(60)

            val bitmap = Bitmap.createBitmap(targetWidthPx, targetHeightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE) // White page background

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error rendering page thumbnail", e)
            null
        } finally {
            page?.close()
            renderer?.close()
            pfd?.close()
        }
    }

    /**
     * Renders up to [maxPages] page thumbnails from a single [PdfRenderer] session to avoid
     * repeatedly reopening the PDF file descriptor in loops.
     */
    suspend fun renderPageThumbnailsBatch(
        context: Context,
        file: File,
        maxPages: Int = 60,
        targetWidthDp: Int = 180
    ): Map<Int, Bitmap> = withContext(Dispatchers.IO) {
        if (!file.exists() || maxPages <= 0) return@withContext emptyMap()
        val result = LinkedHashMap<Int, Bitmap>()
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            val total = renderer.pageCount.coerceAtMost(maxPages)
            val density = context.resources.displayMetrics.density
            val baseWidthPx = (targetWidthDp * density).toInt().coerceAtLeast(80)
            val targetWidthPx = if (total > 30) (baseWidthPx * 0.75f).toInt().coerceAtLeast(80) else baseWidthPx

            for (pageIndex in 0 until total) {
                var page: PdfRenderer.Page? = null
                try {
                    page = renderer.openPage(pageIndex)
                    val pw = page.width.coerceAtLeast(1)
                    val ph = page.height.coerceAtLeast(1)
                    val aspectRatio = (ph.toFloat() / pw.toFloat()).coerceIn(0.25f, 4.0f)
                    val targetHeightPx = (targetWidthPx * aspectRatio).toInt().coerceIn(60, 1000)

                    val bitmap = Bitmap.createBitmap(targetWidthPx, targetHeightPx, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    result[pageIndex] = bitmap
                } catch (e: Throwable) {
                    Log.w(TAG, "Skipping thumbnail for page $pageIndex: ${e.localizedMessage}")
                } finally {
                    try { page?.close() } catch (_: Throwable) {}
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error rendering batch thumbnails", e)
        } finally {
            try { renderer?.close() } catch (_: Throwable) {}
            try { pfd?.close() } catch (_: Throwable) {}
        }
        result
    }

    /**
     * Copies an Image Uri content stream to a temporary local cache file.
     */
    suspend fun getLocalImageFileFromUri(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
        if (uri.scheme == "file" && uri.path != null) {
            return@withContext File(uri.path!!)
        }
        val cacheDir = File(context.cacheDir, "imported_images").apply { mkdirs() }
        val ext = when (context.contentResolver.getType(uri)) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> "jpg"
        }
        val tempFile = File(cacheDir, "img_${System.currentTimeMillis()}_${(1000..9999).random()}.$ext")
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw java.io.IOException("Unable to open image stream for URI: $uri")
        inputStream.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        tempFile
    }

    /**
     * Renders/downscales an image file into a thumbnail bitmap with EXIF orientation applied.
     */
    suspend fun renderImageThumbnail(
        context: Context,
        file: File,
        targetWidthDp: Int = 220
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val density = context.resources.displayMetrics.density
            val targetWidthPx = (targetWidthDp * density).toInt().coerceAtLeast(60)
            decodeScaledOrientedBitmap(file, maxDimension = targetWidthPx * 2)
        } catch (e: Exception) {
            Log.e(TAG, "Error rendering image thumbnail", e)
            null
        }
    }

    /**
     * Merges a list of ordered PdfPageSourceItems into a unified PDF file.
     * Supports both PDF page sources and Image sources (originalPageIndex = -1).
     */
    suspend fun mergeAndReorderPages(
        context: Context,
        pageItems: List<PdfPageSourceItem>,
        outputFileName: String
    ): File = withContext(Dispatchers.IO) {
        val outputDir = File(context.filesDir, "exports").apply { mkdirs() }
        val outputFile = File(outputDir, sanitizeOutputFileName(outputFileName, "PDFCraft_Merged"))
        
        val pdfDocument = PdfDocument()

        // Cache file descriptors and renderers per source file to avoid reopening
        val rendererMap = mutableMapOf<Uri, Pair<ParcelFileDescriptor, PdfRenderer>>()

        try {
            pageItems.forEachIndexed { newIndex, item ->
                if (item.originalPageIndex == -1) {
                    // This item is an Image source!
                    try {
                        val localImgFile = getLocalImageFileFromUri(context, item.sourceUri)
                        val bitmap = decodeScaledOrientedBitmap(localImgFile, maxDimension = 2400)
                            ?: context.contentResolver.openInputStream(item.sourceUri)?.use { input ->
                                android.graphics.BitmapFactory.decodeStream(input)
                            }

                        if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
                            // Apply rotation if requested
                            val finalBitmap = if (item.rotationDegrees % 360 != 0) {
                                val matrix = Matrix().apply { postRotate(item.rotationDegrees.toFloat()) }
                                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                            } else {
                                bitmap
                            }

                            try {
                                if (finalBitmap.width > 0 && finalBitmap.height > 0) {
                                    val pageInfo = PdfDocument.PageInfo.Builder(finalBitmap.width, finalBitmap.height, newIndex + 1).create()
                                    val pdfPage = pdfDocument.startPage(pageInfo)
                                    pdfPage.canvas.drawBitmap(finalBitmap, 0f, 0f, null)
                                    pdfDocument.finishPage(pdfPage)
                                }
                            } finally {
                                if (finalBitmap !== bitmap && !finalBitmap.isRecycled) finalBitmap.recycle()
                                if (!bitmap.isRecycled) bitmap.recycle()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to write image item to PDF page", e)
                    }
                } else {
                    // This item is a PDF page source!
                    var pair = rendererMap[item.sourceUri]
                    if (pair == null) {
                        val localFile = getLocalFileFromUri(context, item.sourceUri)
                        val pfd = ParcelFileDescriptor.open(localFile, ParcelFileDescriptor.MODE_READ_ONLY)
                        val renderer = PdfRenderer(pfd)
                        pair = Pair(pfd, renderer)
                        rendererMap[item.sourceUri] = pair
                    }

                    val renderer = pair.second
                    if (item.originalPageIndex >= 0 && item.originalPageIndex < renderer.pageCount) {
                        val page = renderer.openPage(item.originalPageIndex)
                        val pageWidth: Int
                        val pageHeight: Int
                        val bitmap: Bitmap
                        try {
                            pageWidth = page.width.coerceAtLeast(1)
                            pageHeight = page.height.coerceAtLeast(1)
                            bitmap = Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bitmap)
                            canvas.drawColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        } finally {
                            page.close()
                        }

                        // Apply rotation if requested
                        val finalBitmap = if (item.rotationDegrees % 360 != 0) {
                            val matrix = Matrix().apply { postRotate(item.rotationDegrees.toFloat()) }
                            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                        } else {
                            bitmap
                        }

                        try {
                            val pageInfo = PdfDocument.PageInfo.Builder(finalBitmap.width, finalBitmap.height, newIndex + 1).create()
                            val pdfPage = pdfDocument.startPage(pageInfo)
                            pdfPage.canvas.drawBitmap(finalBitmap, 0f, 0f, null)
                            pdfDocument.finishPage(pdfPage)
                        } finally {
                            if (finalBitmap !== bitmap && !finalBitmap.isRecycled) finalBitmap.recycle()
                            if (!bitmap.isRecycled) bitmap.recycle()
                        }
                    }
                }
            }

            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }
        } finally {
            pdfDocument.close()
            rendererMap.values.forEach { (pfd, renderer) ->
                try { renderer.close() } catch (ignored: Exception) {}
                try { pfd.close() } catch (ignored: Exception) {}
            }
        }

        outputFile
    }

    /**
     * Splits a PDF file by selecting specific page indices (with optional per-page rotations).
     */
    suspend fun splitPdf(
        context: Context,
        sourceUri: Uri,
        selectedPageIndices: List<Int>,
        outputFileName: String,
        pageRotations: Map<Int, Int> = emptyMap()
    ): File = withContext(Dispatchers.IO) {
        val localFile = getLocalFileFromUri(context, sourceUri)
        val localUri = Uri.fromFile(localFile)
        val pageItems = selectedPageIndices.mapIndexed { index, pageIdx ->
            PdfPageSourceItem(
                id = "split_$index",
                sourceTitle = localFile.name,
                sourceUri = localUri,
                originalPageIndex = pageIdx,
                rotationDegrees = pageRotations[pageIdx] ?: 0
            )
        }
        mergeAndReorderPages(context, pageItems, outputFileName)
    }

    /**
     * Splits a PDF file into individual page PDF files (with optional per-page rotations).
     * Returns a list of generated individual page files.
     */
    suspend fun splitPdfToIndividualPages(
        context: Context,
        sourceUri: Uri,
        selectedPageIndices: List<Int>,
        baseOutputName: String,
        pageRotations: Map<Int, Int> = emptyMap()
    ): List<File> = withContext(Dispatchers.IO) {
        val localFile = getLocalFileFromUri(context, sourceUri)
        val localUri = Uri.fromFile(localFile)
        val generatedFiles = mutableListOf<File>()
        val baseWithoutPdf = baseOutputName.replace(Regex("\\.pdf$", RegexOption.IGNORE_CASE), "")
        val cleanBaseName = sanitizeOutputFileName(baseWithoutPdf, "Split", "").ifBlank { "Split" }

        selectedPageIndices.forEach { pageIdx ->
            val pageItem = PdfPageSourceItem(
                id = "split_single_$pageIdx",
                sourceTitle = localFile.name,
                sourceUri = localUri,
                originalPageIndex = pageIdx,
                rotationDegrees = pageRotations[pageIdx] ?: 0
            )
            val outputFileName = "${cleanBaseName}_Page_${pageIdx + 1}.pdf"
            val file = mergeAndReorderPages(context, listOf(pageItem), outputFileName)
            generatedFiles.add(file)
        }

        generatedFiles
    }

    /**
     * Compresses a PDF file by re-sampling pages at a target DPI & embedding genuine JPEG streams via PDFBox.
     * Ensures output is never larger than the source PDF by falling back to a clean lossless copy if already compact.
     */
    suspend fun compressPdf(
        context: Context,
        sourceUri: Uri,
        preset: CompressionPreset,
        outputFileName: String
    ): File = withContext(Dispatchers.IO) {
        val localFile = getLocalFileFromUri(context, sourceUri)
        val pfd = ParcelFileDescriptor.open(localFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)

        val outputDir = File(context.filesDir, "exports").apply { mkdirs() }
        val outputFile = File(outputDir, sanitizeOutputFileName(outputFileName, "PDFCraft_Compressed"))
        val tempCandidate = File(outputDir, ".tmp_${System.currentTimeMillis()}_${outputFile.name}")

        try {
            val scaleFactor = (preset.targetDpi.toFloat() / 72f).coerceIn(0.6f, 3.0f)
            val maxDim = 2400

            com.tom_roush.pdfbox.pdmodel.PDDocument().use { pdDoc ->
                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    val pageW: Int
                    val pageH: Int
                    val bitmap: Bitmap
                    try {
                        pageW = page.width.coerceAtLeast(1)
                        pageH = page.height.coerceAtLeast(1)
                        var targetW = (pageW * scaleFactor).toInt().coerceAtLeast(100)
                        var targetH = (pageH * scaleFactor).toInt().coerceAtLeast(100)
                        if (targetW > maxDim || targetH > maxDim) {
                            val clampScale = maxDim.toFloat() / maxOf(targetW, targetH)
                            targetW = (targetW * clampScale).toInt().coerceAtLeast(100)
                            targetH = (targetH * clampScale).toInt().coerceAtLeast(100)
                        }

                        // PdfRenderer.Page.render strictly requires ARGB_8888 on Android devices
                        bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    } finally {
                        page.close()
                    }

                    val compressedBytes = try {
                        val compressedStream = java.io.ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, preset.jpegQuality.coerceIn(20, 95), compressedStream)
                        compressedStream.toByteArray()
                    } finally {
                        if (!bitmap.isRecycled) bitmap.recycle()
                    }

                    val pdImage = com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory.createFromByteArray(pdDoc, compressedBytes)
                    val pdPage = com.tom_roush.pdfbox.pdmodel.PDPage(
                        com.tom_roush.pdfbox.pdmodel.common.PDRectangle(pageW.toFloat(), pageH.toFloat())
                    )
                    pdDoc.addPage(pdPage)
                    com.tom_roush.pdfbox.pdmodel.PDPageContentStream(
                        pdDoc,
                        pdPage,
                        com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode.OVERWRITE,
                        true,
                        true
                    ).use { content ->
                        content.drawImage(pdImage, 0f, 0f, pageW.toFloat(), pageH.toFloat())
                    }
                }
                pdDoc.save(tempCandidate)
            }

            if (tempCandidate.exists() && tempCandidate.length() > 0 && tempCandidate.length() < localFile.length()) {
                tempCandidate.copyTo(outputFile, overwrite = true)
            } else {
                // Already compact (e.g. text-only vector PDF) — preserve lossless original to avoid bloating
                localFile.copyTo(outputFile, overwrite = true)
            }
        } finally {
            try { tempCandidate.delete() } catch (_: Throwable) {}
            try { renderer.close() } catch (_: Throwable) {}
            try { pfd.close() } catch (_: Throwable) {}
        }

        outputFile
    }

    /**
     * Extracts images from specified pages of a PDF and saves them as PNG/JPG files.
     */
    suspend fun extractImages(
        context: Context,
        sourceUri: Uri,
        selectedPageIndices: List<Int>,
        format: String = "PNG" // PNG or JPEG
    ): List<File> = withContext(Dispatchers.IO) {
        val localFile = getLocalFileFromUri(context, sourceUri)
        val pfd = ParcelFileDescriptor.open(localFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)

        val outputDir = File(context.filesDir, "extracted_images").apply { mkdirs() }
        val extractedFiles = mutableListOf<File>()

        val ext = if (format.equals("JPEG", ignoreCase = true)) "jpg" else "png"
        val mimeType = if (ext == "jpg") "image/jpeg" else "image/png"
        val compressFormat = if (format.equals("JPEG", ignoreCase = true)) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG

        try {
            selectedPageIndices.forEach { pageIdx ->
                if (pageIdx in 0 until renderer.pageCount) {
                    val page = renderer.openPage(pageIdx)
                    val bitmap: Bitmap
                    try {
                        val width = (page.width * 2).coerceIn(100, 3200)
                        val height = (page.height * 2).coerceIn(100, 4400)
                        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    } finally {
                        page.close()
                    }

                    try {
                        val imageFileName = "${localFile.nameWithoutExtension}_page_${pageIdx + 1}_${System.currentTimeMillis()}.$ext"
                        val imageFile = File(outputDir, imageFileName)
                        FileOutputStream(imageFile).use { out ->
                            bitmap.compress(compressFormat, 95, out)
                        }
                        extractedFiles.add(imageFile)

                        // Save copy to public Pictures/PDFCraft via Scoped Storage MediaStore on API 29+
                        try {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                val values = android.content.ContentValues().apply {
                                    put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, imageFileName)
                                    put(android.provider.MediaStore.Images.Media.MIME_TYPE, mimeType)
                                    put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "${android.os.Environment.DIRECTORY_PICTURES}/PDFCraft")
                                    put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                                }
                                val resolver = context.contentResolver
                                val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                                if (uri != null) {
                                    try {
                                        resolver.openOutputStream(uri)?.use { out ->
                                            bitmap.compress(compressFormat, 95, out)
                                        }
                                        values.clear()
                                        values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                                        resolver.update(uri, values, null, null)
                                    } catch (writeErr: Exception) {
                                        try { resolver.delete(uri, null, null) } catch (_: Exception) {}
                                        throw writeErr
                                    }
                                }
                            } else {
                                @Suppress("DEPRECATION")
                                val galleryDir = File(
                                    android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES),
                                    "PDFCraft"
                                ).apply { mkdirs() }
                                val galleryFile = File(galleryDir, imageFileName)
                                FileOutputStream(galleryFile).use { out ->
                                    bitmap.compress(compressFormat, 95, out)
                                }
                                android.media.MediaScannerConnection.scanFile(
                                    context,
                                    arrayOf(galleryFile.absolutePath),
                                    arrayOf(mimeType),
                                    null
                                )
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error saving image to Gallery folder", e)
                        }
                    } finally {
                        if (!bitmap.isRecycled) bitmap.recycle()
                    }
                }
            }
        } finally {
            try { renderer.close() } catch (_: Throwable) {}
            try { pfd.close() } catch (_: Throwable) {}
        }

        extractedFiles
    }

    /**
     * Helper to format bytes to human readable string.
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0 -> String.format(Locale.US, "%.2f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    /**
     * Data class for PDF text search results.
     */
    data class PdfSearchResult(
        val pageIndex: Int,
        val pageNumber: Int,
        val matchCount: Int,
        val snippet: String
    )

    /**
     * Performs text search across non-scanned PDF pages using PDFBox PDFTextStripper.
     */
    suspend fun searchTextInPdf(file: File, query: String): List<PdfSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank() || !file.exists()) return@withContext emptyList()
        val results = mutableListOf<PdfSearchResult>()
        try {
            com.tom_roush.pdfbox.pdmodel.PDDocument.load(file).use { pdDocument ->
                val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
                for (i in 0 until pdDocument.numberOfPages) {
                    stripper.startPage = i + 1
                    stripper.endPage = i + 1
                    val text = stripper.getText(pdDocument) ?: ""
                    if (text.contains(query, ignoreCase = true)) {
                        val lowerText = text.lowercase()
                        val lowerQuery = query.lowercase()
                        var count = 0
                        var idx = 0
                        while (true) {
                            idx = lowerText.indexOf(lowerQuery, idx)
                            if (idx == -1) break
                            count++
                            idx += lowerQuery.length
                        }

                        val matchIdx = lowerText.indexOf(lowerQuery)
                        val start = (matchIdx - 30).coerceAtLeast(0)
                        val end = (matchIdx + lowerQuery.length + 30).coerceAtMost(text.length)
                        val snippet = "..." + text.substring(start, end).replace("\n", " ").trim() + "..."

                        results.add(PdfSearchResult(pageIndex = i, pageNumber = i + 1, matchCount = count, snippet = snippet))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing text search in PDF", e)
        }
        return@withContext results
    }

    /**
     * Saves an image file to the device's public Gallery (Pictures/PDFCraft) via MediaStore API.
     */
    suspend fun saveImageToGallery(context: Context, imageFile: File): Uri? = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, imageFile.name)
                put(
                    android.provider.MediaStore.MediaColumns.MIME_TYPE,
                    if (imageFile.extension.equals("png", true)) "image/png" else "image/jpeg"
                )
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(
                        android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_PICTURES + "/PDFCraft"
                    )
                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                android.provider.MediaStore.Images.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val uri = resolver.insert(collection, contentValues)
            if (uri != null) {
                try {
                    resolver.openOutputStream(uri)?.use { output ->
                        imageFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(uri, contentValues, null, null)
                    }
                    return@withContext uri
                } catch (writeErr: Exception) {
                    try { resolver.delete(uri, null, null) } catch (_: Exception) {}
                    throw writeErr
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error saving image to gallery", e)
            null
        }
    }

    /**
     * Saves a generated PDF file to the device's public Downloads folder (Downloads/PDFCraft)
     * so users can immediately access their files in the system Files / Downloads app.
     */
    suspend fun savePdfToDownloads(context: Context, pdfFile: File): Uri? = withContext(Dispatchers.IO) {
        if (!pdfFile.exists()) return@withContext null
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val displayName = sanitizeOutputFileName(pdfFile.name, "PDFCraft_Document")
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(
                        android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_DOWNLOADS + "/PDFCraft"
                    )
                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val collection = android.provider.MediaStore.Downloads.getContentUri(
                    android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY
                )
                val uri = resolver.insert(collection, contentValues)
                if (uri != null) {
                    try {
                        resolver.openOutputStream(uri)?.use { output ->
                            pdfFile.inputStream().use { input ->
                                input.copyTo(output)
                            }
                        }
                        contentValues.clear()
                        contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(uri, contentValues, null, null)
                        return@withContext uri
                    } catch (writeErr: Exception) {
                        try { resolver.delete(uri, null, null) } catch (_: Exception) {}
                        throw writeErr
                    }
                }
            } else {
                val downloadsDir = File(
                    android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                    "PDFCraft"
                ).apply { mkdirs() }
                val destFile = File(downloadsDir, pdfFile.name)
                pdfFile.copyTo(destFile, overwrite = true)
                return@withContext Uri.fromFile(destFile)
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error saving PDF to public Downloads", e)
            null
        }
    }

    /**
     * Extracts document metadata (Title, Author, Subject, Keywords, Creator, Producer, Page Count, Dates)
     * using PDFBox with fallback to android.graphics.pdf.PdfRenderer.
     */
    suspend fun extractMetadata(file: File): PdfMetadata = withContext(Dispatchers.IO) {
        if (!file.exists()) {
            return@withContext PdfMetadata(
                fileName = file.name,
                filePath = file.absolutePath,
                fileSizeFormatted = "0 B",
                fileSizeBytes = 0L,
                title = null,
                author = null,
                subject = null,
                keywords = null,
                creator = null,
                producer = null,
                pageCount = 0,
                creationDateFormatted = null,
                modDateFormatted = null,
                isEncrypted = false
            )
        }

        val fileSize = file.length()
        val formattedSize = formatFileSize(fileSize)
        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

        var docTitle: String? = null
        var docAuthor: String? = null
        var docSubject: String? = null
        var docKeywords: String? = null
        var docCreator: String? = null
        var docProducer: String? = null
        var pageCount = 0
        var creationDateStr: String? = null
        var modDateStr: String? = null
        var encrypted = false
        var pdfVersionStr: String? = null
        var pageSizeFormatStr: String? = null
        var pageDimensionsStr: String? = null
        var canPrintPerm = true
        var canCopyPerm = true
        var canModifyPerm = true

        try {
            com.tom_roush.pdfbox.pdmodel.PDDocument.load(file).use { doc ->
                encrypted = doc.isEncrypted
                pageCount = doc.numberOfPages
                pdfVersionStr = doc.version.toString()

                val ap = doc.currentAccessPermission
                if (ap != null) {
                    canPrintPerm = ap.canPrint()
                    canCopyPerm = ap.canExtractContent()
                    canModifyPerm = ap.canModify()
                }

                if (pageCount > 0) {
                    try {
                        val firstPage = doc.getPage(0)
                        val mediaBox = firstPage.mediaBox
                        if (mediaBox != null) {
                            val w = mediaBox.width.toInt()
                            val h = mediaBox.height.toInt()
                            pageDimensionsStr = "$w × $h pt"
                            pageSizeFormatStr = when {
                                (w in 590..600 && h in 838..848) || (h in 590..600 && w in 838..848) -> "A4 (${if (w > h) "Landscape" else "Portrait"})"
                                (w in 608..616 && h in 788..796) || (h in 608..616 && w in 788..796) -> "US Letter (${if (w > h) "Landscape" else "Portrait"})"
                                (w in 608..616 && h in 1004..1012) || (h in 608..616 && w in 1004..1012) -> "US Legal"
                                else -> "Custom ($w × $h pt)"
                            }
                        }
                    } catch (_: Exception) {}
                }

                val info = doc.documentInformation
                if (info != null) {
                    docTitle = info.title?.takeIf { it.isNotBlank() }
                    docAuthor = info.author?.takeIf { it.isNotBlank() }
                    docSubject = info.subject?.takeIf { it.isNotBlank() }
                    docKeywords = info.keywords?.takeIf { it.isNotBlank() }
                    docCreator = info.creator?.takeIf { it.isNotBlank() }
                    docProducer = info.producer?.takeIf { it.isNotBlank() }

                    info.creationDate?.let { cal ->
                        creationDateStr = dateFormat.format(cal.time)
                    }
                    info.modificationDate?.let { cal ->
                        modDateStr = dateFormat.format(cal.time)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting PDFBox metadata: ${e.localizedMessage}")
            var fallbackPfd: ParcelFileDescriptor? = null
            var fallbackRenderer: PdfRenderer? = null
            try {
                fallbackPfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                fallbackRenderer = PdfRenderer(fallbackPfd)
                pageCount = fallbackRenderer.pageCount
                if (pageCount > 0) {
                    val p = fallbackRenderer.openPage(0)
                    try {
                        pageDimensionsStr = "${p.width} × ${p.height} pt"
                    } finally {
                        p.close()
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Fallback PdfRenderer error: ${t.localizedMessage}")
            } finally {
                try { fallbackRenderer?.close() } catch (_: Throwable) {}
                try { fallbackPfd?.close() } catch (_: Throwable) {}
            }
        }

        if (modDateStr == null && file.lastModified() > 0) {
            modDateStr = dateFormat.format(Date(file.lastModified()))
        }

        PdfMetadata(
            fileName = file.name,
            filePath = file.absolutePath,
            fileSizeFormatted = formattedSize,
            fileSizeBytes = fileSize,
            title = docTitle,
            author = docAuthor,
            subject = docSubject,
            keywords = docKeywords,
            creator = docCreator,
            producer = docProducer,
            pageCount = pageCount,
            creationDateFormatted = creationDateStr,
            modDateFormatted = modDateStr,
            isEncrypted = encrypted,
            pdfVersion = pdfVersionStr ?: "1.7",
            pageSizeFormat = pageSizeFormatStr,
            pageDimensions = pageDimensionsStr,
            canPrint = canPrintPerm,
            canCopyContent = canCopyPerm,
            canModify = canModifyPerm
        )
    }

    /**
     * Renders page 0 of a PDF file into a crisp thumbnail Bitmap with memory caching.
     */
    suspend fun generateFirstPageThumbnail(file: File, widthPx: Int = 220): Bitmap? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null

        val cacheKey = "${file.absolutePath}_${file.lastModified()}_$widthPx"
        thumbnailCache.get(cacheKey)?.let { cached ->
            if (!cached.isRecycled) return@withContext cached
        }

        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            if (renderer.pageCount > 0) {
                val page = renderer.openPage(0)
                val pw = if (page.width > 0) page.width else 100
                val ph = if (page.height > 0) page.height else 100
                val aspectRatio = (ph.toFloat() / pw.toFloat()).takeIf { !it.isNaN() && !it.isInfinite() } ?: 1.414f
                val targetHeight = (widthPx * aspectRatio).toInt().coerceIn(80, 500)

                val bitmap = Bitmap.createBitmap(widthPx, targetHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                thumbnailCache.put(cacheKey, bitmap)
                return@withContext bitmap
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error generating page 1 thumbnail for ${file.name}: ${t.localizedMessage}")
        } finally {
            try { renderer?.close() } catch (_: Throwable) {}
            try { pfd?.close() } catch (_: Throwable) {}
        }
        return@withContext null
    }

    /**
     * Encrypts and password-protects a PDF file with 128-bit AES encryption.
     */
    suspend fun encryptPdf(
        context: Context,
        sourceUri: Uri,
        userPassword: String,
        ownerPassword: String,
        outputFileName: String
    ): File = withContext(Dispatchers.IO) {
        val localFile = getLocalFileFromUri(context, sourceUri)
        val outputDir = File(context.filesDir, "exports").apply { mkdirs() }
        val outputFile = File(outputDir, sanitizeOutputFileName(outputFileName, "PDFCraft_Secure"))

        com.tom_roush.pdfbox.pdmodel.PDDocument.load(localFile).use { doc ->
            val ap = com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission().apply {
                setCanExtractContent(true)
                setCanAssembleDocument(true)
                setCanPrint(true)
            }
            val spp = com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy(ownerPassword, userPassword, ap).apply {
                encryptionKeyLength = 128
            }
            doc.protect(spp)
            doc.save(outputFile)
        }
        outputFile
    }
    /**
     * Watermarks all pages of a PDF with text.
     */
    suspend fun watermarkPdf(
        context: Context,
        sourceUri: Uri,
        watermarkText: String,
        outputFileName: String
    ): File = watermarkPdf(
        context = context,
        sourceUri = sourceUri,
        options = WatermarkOptions(text = watermarkText),
        outputFileName = outputFileName
    )

    suspend fun watermarkPdf(
        context: Context,
        sourceUri: Uri,
        options: WatermarkOptions,
        outputFileName: String
    ): File = withContext(Dispatchers.IO) {
        val localFile = getLocalFileFromUri(context, sourceUri)
        val outputDir = File(context.filesDir, "exports").apply { mkdirs() }
        val outputFile = File(outputDir, sanitizeOutputFileName(outputFileName, "PDFCraft_Watermarked"))

        com.tom_roush.pdfbox.pdmodel.PDDocument.load(localFile).use { doc ->
            applyWatermarkToDocument(context, doc, options)
            doc.save(outputFile)
        }
        outputFile
    }

    suspend fun generateWatermarkedPreview(
        context: Context,
        sourceUri: Uri,
        options: WatermarkOptions,
        widthPx: Int = 400
    ): Bitmap? = withContext(Dispatchers.IO) {
        var tempWatermarkedFile: File? = null
        var pfd: android.os.ParcelFileDescriptor? = null
        var renderer: android.graphics.pdf.PdfRenderer? = null
        try {
            val localFile = getLocalFileFromUri(context, sourceUri)
            val tempDir = File(context.cacheDir, "preview_watermark").apply { mkdirs() }
            tempWatermarkedFile = File(tempDir, "preview_temp_${System.currentTimeMillis()}.pdf")

            com.tom_roush.pdfbox.pdmodel.PDDocument.load(localFile).use { doc ->
                if (doc.numberOfPages == 0) return@withContext null
                while (doc.numberOfPages > 1) {
                    doc.removePage(1)
                }
                applyWatermarkToDocument(context, doc, options)
                doc.save(tempWatermarkedFile)
            }

            pfd = android.os.ParcelFileDescriptor.open(tempWatermarkedFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = android.graphics.pdf.PdfRenderer(pfd)
            if (renderer.pageCount == 0) return@withContext null

            val page = renderer.openPage(0)
            try {
                val pw = page.width.coerceAtLeast(1)
                val ph = page.height.coerceAtLeast(1)
                val targetHeight = (widthPx * (ph.toFloat() / pw.toFloat())).toInt().coerceIn(80, 1200)
                val bitmap = Bitmap.createBitmap(widthPx, targetHeight, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } finally {
                page.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating watermarked preview", e)
            null
        } finally {
            try { renderer?.close() } catch (_: Throwable) {}
            try { pfd?.close() } catch (_: Throwable) {}
            try { tempWatermarkedFile?.delete() } catch (_: Throwable) {}
        }
    }

    private suspend fun applyWatermarkToDocument(
        context: Context,
        doc: com.tom_roush.pdfbox.pdmodel.PDDocument,
        options: WatermarkOptions
    ) {
        val pageCount = doc.numberOfPages

        var pdImage: com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject? = null
        var rawBitmap: Bitmap? = null
        if (options.type == WatermarkType.IMAGE && options.imageUri != null) {
            try {
                val imgFile = getLocalImageFileFromUri(context, options.imageUri)
                rawBitmap = decodeScaledOrientedBitmap(imgFile, maxDimension = 1600, preserveAlpha = true)
                    ?: context.contentResolver.openInputStream(options.imageUri)?.use { android.graphics.BitmapFactory.decodeStream(it) }
                if (rawBitmap != null) {
                    pdImage = com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(doc, rawBitmap)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load watermark image", e)
            }
        }

        val font = com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD
        val rgb = options.textColorRgb
        val rFloat = (rgb.getOrElse(0) { 180 }.coerceIn(0, 255)) / 255f
        val gFloat = (rgb.getOrElse(1) { 180 }.coerceIn(0, 255)) / 255f
        val bFloat = (rgb.getOrElse(2) { 180 }.coerceIn(0, 255)) / 255f

        for (i in 0 until pageCount) {
            val shouldApply = when (options.pageRange) {
                WatermarkPageRange.ALL_PAGES -> true
                WatermarkPageRange.FIRST_PAGE_ONLY -> i == 0
                WatermarkPageRange.EVEN_PAGES -> (i + 1) % 2 == 0
                WatermarkPageRange.ODD_PAGES -> (i + 1) % 2 != 0
            }
            if (!shouldApply) continue

            val page = doc.getPage(i)
            val box = page.cropBox ?: page.mediaBox
            val originX = box.lowerLeftX
            val originY = box.lowerLeftY
            val width = box.width
            val height = box.height

            com.tom_roush.pdfbox.pdmodel.PDPageContentStream(
                doc,
                page,
                com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND,
                true,
                true
            ).use { contentStream ->
                val gState = com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState().apply {
                    nonStrokingAlphaConstant = options.opacity.coerceIn(0.05f, 1.0f)
                    strokingAlphaConstant = options.opacity.coerceIn(0.05f, 1.0f)
                }
                contentStream.setGraphicsStateParameters(gState)

                if (options.type == WatermarkType.TEXT) {
                    val rawText = options.text.ifBlank { "CONFIDENTIAL" }
                    val textToDraw = toWinAnsiSafeText(rawText, font).ifBlank { "CONFIDENTIAL" }
                    contentStream.setNonStrokingColor(rFloat, gFloat, bFloat)
                    contentStream.setFont(font, options.textSize)

                    val textWidth = (font.getStringWidth(textToDraw) / 1000f) * options.textSize
                    val capHeight = font.fontDescriptor?.capHeight ?: 718f
                    val textHeight = (capHeight / 1000f) * options.textSize
                    val rad = Math.toRadians(options.rotationAngle.toDouble())

                    if (options.position == WatermarkPosition.TILE) {
                        val xStep = maxOf(textWidth * 1.4f, 160f)
                        val yStep = maxOf(textHeight * 3.5f, 120f)
                        var y = originY + 40f
                        while (y < originY + height) {
                            var x = originX + 30f
                            while (x < originX + width) {
                                contentStream.beginText()
                                val matrix = com.tom_roush.pdfbox.util.Matrix.getRotateInstance(rad, x, y)
                                contentStream.setTextMatrix(matrix)
                                contentStream.showText(textToDraw)
                                contentStream.endText()
                                x += xStep
                            }
                            y += yStep
                        }
                    } else {
                        val (relX, relY) = calculateTextCoordinates(options.position, width, height, textWidth, textHeight)
                        val matrix = if (options.position == WatermarkPosition.CENTER) {
                            val centerX = originX + width / 2f
                            val centerY = originY + height / 2f
                            com.tom_roush.pdfbox.util.Matrix.getTranslateInstance(centerX, centerY).apply {
                                concatenate(com.tom_roush.pdfbox.util.Matrix.getRotateInstance(rad, 0f, 0f))
                                concatenate(com.tom_roush.pdfbox.util.Matrix.getTranslateInstance(-textWidth / 2f, -textHeight / 2f))
                            }
                        } else {
                            com.tom_roush.pdfbox.util.Matrix.getRotateInstance(rad, originX + relX, originY + relY)
                        }
                        contentStream.beginText()
                        contentStream.setTextMatrix(matrix)
                        contentStream.showText(textToDraw)
                        contentStream.endText()
                    }
                } else if (options.type == WatermarkType.IMAGE && pdImage != null && rawBitmap != null && rawBitmap.width > 0 && rawBitmap.height > 0) {
                    val imgAspect = rawBitmap.height.toFloat() / rawBitmap.width.toFloat()
                    var imgWidth = (width * options.imageScale.coerceIn(0.08f, 1.0f)).coerceAtLeast(24f)
                    var imgHeight = imgWidth * imgAspect
                    if (imgHeight > height * 0.9f) {
                        val clamp = (height * 0.9f) / imgHeight
                        imgWidth *= clamp
                        imgHeight *= clamp
                    }
                    val rad = Math.toRadians(options.rotationAngle.toDouble())

                    if (options.position == WatermarkPosition.TILE) {
                        val xStep = maxOf(imgWidth * 1.5f, 120f)
                        val yStep = maxOf(imgHeight * 1.5f, 120f)
                        var y = originY + 40f
                        while (y < originY + height) {
                            var x = originX + 40f
                            while (x < originX + width) {
                                val matrix = com.tom_roush.pdfbox.util.Matrix.getRotateInstance(rad, x, y)
                                matrix.scale(imgWidth, imgHeight)
                                contentStream.drawImage(pdImage, matrix)
                                x += xStep
                            }
                            y += yStep
                        }
                    } else {
                        val (relX, relY) = calculateImageCoordinates(options.position, width, height, imgWidth, imgHeight)
                        val matrix = if (options.position == WatermarkPosition.CENTER) {
                            val centerX = originX + width / 2f
                            val centerY = originY + height / 2f
                            com.tom_roush.pdfbox.util.Matrix.getTranslateInstance(centerX, centerY).apply {
                                concatenate(com.tom_roush.pdfbox.util.Matrix.getRotateInstance(rad, 0f, 0f))
                                concatenate(com.tom_roush.pdfbox.util.Matrix.getTranslateInstance(-imgWidth / 2f, -imgHeight / 2f))
                                scale(imgWidth, imgHeight)
                            }
                        } else {
                            com.tom_roush.pdfbox.util.Matrix.getRotateInstance(rad, originX + relX, originY + relY).apply {
                                scale(imgWidth, imgHeight)
                            }
                        }
                        contentStream.drawImage(pdImage, matrix)
                    }
                }
            }
        }

        rawBitmap?.let {
            try { it.recycle() } catch (_: Exception) {}
        }
    }

    private fun calculateTextCoordinates(
        position: WatermarkPosition,
        width: Float,
        height: Float,
        textWidth: Float,
        textHeight: Float
    ): Pair<Float, Float> {
        return when (position) {
            WatermarkPosition.CENTER -> Pair((width - textWidth) / 2f, (height - textHeight) / 2f)
            WatermarkPosition.TOP_LEFT -> Pair(40f, height - 60f)
            WatermarkPosition.TOP_RIGHT -> Pair(maxOf(40f, width - textWidth - 40f), height - 60f)
            WatermarkPosition.BOTTOM_LEFT -> Pair(40f, 40f)
            WatermarkPosition.BOTTOM_RIGHT -> Pair(maxOf(40f, width - textWidth - 40f), 40f)
            WatermarkPosition.TILE -> Pair((width - textWidth) / 2f, (height - textHeight) / 2f)
        }
    }

    private fun calculateImageCoordinates(
        position: WatermarkPosition,
        width: Float,
        height: Float,
        imgWidth: Float,
        imgHeight: Float
    ): Pair<Float, Float> {
        return when (position) {
            WatermarkPosition.CENTER -> Pair((width - imgWidth) / 2f, maxOf(40f, (height - imgHeight) / 2f))
            WatermarkPosition.TOP_LEFT -> Pair(40f, maxOf(40f, height - imgHeight - 40f))
            WatermarkPosition.TOP_RIGHT -> Pair(maxOf(40f, width - imgWidth - 40f), maxOf(40f, height - imgHeight - 40f))
            WatermarkPosition.BOTTOM_LEFT -> Pair(40f, 40f)
            WatermarkPosition.BOTTOM_RIGHT -> Pair(maxOf(40f, width - imgWidth - 40f), 40f)
            WatermarkPosition.TILE -> Pair((width - imgWidth) / 2f, maxOf(40f, (height - imgHeight) / 2f))
        }
    }

    /**
     * Adds a digital signature text box to the last page of a PDF.
     */
    suspend fun signPdf(
        context: Context,
        sourceUri: Uri,
        signerName: String,
        reason: String,
        outputFileName: String
    ): File = withContext(Dispatchers.IO) {
        val localFile = getLocalFileFromUri(context, sourceUri)
        val outputDir = File(context.filesDir, "exports").apply { mkdirs() }
        val outputFile = File(outputDir, sanitizeOutputFileName(outputFileName, "PDFCraft_Signed"))

        com.tom_roush.pdfbox.pdmodel.PDDocument.load(localFile).use { doc ->
            require(doc.numberOfPages > 0) { "PDF has no pages to sign." }
            val lastPage = doc.getPage(doc.numberOfPages - 1)
            val box = lastPage.cropBox ?: lastPage.mediaBox
            val fontBold = com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD
            val fontRegular = com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA

            val safeSigner = toWinAnsiSafeText(signerName.ifBlank { "Authorized Signer" }, fontBold)
            val safeReason = toWinAnsiSafeText(reason.ifBlank { "Approved" }, fontRegular)
            val dateLine = toWinAnsiSafeText(
                "Date: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}",
                fontRegular
            )

            val boxX = box.lowerLeftX + 42f
            val boxY = box.lowerLeftY + 36f
            val boxW = 260f
            val boxH = 56f

            com.tom_roush.pdfbox.pdmodel.PDPageContentStream(
                doc,
                lastPage,
                com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND,
                true,
                true
            ).use { contentStream ->
                // Clean signature stamp frame
                contentStream.setNonStrokingColor(246f / 255f, 252f / 255f, 249f / 255f)
                contentStream.addRect(boxX, boxY, boxW, boxH)
                contentStream.fill()

                contentStream.setNonStrokingColor(16f / 255f, 185f / 255f, 129f / 255f)
                contentStream.addRect(boxX, boxY, 4f, boxH)
                contentStream.fill()

                contentStream.setStrokingColor(16f / 255f, 185f / 255f, 129f / 255f)
                contentStream.setLineWidth(0.8f)
                contentStream.addRect(boxX, boxY, boxW, boxH)
                contentStream.stroke()

                contentStream.beginText()
                contentStream.setFont(fontBold, 9.5f)
                contentStream.setNonStrokingColor(15f / 255f, 23f / 255f, 42f / 255f)
                contentStream.newLineAtOffset(boxX + 12f, boxY + 40f)
                contentStream.showText("Digitally Signed by: $safeSigner")
                contentStream.setFont(fontRegular, 8.5f)
                contentStream.setNonStrokingColor(51f / 255f, 65f / 255f, 85f / 255f)
                contentStream.newLineAtOffset(0f, -13f)
                contentStream.showText("Reason: $safeReason")
                contentStream.newLineAtOffset(0f, -12f)
                contentStream.showText(dateLine)
                contentStream.endText()
            }

            doc.save(outputFile)
        }
        outputFile
    }

    /**
     * Converts any input file (PDF, Images like JPG/PNG/WEBP/BMP/GIF/HEIC, or Text/Document files) into a standard PDF file.
     */
    suspend fun convertAnyFileToPdf(context: Context, sourceUri: Uri): File = withContext(Dispatchers.IO) {
        val mimeType = context.contentResolver.getType(sourceUri) ?: ""
        val localFile = getLocalFileFromUri(context, sourceUri)
        
        if (mimeType.contains("pdf", ignoreCase = true) || localFile.name.lowercase().endsWith(".pdf")) {
            return@withContext localFile
        }

        val outputDir = File(context.filesDir, "converted").apply { mkdirs() }
        val outputFile = File(outputDir, sanitizeOutputFileName("Converted_${localFile.nameWithoutExtension}_${System.currentTimeMillis()}", "Converted"))

        val pdfDocument = android.graphics.pdf.PdfDocument()
        try {
            val isImage = mimeType.startsWith("image/", ignoreCase = true) ||
                    localFile.name.lowercase().matches(Regex(".*\\.(jpg|jpeg|png|webp|bmp|gif|heic|heif)$"))

            if (isImage) {
                var bitmap = decodeScaledOrientedBitmap(localFile, maxDimension = 2400)
                if (bitmap == null) {
                    context.contentResolver.openInputStream(sourceUri)?.use { input ->
                        bitmap = android.graphics.BitmapFactory.decodeStream(input)
                    }
                }

                val bmp = bitmap ?: throw java.io.IOException("Unable to decode image file: ${localFile.name}")
                if (bmp.width <= 0 || bmp.height <= 0) {
                    try { bmp.recycle() } catch (_: Exception) {}
                    throw java.io.IOException("Invalid image dimensions for: ${localFile.name}")
                }
                try {
                    val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(bmp.width, bmp.height, 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    page.canvas.drawBitmap(bmp, 0f, 0f, null)
                    pdfDocument.finishPage(page)
                } finally {
                    try { bmp.recycle() } catch (_: Exception) {}
                }
            } else {
                val text = try {
                    localFile.readText(Charsets.UTF_8)
                } catch (e: Exception) {
                    try {
                        localFile.readBytes().toString(Charsets.ISO_8859_1)
                    } catch (_: Exception) {
                        "Cannot parse file text content"
                    }
                }
                val paint = android.text.TextPaint().apply {
                    textSize = 13f
                    color = android.graphics.Color.BLACK
                    isAntiAlias = true
                }
                val pageWidth = 595
                val pageHeight = 842
                val margin = 40f
                val maxTextWidth = pageWidth - 2 * margin

                // Word wrap text lines
                val wrappedLines = mutableListOf<String>()
                val rawLines = text.split("\n")
                for (rawLine in rawLines) {
                    if (rawLine.isEmpty()) {
                        wrappedLines.add("")
                        continue
                    }
                    var currentLine = StringBuilder()
                    val words = rawLine.split(Regex("\\s+"))
                    for (word in words) {
                        val testString = if (currentLine.isEmpty()) word else "$currentLine $word"
                        if (paint.measureText(testString) <= maxTextWidth) {
                            if (currentLine.isNotEmpty()) currentLine.append(" ")
                            currentLine.append(word)
                        } else {
                            if (currentLine.isNotEmpty()) {
                                wrappedLines.add(currentLine.toString())
                                currentLine = StringBuilder(word)
                            } else {
                                wrappedLines.add(word)
                            }
                        }
                    }
                    if (currentLine.isNotEmpty()) {
                        wrappedLines.add(currentLine.toString())
                    }
                }

                var pageNum = 1
                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                var page = pdfDocument.startPage(pageInfo)
                var canvas = page.canvas

                var y = 50f
                val lineHeight = 18f

                for (line in wrappedLines) {
                    if (y > pageHeight - margin) {
                        pdfDocument.finishPage(page)
                        pageNum++
                        val nextPageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                        page = pdfDocument.startPage(nextPageInfo)
                        canvas = page.canvas
                        y = 50f
                    }
                    canvas.drawText(line, margin, y, paint)
                    y += lineHeight
                }
                pdfDocument.finishPage(page)
            }

            FileOutputStream(outputFile).use { fos ->
                pdfDocument.writeTo(fos)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert file to PDF: ${e.localizedMessage}", e)
            throw e
        } finally {
            pdfDocument.close()
        }

        outputFile
    }

    suspend fun readPdfMetadata(
        context: Context,
        sourceUri: Uri
    ): PdfMetadata = withContext(Dispatchers.IO) {
        val file = getLocalFileFromUri(context, sourceUri)
        var metadata = PdfMetadata(
            fileName = file.name,
            filePath = file.absolutePath,
            fileSizeFormatted = formatFileSize(file.length()),
            fileSizeBytes = file.length(),
            title = "",
            author = "",
            subject = "",
            keywords = "",
            creator = "PDFCraft Studio",
            producer = "Apache PDFBox",
            pageCount = 0,
            creationDateFormatted = "Unknown",
            modDateFormatted = "Unknown",
            isEncrypted = false
        )
        try {
            com.tom_roush.pdfbox.pdmodel.PDDocument.load(file).use { doc ->
                val info = doc.documentInformation
                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                val cDateStr = info?.creationDate?.time?.let { dateFormat.format(it) } ?: "Unknown"
                val mDateStr = info?.modificationDate?.time?.let { dateFormat.format(it) } ?: "Unknown"

                metadata = PdfMetadata(
                    fileName = file.name,
                    filePath = file.absolutePath,
                    fileSizeFormatted = formatFileSize(file.length()),
                    fileSizeBytes = file.length(),
                    title = info?.title ?: "",
                    author = info?.author ?: "",
                    subject = info?.subject ?: "",
                    keywords = info?.keywords ?: "",
                    creator = info?.creator ?: "PDFCraft Studio",
                    producer = info?.producer ?: "Apache PDFBox",
                    pageCount = doc.numberOfPages,
                    creationDateFormatted = cDateStr,
                    modDateFormatted = mDateStr,
                    isEncrypted = doc.isEncrypted
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading PDF metadata", e)
        }
        metadata
    }

    suspend fun updatePdfMetadata(
        context: Context,
        sourceUri: Uri,
        metadata: PdfMetadata,
        outputFileName: String
    ): File = withContext(Dispatchers.IO) {
        val localFile = getLocalFileFromUri(context, sourceUri)
        val outputDir = File(context.filesDir, "exports").apply { mkdirs() }
        val outputFile = File(outputDir, sanitizeOutputFileName(outputFileName, "PDFCraft_Metadata"))

        com.tom_roush.pdfbox.pdmodel.PDDocument.load(localFile).use { doc ->
            var info = doc.documentInformation
            if (info == null) {
                info = com.tom_roush.pdfbox.pdmodel.PDDocumentInformation()
            }
            info.title = metadata.title
            info.author = metadata.author
            info.subject = metadata.subject
            info.keywords = metadata.keywords
            info.modificationDate = java.util.Calendar.getInstance()
            doc.documentInformation = info
            doc.save(outputFile)
        }
        outputFile
    }

    suspend fun rotatePdfPages(
        context: Context,
        sourceUri: Uri,
        pageRotations: Map<Int, Int>,
        outputName: String = "PDFCraft_Rotated.pdf",
        deletedPages: Set<Int> = emptySet()
    ): File = withContext(Dispatchers.IO) {
        val file = getLocalFileFromUri(context, sourceUri)
        applyPageRotationsAndDeletions(
            context = context,
            sourceFile = file,
            pageRotations = pageRotations,
            deletedPages = deletedPages,
            outputName = outputName
        )
    }

    /**
     * Applies per-page rotations and removes any deleted pages from a PDF document losslessly via PDFBox.
     */
    suspend fun applyPageRotationsAndDeletions(
        context: Context,
        sourceFile: File,
        pageRotations: Map<Int, Int>,
        deletedPages: Set<Int>,
        outputName: String = "Modified_${sourceFile.name}"
    ): File = withContext(Dispatchers.IO) {
        val outputDir = File(context.filesDir, "exports").apply { mkdirs() }
        val outputFile = File(outputDir, sanitizeOutputFileName(outputName, "PDFCraft_Modified"))

        com.tom_roush.pdfbox.pdmodel.PDDocument.load(sourceFile).use { doc ->
            val pageCount = doc.numberOfPages
            for (i in 0 until pageCount) {
                val additionalDegrees = pageRotations[i] ?: 0
                if (additionalDegrees % 360 != 0) {
                    val page = doc.getPage(i)
                    val currentRotation = page.rotation
                    val newRotation = (currentRotation + additionalDegrees) % 360
                    page.rotation = if (newRotation < 0) newRotation + 360 else newRotation
                }
            }
            if (deletedPages.isNotEmpty()) {
                val sortedToDelete = deletedPages.filter { it in 0 until pageCount }.sortedDescending()
                for (delIdx in sortedToDelete) {
                    if (doc.numberOfPages > 1) {
                        doc.removePage(delIdx)
                    }
                }
            }
            doc.save(outputFile)
        }
        outputFile
    }

    /**
     * Converts a list of image Uris (photos/gallery images) into a multi-page PDF document.
     */
    suspend fun convertImagesToPdf(
        context: Context,
        imageUris: List<Uri>,
        outputName: String = "PDFCraft_Images.pdf"
    ): File = withContext(Dispatchers.IO) {
        val outputDir = File(context.filesDir, "exports").apply { mkdirs() }
        val outputFile = File(outputDir, sanitizeOutputFileName(outputName, "PDFCraft_Images"))

        com.tom_roush.pdfbox.pdmodel.PDDocument().use { pdDoc ->
            var addedCount = 0
            for (uri in imageUris) {
                var bitmap: Bitmap? = null
                try {
                    val localFile = getLocalFileFromUri(context, uri)
                    bitmap = decodeScaledOrientedBitmap(localFile, maxDimension = 2400)
                        ?: context.contentResolver.openInputStream(uri)?.use {
                            android.graphics.BitmapFactory.decodeStream(it)
                        }

                    val bmp = bitmap
                    if (bmp != null && bmp.width > 0 && bmp.height > 0) {
                        val pdImage = com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory.createFromImage(pdDoc, bmp, 0.88f)
                        val page = com.tom_roush.pdfbox.pdmodel.PDPage(
                            com.tom_roush.pdfbox.pdmodel.common.PDRectangle(
                                bmp.width.toFloat(),
                                bmp.height.toFloat()
                            )
                        )
                        pdDoc.addPage(page)
                        com.tom_roush.pdfbox.pdmodel.PDPageContentStream(
                            pdDoc,
                            page,
                            com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode.OVERWRITE,
                            true,
                            true
                        ).use { content ->
                            content.drawImage(pdImage, 0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
                        }
                        addedCount++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error adding image to PDF: $uri", e)
                } finally {
                    try {
                        bitmap?.recycle()
                    } catch (_: Exception) {}
                }
            }
            require(addedCount > 0) { "No valid images could be converted to PDF." }
            pdDoc.save(outputFile)
        }
        outputFile
    }

    /**
     * Decodes a local image file safely within maxDimension and applies EXIF orientation to prevent OOM and rotated photos.
     */
    private fun decodeScaledOrientedBitmap(
        file: File,
        maxDimension: Int,
        preserveAlpha: Boolean = false
    ): Bitmap? {
        val boundsOpts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null

        var sampleSize = 1
        while ((boundsOpts.outWidth / sampleSize) > maxDimension || (boundsOpts.outHeight / sampleSize) > maxDimension) {
            sampleSize *= 2
        }

        val decodeOpts = android.graphics.BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = if (preserveAlpha) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
        }
        val decoded = android.graphics.BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return null

        val rotationDegrees = try {
            val exif = android.media.ExifInterface(file.absolutePath)
            when (exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } catch (_: Exception) {
            0f
        }

        if (rotationDegrees == 0f) return decoded
        val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees) }
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (rotated !== decoded) {
            decoded.recycle()
        }
        return rotated
    }

    /**
     * Sanitizes text so standard PDF Type1 WinAnsi fonts never throw IllegalArgumentException on unsupported Unicode glyphs.
     */
    private fun toWinAnsiSafeText(input: String, font: com.tom_roush.pdfbox.pdmodel.font.PDFont): String {
        val normalized = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFKD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        val sb = StringBuilder(normalized.length)
        for (ch in normalized) {
            if (ch == '\n' || ch == '\r' || ch == '\t') {
                sb.append(' ')
                continue
            }
            val s = ch.toString()
            try {
                font.encode(s)
                sb.append(ch)
            } catch (_: Exception) {
                // Skip or replace unsupported glyph cleanly
                if (ch.code > 127) sb.append('?')
            }
        }
        return sb.toString().trim()
    }

    /**
     * Overlays customizable page numbers onto the header or footer of existing PDF documents.
     */
    suspend fun addPageNumbersToPdf(
        context: Context,
        sourceUri: Uri,
        options: PageNumberOptions,
        outputName: String = "PDFCraft_Numbered.pdf"
    ): File = withContext(Dispatchers.IO) {
        val file = getLocalFileFromUri(context, sourceUri)
        val outputDir = File(context.filesDir, "exports").apply { mkdirs() }
        val outputFile = File(outputDir, sanitizeOutputFileName(outputName, "PDFCraft_Numbered"))

        com.tom_roush.pdfbox.pdmodel.PDDocument.load(file).use { pdDoc ->
            val totalPages = pdDoc.numberOfPages
            val font = com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD
            val rgb = options.textColorRgb
            val rFloat = (rgb.getOrElse(0) { 80 }.coerceIn(0, 255)) / 255f
            val gFloat = (rgb.getOrElse(1) { 80 }.coerceIn(0, 255)) / 255f
            val bFloat = (rgb.getOrElse(2) { 80 }.coerceIn(0, 255)) / 255f
            val capHeight = font.fontDescriptor?.capHeight ?: 718f

            for (i in 0 until totalPages) {
                val pageNum = i + options.startPageNumber
                val rawPageText = options.formatPattern
                    .replace("{P}", pageNum.toString())
                    .replace("{N}", totalPages.toString())
                val pageText = toWinAnsiSafeText(rawPageText, font).ifBlank { "$pageNum" }

                val page = pdDoc.getPage(i)
                val box = page.cropBox ?: page.mediaBox
                val width = box.width
                val height = box.height

                val textWidth = (font.getStringWidth(pageText) / 1000f) * options.fontSize
                val textHeight = (capHeight / 1000f) * options.fontSize

                val margin = options.marginPt

                val isHeader = options.position in listOf(
                    PageNumberPosition.HEADER_LEFT,
                    PageNumberPosition.HEADER_CENTER,
                    PageNumberPosition.HEADER_RIGHT
                )

                val x = box.lowerLeftX + when (options.position) {
                    PageNumberPosition.HEADER_LEFT, PageNumberPosition.FOOTER_LEFT -> margin
                    PageNumberPosition.HEADER_CENTER, PageNumberPosition.FOOTER_CENTER -> (width - textWidth) / 2f
                    PageNumberPosition.HEADER_RIGHT, PageNumberPosition.FOOTER_RIGHT -> width - margin - textWidth
                }

                val y = box.lowerLeftY + if (isHeader) height - margin - textHeight else margin

                com.tom_roush.pdfbox.pdmodel.PDPageContentStream(
                    pdDoc,
                    page,
                    com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                ).use { contentStream ->
                    contentStream.setNonStrokingColor(rFloat, gFloat, bFloat)
                    contentStream.setFont(font, options.fontSize)
                    contentStream.beginText()
                    contentStream.newLineAtOffset(x, y)
                    contentStream.showText(pageText)
                    contentStream.endText()
                }
            }
            pdDoc.save(outputFile)
        }
        outputFile
    }
}

enum class PageNumberPosition {
    HEADER_LEFT, HEADER_CENTER, HEADER_RIGHT,
    FOOTER_LEFT, FOOTER_CENTER, FOOTER_RIGHT
}

data class PageNumberOptions(
    val formatPattern: String = "Page {P} of {N}",
    val position: PageNumberPosition = PageNumberPosition.FOOTER_CENTER,
    val startPageNumber: Int = 1,
    val fontSize: Float = 10f,
    val marginPt: Float = 25f,
    val textColorRgb: IntArray = intArrayOf(80, 80, 80)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PageNumberOptions
        if (formatPattern != other.formatPattern) return false
        if (position != other.position) return false
        if (startPageNumber != other.startPageNumber) return false
        if (fontSize != other.fontSize) return false
        if (marginPt != other.marginPt) return false
        if (!textColorRgb.contentEquals(other.textColorRgb)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = formatPattern.hashCode()
        result = 31 * result + position.hashCode()
        result = 31 * result + startPageNumber
        result = 31 * result + fontSize.hashCode()
        result = 31 * result + marginPt.hashCode()
        result = 31 * result + textColorRgb.contentHashCode()
        return result
    }
}

enum class WatermarkType { TEXT, IMAGE }

enum class WatermarkPosition {
    CENTER,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    TILE
}

enum class WatermarkPageRange {
    ALL_PAGES,
    FIRST_PAGE_ONLY,
    EVEN_PAGES,
    ODD_PAGES
}

data class WatermarkOptions(
    val type: WatermarkType = WatermarkType.TEXT,
    val text: String = "CONFIDENTIAL",
    val textSize: Float = 36f,
    val textColorRgb: IntArray = intArrayOf(180, 180, 180),
    val imageUri: Uri? = null,
    val imageScale: Float = 0.4f,
    val opacity: Float = 0.3f,
    val rotationAngle: Float = 45f,
    val position: WatermarkPosition = WatermarkPosition.CENTER,
    val pageRange: WatermarkPageRange = WatermarkPageRange.ALL_PAGES
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as WatermarkOptions

        if (type != other.type) return false
        if (text != other.text) return false
        if (textSize != other.textSize) return false
        if (!textColorRgb.contentEquals(other.textColorRgb)) return false
        if (imageUri != other.imageUri) return false
        if (imageScale != other.imageScale) return false
        if (opacity != other.opacity) return false
        if (rotationAngle != other.rotationAngle) return false
        if (position != other.position) return false
        if (pageRange != other.pageRange) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + textSize.hashCode()
        result = 31 * result + textColorRgb.contentHashCode()
        result = 31 * result + (imageUri?.hashCode() ?: 0)
        result = 31 * result + imageScale.hashCode()
        result = 31 * result + opacity.hashCode()
        result = 31 * result + rotationAngle.hashCode()
        result = 31 * result + position.hashCode()
        result = 31 * result + pageRange.hashCode()
        return result
    }
}

