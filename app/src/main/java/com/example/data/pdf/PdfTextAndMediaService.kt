package com.example.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class TextFontFamily {
    SANS_SERIF,
    SERIF,
    MONOSPACE
}

enum class PageSizeFormat(val widthPt: Int, val heightPt: Int, val displayName: String) {
    A4(595, 842, "A4 (210 × 297 mm)"),
    LETTER(612, 792, "US Letter (8.5 × 11 in)"),
    LEGAL(612, 1008, "US Legal (8.5 × 14 in)")
}

data class TextToPdfOptions(
    val title: String = "",
    val fontSize: Float = 12f,
    val lineSpacingMultiplier: Float = 1.25f,
    val marginPt: Float = 40f,
    val fontFamily: TextFontFamily = TextFontFamily.SANS_SERIF,
    val pageSize: PageSizeFormat = PageSizeFormat.A4,
    val includePageNumbers: Boolean = true,
    val includeHeaderDate: Boolean = true
)

object PdfTextAndMediaService {
    private const val TAG = "PdfTextAndMediaService"

    /**
     * Reads text content from a given Uri (e.g. .txt, .md, .csv, .json, .log file).
     */
    suspend fun readTextFromUri(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val stringBuilder = StringBuilder()
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var line: String? = reader.readLine()
                while (line != null) {
                    stringBuilder.append(line).append("\n")
                    line = reader.readLine()
                }
            }
        }
        stringBuilder.toString()
    }

    /**
     * Converts raw or markdown text into a paginated PDF document.
     */
    suspend fun convertTextToPdf(
        context: Context,
        rawText: String,
        options: TextToPdfOptions,
        outputFileName: String
    ): File = withContext(Dispatchers.IO) {
        val outputDir = File(context.filesDir, "exports").apply { mkdirs() }
        val cleanName = if (outputFileName.endsWith(".pdf", ignoreCase = true)) {
            outputFileName
        } else {
            "$outputFileName.pdf"
        }
        val outputFile = File(outputDir, cleanName)

        val pdfDocument = PdfDocument()
        val pageWidth = options.pageSize.widthPt
        val pageHeight = options.pageSize.heightPt
        val margin = options.marginPt
        val contentWidth = (pageWidth - (margin * 2)).toInt().coerceAtLeast(100)

        val typeface = when (options.fontFamily) {
            TextFontFamily.SANS_SERIF -> Typeface.SANS_SERIF
            TextFontFamily.SERIF -> Typeface.SERIF
            TextFontFamily.MONOSPACE -> Typeface.MONOSPACE
        }

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            this.textSize = options.fontSize
            this.color = Color.BLACK
        }

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            this.textSize = 10f
            this.color = Color.DKGRAY
        }

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = Typeface.SANS_SERIF
            this.textSize = 9f
            this.color = Color.GRAY
            this.textAlign = Paint.Align.CENTER
        }

        val lineDividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.LTGRAY
            this.strokeWidth = 0.75f
        }

        val headerHeight = if (options.title.isNotBlank() || options.includeHeaderDate) 32f else 10f
        val footerHeight = if (options.includePageNumbers) 28f else 10f
        val availableContentHeight = (pageHeight - (margin * 2) - headerHeight - footerHeight).toInt().coerceAtLeast(100)

        // Split text into paragraphs and prepare StaticLayout
        val paragraphs = rawText.split("\n")
        val linesList = mutableListOf<StaticLayout>()
        for (para in paragraphs) {
            val textToLayout = if (para.isEmpty()) " " else para
            val layout = StaticLayout.Builder.obtain(
                textToLayout,
                0,
                textToLayout.length,
                textPaint,
                contentWidth
            )
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, options.lineSpacingMultiplier)
                .setIncludePad(false)
                .build()
            linesList.add(layout)
        }

        // Paginate layouts
        val pagesLayouts = mutableListOf<MutableList<StaticLayout>>()
        var currentPage = mutableListOf<StaticLayout>()
        var currentHeight = 0

        for (layout in linesList) {
            val layoutH = layout.height
            if (currentHeight + layoutH > availableContentHeight && currentPage.isNotEmpty()) {
                pagesLayouts.add(currentPage)
                currentPage = mutableListOf()
                currentHeight = 0
            }
            currentPage.add(layout)
            currentHeight += layoutH
        }
        if (currentPage.isNotEmpty() || pagesLayouts.isEmpty()) {
            pagesLayouts.add(currentPage)
        }

        val totalPages = pagesLayouts.size
        val dateString = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

        for (pageIndex in 0 until totalPages) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            // Background
            canvas.drawColor(Color.WHITE)

            // Header
            var topY = margin
            if (options.title.isNotBlank() || options.includeHeaderDate) {
                if (options.title.isNotBlank()) {
                    canvas.drawText(options.title, margin, topY + 12f, headerPaint)
                }
                if (options.includeHeaderDate) {
                    val datePaint = Paint(headerPaint).apply { textAlign = Paint.Align.RIGHT }
                    canvas.drawText(dateString, pageWidth - margin, topY + 12f, datePaint)
                }
                topY += 18f
                canvas.drawLine(margin, topY, pageWidth - margin, topY, lineDividerPaint)
                topY += 14f
            }

            // Body Content
            canvas.save()
            canvas.translate(margin, topY)
            var runningY = 0f
            for (layout in pagesLayouts[pageIndex]) {
                canvas.save()
                canvas.translate(0f, runningY)
                layout.draw(canvas)
                canvas.restore()
                runningY += layout.height
            }
            canvas.restore()

            // Footer
            if (options.includePageNumbers) {
                val footerY = pageHeight - margin + 10f
                canvas.drawLine(margin, pageHeight - margin - 6f, pageWidth - margin, pageHeight - margin - 6f, lineDividerPaint)
                canvas.drawText("Page ${pageIndex + 1} of $totalPages", pageWidth / 2f, footerY, footerPaint)
            }

            pdfDocument.finishPage(page)
        }

        FileOutputStream(outputFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        outputFile
    }

    /**
     * Extracts all raw text from a given PDF document using PDFBox.
     */
    suspend fun extractTextFromPdf(pdfFile: File): String = withContext(Dispatchers.IO) {
        try {
            PDDocument.load(pdfFile).use { document ->
                val stripper = PDFTextStripper()
                stripper.sortByPosition = true
                val extracted = stripper.getText(document)
                if (extracted.isNullOrBlank()) {
                    "No extractable text found in this PDF (it may contain scanned image pages)."
                } else {
                    extracted
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting text with PDFBox", e)
            "Error extracting text: ${e.localizedMessage ?: "Unknown error"}"
        }
    }

    /**
     * Renders all pages of a PDF document as high-resolution PNG or JPEG images.
     */
    suspend fun renderPdfToImages(
        context: Context,
        pdfFile: File,
        isJpeg: Boolean = false,
        dpiMultiplier: Float = 2.0f
    ): List<File> = withContext(Dispatchers.IO) {
        val outputDir = File(context.filesDir, "exported_images").apply { mkdirs() }
        val outputFiles = mutableListOf<File>()

        ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val pageCount = renderer.pageCount
                val baseName = pdfFile.nameWithoutExtension

                for (pageIndex in 0 until pageCount) {
                    val page = renderer.openPage(pageIndex)
                    val width = (page.width * dpiMultiplier).toInt().coerceAtLeast(100)
                    val height = (page.height * dpiMultiplier).toInt().coerceAtLeast(100)

                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    val ext = if (isJpeg) "jpg" else "png"
                    val format = if (isJpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
                    val quality = if (isJpeg) 90 else 100
                    val imageFile = File(outputDir, "${baseName}_page_${pageIndex + 1}_${System.currentTimeMillis()}.$ext")

                    FileOutputStream(imageFile).use { out ->
                        bitmap.compress(format, quality, out)
                    }
                    bitmap.recycle()

                    // Scan file so it's registered in system media library
                    try {
                        MediaScannerConnection.scanFile(
                            context,
                            arrayOf(imageFile.absolutePath),
                            arrayOf(if (isJpeg) "image/jpeg" else "image/png"),
                            null
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "MediaScanner error", e)
                    }

                    outputFiles.add(imageFile)
                }
            }
        }
        outputFiles
    }

    /**
     * Converts basic HTML formatted text / web markup into a crisp PDF document.
     */
    suspend fun convertHtmlToPdf(
        context: Context,
        htmlContent: String,
        title: String,
        outputFileName: String
    ): File = withContext(Dispatchers.IO) {
        // Strip HTML tags or convert to structured markdown-like text for printing
        val cleanPlainText = android.text.Html.fromHtml(htmlContent, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
        val options = TextToPdfOptions(
            title = title.ifBlank { "HTML Document" },
            fontSize = 11f,
            lineSpacingMultiplier = 1.3f,
            marginPt = 36f,
            fontFamily = TextFontFamily.SANS_SERIF,
            pageSize = PageSizeFormat.A4,
            includePageNumbers = true,
            includeHeaderDate = true
        )
        convertTextToPdf(context, cleanPlainText, options, outputFileName)
    }
}
