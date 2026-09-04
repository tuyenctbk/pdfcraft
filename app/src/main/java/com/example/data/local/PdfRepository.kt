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
        val cleanName = if (newNameRaw.endsWith(extension, ignoreCase = true)) {
            newNameRaw
        } else if (newNameRaw.contains(".")) {
            newNameRaw
        } else {
            "$newNameRaw$extension"
        }

        val newFile = File(oldFile.parentFile, cleanName)
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
    suspend fun generateSamples(context: Context): List<File> =
        PdfEngine.generateSamplePdfs(context)

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
        originalSizeBytes: Long
    ): File {
        val outputFile = PdfEngine.splitPdf(context, sourceUri, selectedIndices, outputName)
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
        originalSizeBytes: Long
    ): List<File> {
        val outputFiles = PdfEngine.splitPdfToIndividualPages(context, sourceUri, selectedIndices, baseOutputName)
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
        val savedBytes = originalSizeBytes - outputFile.length()
        val percentSaved = if (originalSizeBytes > 0) ((savedBytes.toDouble() / originalSizeBytes) * 100).toInt() else 0
        saveProjectRecord(
            PdfProjectEntity(
                title = outputFile.name,
                operationType = PdfOperationType.COMPRESS,
                pageCount = 1,
                originalSizeBytes = originalSizeBytes,
                resultSizeBytes = outputFile.length(),
                outputFilePath = outputFile.absolutePath,
                summaryDetails = "Compressed with ${preset.title} ($percentSaved% size reduction)"
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
                    outputFilePath = images.first().parentFile?.absolutePath ?: "",
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
        saveProjectRecord(
            PdfProjectEntity(
                title = outputFile.name,
                operationType = PdfOperationType.MERGE,
                pageCount = pdfUris.size,
                originalSizeBytes = originalSizeBytes,
                resultSizeBytes = outputFile.length(),
                outputFilePath = outputFile.absolutePath,
                summaryDetails = "Merged ${pdfUris.size} files using PDFBox Engine into ${outputFile.name}"
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
        saveProjectRecord(
            PdfProjectEntity(
                title = outputFile.name,
                operationType = PdfOperationType.ENCRYPT,
                pageCount = 1,
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
        val details = if (options.type == com.example.data.pdf.WatermarkType.TEXT) {
            "Text Watermark '${options.text}' applied"
        } else {
            "Image Watermark applied"
        }
        saveProjectRecord(
            PdfProjectEntity(
                title = outputFile.name,
                operationType = PdfOperationType.WATERMARK,
                pageCount = 1,
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
        saveProjectRecord(
            PdfProjectEntity(
                title = outputFile.name,
                operationType = PdfOperationType.SIGN,
                pageCount = 1,
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
        pageCount: Int
    ): File {
        val outputFile = PdfEngine.rotatePdfPages(context, sourceUri, pageRotations, outputName)
        val rotatedCount = pageRotations.filterValues { it % 360 != 0 }.size
        saveProjectRecord(
            PdfProjectEntity(
                title = outputFile.name,
                operationType = PdfOperationType.ROTATE,
                pageCount = pageCount,
                originalSizeBytes = originalSizeBytes,
                resultSizeBytes = outputFile.length(),
                outputFilePath = outputFile.absolutePath,
                summaryDetails = "Rotated $rotatedCount pages in ${outputFile.name}"
            )
        )
        return outputFile
    }

    suspend fun convertImagesToPdf(
        context: Context,
        imageUris: List<Uri>,
        outputName: String
    ): File {
        val outputFile = PdfEngine.convertImagesToPdf(context, imageUris, outputName)
        saveProjectRecord(
            PdfProjectEntity(
                title = outputFile.name,
                operationType = PdfOperationType.IMAGE_TO_PDF,
                pageCount = imageUris.size,
                originalSizeBytes = imageUris.size * 100000L,
                resultSizeBytes = outputFile.length(),
                outputFilePath = outputFile.absolutePath,
                summaryDetails = "Converted ${imageUris.size} photos into PDF document ${outputFile.name}"
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
        saveProjectRecord(
            PdfProjectEntity(
                title = outputFile.name,
                operationType = PdfOperationType.PAGE_NUMBER,
                pageCount = 1,
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
