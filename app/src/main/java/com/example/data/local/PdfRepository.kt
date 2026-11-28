package com.example.data.local

import android.content.Context
import android.net.Uri
import com.example.data.pdf.CompressionPreset
import com.example.data.pdf.PdfEngine
import com.example.data.pdf.PdfPageSourceItem
import kotlinx.coroutines.flow.Flow
import java.io.File

class PdfRepository(
    private val dao: PdfProjectDao,
    private val recentDao: RecentFileDao
) {

    val allProjects: Flow<List<PdfProjectEntity>> = dao.getAllProjects()
    val favoriteProjects: Flow<List<PdfProjectEntity>> = dao.getFavoriteProjects()
    val recentFiles: Flow<List<RecentFileEntity>> = recentDao.getRecentFiles()

    suspend fun recordRecentFileAccess(
        filePath: String,
        fileName: String,
        fileSizeFormatted: String = "",
        pageCount: Int = 0
    ) {
        recentDao.insertOrUpdate(
            RecentFileEntity(
                filePath = filePath,
                fileName = fileName,
                fileSizeFormatted = fileSizeFormatted,
                pageCount = pageCount,
                lastAccessedTimestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun removeRecentFile(filePath: String) {
        recentDao.deleteByPath(filePath)
    }

    suspend fun removeRecentFiles(filePaths: List<String>) {
        recentDao.deleteByPaths(filePaths)
    }

    fun searchRecentFiles(query: String): Flow<List<RecentFileEntity>> {
        return if (query.isBlank()) recentDao.getRecentFiles() else recentDao.searchRecentFiles(query)
    }

    suspend fun clearRecentFiles() {
        recentDao.clearAll()
    }

    fun getProjectsByType(type: PdfOperationType): Flow<List<PdfProjectEntity>> =
        dao.getProjectsByType(type)

    suspend fun saveProjectRecord(project: PdfProjectEntity): Long {
        val id = dao.insertProject(project)
        if (project.outputFilePath.isNotBlank()) {
            recordRecentFileAccess(
                filePath = project.outputFilePath,
                fileName = project.title,
                fileSizeFormatted = PdfEngine.formatFileSize(project.resultSizeBytes),
                pageCount = project.pageCount
            )
        }
        return id
    }

    suspend fun toggleFavorite(id: Long, isFavorite: Boolean) =
        dao.setFavorite(id, isFavorite)

    suspend fun deleteProject(id: Long) =
        dao.deleteProjectById(id)

    suspend fun deleteProjectWithFile(id: Long, filePath: String, deleteFromDisk: Boolean = true) {
        if (deleteFromDisk && filePath.isNotBlank()) {
            try {
                val file = File(filePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        dao.deleteProjectById(id)
        if (filePath.isNotBlank()) {
            recentDao.deleteByPath(filePath)
        }
    }

    suspend fun renameProjectFile(id: Long, oldFilePath: String, newNameRaw: String): String? {
        val oldFile = File(oldFilePath)
        if (!oldFile.exists()) return null

        val extension = if (oldFile.extension.isNotBlank()) ".${oldFile.extension}" else ".pdf"
        val cleanName = PdfEngine.sanitizeOutputFileName(newNameRaw, oldFile.nameWithoutExtension.ifBlank { "PDFCraft_Document" }, extension)
        if (cleanName == oldFile.name) return oldFile.absolutePath

        val newFile = File(oldFile.parentFile, cleanName)
        if (newFile.exists()) return null

        val success = oldFile.renameTo(newFile)
        if (success) {
            dao.updateProjectFile(id, newFile.absolutePath, newFile.name)
            recentDao.updateRecentFileByPath(oldFilePath, newFile.absolutePath, newFile.name)
            return newFile.absolutePath
        }
        return null
    }

    suspend fun clearHistory() =
        dao.clearAll()

    // Engine Operations Wrappers
    suspend fun renderThumbnail(context: Context, file: File, pageIndex: Int, targetWidthDp: Int = 220) =
        PdfEngine.renderPageThumbnail(context, file, pageIndex, targetWidthDp)

    suspend fun mergePages(
        context: Context,
        pageItems: List<PdfPageSourceItem>,
        outputName: String,
        originalSizeBytes: Long
    ): File {
        val outputFile = PdfEngine.mergeAndReorderPages(context, pageItems, outputName)
        saveProjectRecord(
            PdfProjectEntity(
                title = outputFile.name,
                operationType = PdfOperationType.MERGE,
                pageCount = pageItems.size,
                originalSizeBytes = originalSizeBytes,
                resultSizeBytes = outputFile.length(),
                outputFilePath = outputFile.absolutePath,
                summaryDetails = "Merged ${pageItems.size} pages into ${outputFile.name}"
            )
        )
        return outputFile
    }

    suspend fun splitPdf(
        context: Context,
        sourceUri: Uri,
        selectedIndices: List<Int>,
        outputName: String,
        originalSizeBytes: Long,
        pageRotations: Map<Int, Int> = emptyMap()
    ): File {
        val outputFile = PdfEngine.splitPdf(context, sourceUri, selectedIndices, outputName, pageRotations)
        saveProjectRecord(
            PdfProjectEntity(
                title = outputFile.name,
                operationType = PdfOperationType.SPLIT,
                pageCount = selectedIndices.size,
                originalSizeBytes = originalSizeBytes,
                resultSizeBytes = outputFile.length(),
                outputFilePath = outputFile.absolutePath,
                summaryDetails = "Extracted ${selectedIndices.size} pages into ${outputFile.name}"
            )
        )
        return outputFile
    }

    suspend fun splitPdfToSeparate(
        context: Context,
        sourceUri: Uri,
        selectedIndices: List<Int>,
        baseOutputName: String,
        originalSizeBytes: Long,
        pageRotations: Map<Int, Int> = emptyMap()
    ): List<File> {
        val outputFiles = PdfEngine.splitPdfToIndividualPages(context, sourceUri, selectedIndices, baseOutputName, pageRotations)
        outputFiles.forEach { outputFile ->
            saveProjectRecord(
                PdfProjectEntity(
                    title = outputFile.name,
                    operationType = PdfOperationType.SPLIT,
                    pageCount = 1,
                    originalSizeBytes = originalSizeBytes,
                    resultSizeBytes = outputFile.length(),
                    outputFilePath = outputFile.absolutePath,
                    summaryDetails = "Extracted page from ${baseOutputName}"
                )
            )
        }
        return outputFiles
    }

    suspend fun compressPdf(
        context: Context,
        sourceUri: Uri,
        preset: CompressionPreset,
        outputName: String,
        originalSizeBytes: Long
    ): File {
        val outputFile = PdfEngine.compressPdf(context, sourceUri, preset, outputName)
        val actualPages = PdfEngine.getPageCount(outputFile).coerceAtLeast(1)
        val savedBytes = (originalSizeBytes - outputFile.length()).coerceAtLeast(0L)
        val percentSaved = if (originalSizeBytes > 0) ((savedBytes.toDouble() / originalSizeBytes) * 100).toInt() else 0
        val detailNote = if (percentSaved > 0) "$percentSaved% size reduction" else "already optimized"
        saveProjectRecord(
            PdfProjectEntity(
                title = outputFile.name,
                operationType = PdfOperationType.COMPRESS,
                pageCount = actualPages,
                originalSizeBytes = originalSizeBytes,
                resultSizeBytes = outputFile.length(),
                outputFilePath = outputFile.absolutePath,
                summaryDetails = "Compressed with ${preset.title} ($detailNote)"
            )
        )
        return outputFile
    }

    suspend fun extractImages(
        context: Context,
        sourceUri: Uri,
        selectedIndices: List<Int>,
        format: String,
        originalSizeBytes: Long
    ): List<File> {
        val images = PdfEngine.extractImages(context, sourceUri, selectedIndices, format)
        val totalImageSize = images.sumOf { it.length() }
        if (images.isNotEmpty()) {
            saveProjectRecord(
                PdfProjectEntity(
                    title = "Extracted_${images.size}_Images",
                    operationType = PdfOperationType.EXTRACT,
                    pageCount = images.size,
                    originalSizeBytes = originalSizeBytes,
                    resultSizeBytes = totalImageSize,
                    outputFilePath = images.first().absolutePath,
                    summaryDetails = "Saved ${images.size} high-res $format images"
                )
            )
        }
        return images
    }

    suspend fun mergePdfFilesWithPdfBox(
        context: Context,
        pdfUris: List<Uri>,
        outputName: String,
        originalSizeBytes: Long
    ): File {
        val outputFile = com.example.data.pdf.PdfBoxMergeService.mergePdfFiles(context, pdfUris, outputName)
        val actualPages = PdfEngine.getPageCount(outputFile).coerceAtLeast(pdfUris.size)
        saveProjectRecord(
            PdfProjectEntity(
                title = outputFile.name,
                operationType = PdfOperationType.MERGE,
                pageCount = actualPages,
                originalSizeBytes = originalSizeBytes,
                resultSizeBytes = outputFile.length(),
                outputFilePath = outputFile.absolutePath,
                summaryDetails = "Merged ${pdfUris.size} files ($actualPages pages) into ${outputFile.name}"
            )
        )
        return outputFile
    }

    suspend fun encryptPdf(
        context: Context,
        sourceUri: Uri,
        userPassword: String,
        ownerPassword: String,
        outputName: String,
        originalSizeBytes: Long
    ): File {
        val outputFile = PdfEngine.encryptPdf(context, sourceUri, userPassword, ownerPassword, outputName)
        val sourceFile = try { PdfEngine.getLocalFileFromUri(context, sourceUri) } catch (_: Exception) { null }
        val actualPages = sourceFile?.let { PdfEngine.getPageCount(it) }?.coerceAtLeast(1) ?: 1
        saveProjectRecord(
            PdfProjectEntity(
                title = outputFile.name,
                operationType = PdfOperationType.ENCRYPT,
                pageCount = actualPages,
                originalSizeBytes = originalSizeBytes,
                resultSizeBytes = outputFile.length(),
                outputFilePath = outputFile.absolutePath,
                summaryDetails = "Encrypted with 128-bit AES password protection into ${outputFile.name}"
            )
        )
        return outputFile
    }

    suspend fun watermarkPdf(
        context: Context,
        sourceUri: Uri,
        watermarkText: String,
        outputName: String,
        originalSizeBytes: Long
    ): File = watermarkPdf(
        context = context,
        sourceUri = sourceUri,
        options = com.example.data.pdf.WatermarkOptions(text = watermarkText),
        outputName = outputName,
        originalSizeBytes = originalSizeBytes
    )

    suspend fun watermarkPdf(
        context: Context,
        sourceUri: Uri,
        options: com.example.data.pdf.WatermarkOptions,
        outputName: String,
        originalSizeBytes: Long
    ): File {
        val outputFile = PdfEngine.watermarkPdf(context, sourceUri, options, outputName)
        val actualPages = PdfEngine.getPageCount(outputFile).coerceAtLeast(1)
        val details = if (options.type == com.example.data.pdf.WatermarkType.TEXT) {
            "Text Watermark '${options.text}' applied"
        } else {
            "Image Watermark applied"
        }
        saveProjectRecord(
            PdfProjectEntity(
                title = outputFile.name,
                operationType = PdfOperationType.WATERMARK,
                pageCount = actualPages,
                originalSizeBytes = originalSizeBytes,
                resultSizeBytes = outputFile.length(),
                outputFilePath = outputFile.absolutePath,
                summaryDetails = "$details into ${outputFile.name}"
            )
        )
        return outputFile
    }

    suspend fun signPdf(
        context: Context,
        sourceUri: Uri,
        signerName: String,
        reason: String,
        outputName: String,
        originalSizeBytes: Long
    ): File {
        val outputFile = PdfEngine.signPdf(context, sourceUri, signerName, reason, outputName)
        val actualPages = PdfEngine.getPageCount(outputFile).coerceAtLeast(1)
        saveProjectRecord(
            PdfProjectEntity(
                title = outputFile.name,
                operationType = PdfOperationType.SIGN,
                pageCount = actualPages,
                originalSizeBytes = originalSizeBytes,
                resultSizeBytes = outputFile.length(),
                outputFilePath = outputFile.absolutePath,
                summaryDetails = "Digitally signed by $signerName into ${outputFile.name}"
            )
        )
        return outputFile
    }

    suspend fun updatePdfMetadata(
        context: Context,
        sourceUri: Uri,
        metadata: com.example.data.pdf.PdfMetadata,
        outputName: String,
        originalSizeBytes: Long
    ): File {
        val outputFile = PdfEngine.updatePdfMetadata(context, sourceUri, metadata, outputName)
        val details = "Metadata updated (Title: '${metadata.title}', Author: '${metadata.author}')"
        saveProjectRecord(
            PdfProjectEntity(
                title = outputFile.name,
                operationType = PdfOperationType.METADATA,
                pageCount = metadata.pageCount,
                originalSizeBytes = originalSizeBytes,
                resultSizeBytes = outputFile.length(),
                outputFilePath = outputFile.absolutePath,
                summaryDetails = "$details into ${outputFile.name}"
            )
        )
        return outputFile
    }

    suspend fun rotatePdfPages(
        context: Context,
        sourceUri: Uri,
        pageRotations: Map<Int, Int>,
        outputName: String,
        originalSizeBytes: Long,
        pageCount: Int,
        deletedPages: Set<Int> = emptySet()
    ): File {
        val outputFile = PdfEngine.rotatePdfPages(context, sourceUri, pageRotations, outputName, deletedPages)
        val rotatedCount = pageRotations.filterValues { it % 360 != 0 }.size
        val remainingPages = (pageCount - deletedPages.size).coerceAtLeast(1)
        val details = buildString {
            if (rotatedCount > 0) append("Rotated $rotatedCount pages")
            if (deletedPages.isNotEmpty()) {
                if (isNotEmpty()) append(" • ")
                append("Removed ${deletedPages.size} pages")
            }
            append(" in ${outputFile.name}")
        }
        saveProjectRecord(
            PdfProjectEntity(
                title = outputFile.name,
                operationType = PdfOperationType.ROTATE,
                pageCount = remainingPages,
                originalSizeBytes = originalSizeBytes,
                resultSizeBytes = outputFile.length(),
                outputFilePath = outputFile.absolutePath,
                summaryDetails = details
            )
        )
        return outputFile
    }

    suspend fun convertImagesToPdf(
        context: Context,
        imageUris: List<Uri>,
        outputName: String
    ): File {
        val totalSourceBytes = imageUris.sumOf { uri ->
            try {
                if (uri.scheme == "file" && uri.path != null) {
                    File(uri.path!!).length()
                } else {
                    context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length.coerceAtLeast(0L) } ?: 0L
                }
            } catch (_: Exception) {
                0L
            }
        }
        val outputFile = PdfEngine.convertImagesToPdf(context, imageUris, outputName)
        val actualPages = PdfEngine.getPageCount(outputFile).coerceAtLeast(1)
        saveProjectRecord(
            PdfProjectEntity(
                title = outputFile.name,
                operationType = PdfOperationType.IMAGE_TO_PDF,
                pageCount = actualPages,
                originalSizeBytes = if (totalSourceBytes > 0L) totalSourceBytes else outputFile.length(),
                resultSizeBytes = outputFile.length(),
                outputFilePath = outputFile.absolutePath,
                summaryDetails = "Converted $actualPages photos into PDF document ${outputFile.name}"
            )
        )
        return outputFile
    }

    suspend fun addPageNumbersToPdf(
        context: Context,
        sourceUri: Uri,
        options: com.example.data.pdf.PageNumberOptions,
        outputName: String,
        originalSizeBytes: Long
    ): File {
        val outputFile = PdfEngine.addPageNumbersToPdf(context, sourceUri, options, outputName)
        val actualPages = PdfEngine.getPageCount(outputFile).coerceAtLeast(1)
        saveProjectRecord(
            PdfProjectEntity(
                title = outputFile.name,
                operationType = PdfOperationType.PAGE_NUMBER,
                pageCount = actualPages,
                originalSizeBytes = originalSizeBytes,
                resultSizeBytes = outputFile.length(),
                outputFilePath = outputFile.absolutePath,
                summaryDetails = "Applied custom ${options.position.name} page numbers into ${outputFile.name}"
            )
        )
        return outputFile
    }

    suspend fun convertTextToPdf(
        context: Context,
        rawText: String,
        options: com.example.data.pdf.TextToPdfOptions,
        outputName: String
    ): File {
        val outputFile = com.example.data.pdf.PdfTextAndMediaService.convertTextToPdf(context, rawText, options, outputName)
        val actualPages = try {
            PdfEngine.readPdfMetadata(context, Uri.fromFile(outputFile)).pageCount.coerceAtLeast(1)
        } catch (e: Exception) {
            1
        }
        saveProjectRecord(
            PdfProjectEntity(
                title = outputFile.name,
                operationType = PdfOperationType.TEXT_TO_PDF,
                pageCount = actualPages,
                originalSizeBytes = rawText.toByteArray().size.toLong(),
                resultSizeBytes = outputFile.length(),
                outputFilePath = outputFile.absolutePath,
                summaryDetails = "Converted ${rawText.lines().size} lines into PDF document ${outputFile.name}"
            )
        )
        return outputFile
    }

    suspend fun extractTextFromPdf(
        context: Context,
        pdfFile: File
    ): String {
        val text = com.example.data.pdf.PdfTextAndMediaService.extractTextFromPdf(pdfFile)
        val outputDir = File(context.filesDir, "exports").apply { mkdirs() }
        val txtFile = File(outputDir, "${pdfFile.nameWithoutExtension}_text.txt")
        try {
            txtFile.writeText(text)
            saveProjectRecord(
                PdfProjectEntity(
                    title = txtFile.name,
                    operationType = PdfOperationType.PDF_TO_TEXT,
                    pageCount = 1,
                    originalSizeBytes = pdfFile.length(),
                    resultSizeBytes = txtFile.length(),
                    outputFilePath = txtFile.absolutePath,
                    summaryDetails = "Extracted ${text.length} characters from ${pdfFile.name}"
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return text
    }

    suspend fun renderPdfToImages(
        context: Context,
        pdfFile: File,
        isJpeg: Boolean = false,
        dpiMultiplier: Float = 2.0f
    ): List<File> {
        val outputFiles = com.example.data.pdf.PdfTextAndMediaService.renderPdfToImages(context, pdfFile, isJpeg, dpiMultiplier)
        if (outputFiles.isNotEmpty()) {
            val first = outputFiles.first()
            saveProjectRecord(
                PdfProjectEntity(
                    title = "${pdfFile.nameWithoutExtension}_images",
                    operationType = PdfOperationType.PDF_TO_IMAGES,
                    pageCount = outputFiles.size,
                    originalSizeBytes = pdfFile.length(),
                    resultSizeBytes = outputFiles.sumOf { it.length() },
                    outputFilePath = first.absolutePath,
                    summaryDetails = "Rendered ${outputFiles.size} pages into high-res images"
                )
            )
        }
        return outputFiles
    }

    suspend fun convertHtmlToPdf(
        context: Context,
        htmlContent: String,
        title: String,
        outputName: String
    ): File {
        val outputFile = com.example.data.pdf.PdfTextAndMediaService.convertHtmlToPdf(context, htmlContent, title, outputName)
        val actualPages = try {
            PdfEngine.readPdfMetadata(context, Uri.fromFile(outputFile)).pageCount.coerceAtLeast(1)
        } catch (e: Exception) {
            1
        }
        saveProjectRecord(
            PdfProjectEntity(
                title = outputFile.name,
                operationType = PdfOperationType.HTML_TO_PDF,
                pageCount = actualPages,
                originalSizeBytes = htmlContent.toByteArray().size.toLong(),
                resultSizeBytes = outputFile.length(),
                outputFilePath = outputFile.absolutePath,
                summaryDetails = "Converted HTML markup into PDF document ${outputFile.name}"
            )
        )
        return outputFile
    }
}
