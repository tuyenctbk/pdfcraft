package com.example.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class PdfBoxDocumentMetadata(
    val title: String,
    val totalPages: Int,
    val fileSizeFormatted: String,
    val fileSizeBytes: Long,
    val isEncrypted: Boolean,
    val version: Float
)

object PdfBoxPreviewService {

    /**
     * Renders a specific page using PDFBox PDFRenderer into a high-quality Bitmap.
     */
    suspend fun renderPageBitmapWithPdfBox(
        context: Context,
        pdfUri: Uri,
        pageIndex: Int,
        scale: Float = 1.5f
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val file = PdfEngine.convertAnyFileToPdf(context, pdfUri)
            PDDocument.load(file).use { pdDocument ->
                if (pageIndex in 0 until pdDocument.numberOfPages) {
                    val pdfRenderer = PDFRenderer(pdDocument)
                    return@withContext pdfRenderer.renderImage(pageIndex, scale)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    /**
     * Extract metadata of a PDF document using Apache PDFBox.
     */
    suspend fun getDocumentMetadataWithPdfBox(
        context: Context,
        pdfUri: Uri
    ): PdfBoxDocumentMetadata? = withContext(Dispatchers.IO) {
        try {
            val file = PdfEngine.convertAnyFileToPdf(context, pdfUri)
            PDDocument.load(file).use { pdDocument ->
                val info = pdDocument.documentInformation
                return@withContext PdfBoxDocumentMetadata(
                    title = info?.title?.takeIf { it.isNotBlank() } ?: file.name,
                    totalPages = pdDocument.numberOfPages,
                    fileSizeFormatted = PdfEngine.formatFileSize(file.length()),
                    fileSizeBytes = file.length(),
                    isEncrypted = pdDocument.isEncrypted,
                    version = pdDocument.version
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    /**
     * Scans a selected PDF file for embedded images using PDFBox resources.
     */
    suspend fun scanEmbeddedImagesWithPdfBox(
        context: Context,
        pdfUri: Uri
    ): List<File> = withContext(Dispatchers.IO) {
        val extractedFiles = mutableListOf<File>()
        try {
            val file = PdfEngine.convertAnyFileToPdf(context, pdfUri)
            val outputDir = File(context.cacheDir, "scanned_embedded_images").apply { mkdirs() }

            PDDocument.load(file).use { pdDocument ->
                for (pageIndex in 0 until pdDocument.numberOfPages) {
                    val page = pdDocument.getPage(pageIndex)
                    val resources = page.resources ?: continue

                    for (xObjectName in resources.xObjectNames) {
                        try {
                            val xObject = resources.getXObject(xObjectName)
                            if (xObject is com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject) {
                                val bitmap = xObject.image
                                if (bitmap != null) {
                                    val imgFile = File(outputDir, "embedded_p${pageIndex + 1}_${xObjectName.name.replace("/", "_")}.png")
                                    java.io.FileOutputStream(imgFile).use { out ->
                                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                                    }
                                    extractedFiles.add(imgFile)
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback: If no embedded image objects were found (e.g. vector/flattened PDF), render page bitmaps
        if (extractedFiles.isEmpty()) {
            return@withContext PdfEngine.extractImages(context, pdfUri, emptyList(), "PNG")
        }

        return@withContext extractedFiles
    }
}
