package com.example

import com.example.data.pdf.PdfEngine
import org.junit.Assert.*
import org.junit.Test

/**
 * Local unit tests covering PDFCraft utility invariants and bug-fix regressions.
 */
class ExampleUnitTest {
    @Test
    fun sanitizeOutputFileName_stripsPathTraversalAndDoubleExtensions() {
        assertEquals("report.pdf", PdfEngine.sanitizeOutputFileName("../../report.PDF"))
        assertEquals("secret.pdf", PdfEngine.sanitizeOutputFileName("/etc/passwd/secret.pdf"))
        assertEquals("doc_1.pdf", PdfEngine.sanitizeOutputFileName("doc:1"))
        assertEquals("PDFCraft_Output.pdf", PdfEngine.sanitizeOutputFileName("   "))
        assertEquals("PDFCraft_Output.pdf", PdfEngine.sanitizeOutputFileName(".."))
        assertEquals("CustomBase_Page_1.pdf", PdfEngine.sanitizeOutputFileName("CustomBase_Page_1", "Fallback"))
        assertEquals("CleanName", PdfEngine.sanitizeOutputFileName("CleanName", "Fallback", ""))
        assertEquals("doc_report.pdf", PdfEngine.sanitizeOutputFileName("doc...report.PDF"))
    }

    @Test
    fun formatFileSize_formatsBytesKbAndMbCleanly() {
        assertEquals("0 B", PdfEngine.formatFileSize(0L))
        assertEquals("0 B", PdfEngine.formatFileSize(-100L))
        assertEquals("512 B", PdfEngine.formatFileSize(512L))
        assertEquals("1.0 KB", PdfEngine.formatFileSize(1024L))
        assertEquals("1.50 MB", PdfEngine.formatFileSize((1.5 * 1024 * 1024).toLong()))
    }
}
