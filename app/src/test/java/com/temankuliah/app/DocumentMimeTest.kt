package com.temankuliah.app

import com.temankuliah.app.domain.documentMimeType
import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentMimeTest {
    @Test
    fun `maps each stored document extension to its real mime type`() {
        assertEquals("image/bmp", documentMimeType("bmp"))
        assertEquals("image/gif", documentMimeType("gif"))
        assertEquals("text/csv", documentMimeType("csv"))
        assertEquals("text/tab-separated-values", documentMimeType("tsv"))
        assertEquals("text/markdown", documentMimeType("md"))
        assertEquals("text/plain", documentMimeType("txt"))
        assertEquals("application/pdf", documentMimeType("pdf"))
        assertEquals("image/jpeg", documentMimeType("JPG"))
    }

    @Test
    fun `unknown extension falls back to a generic binary type`() {
        assertEquals("application/octet-stream", documentMimeType("xyz"))
    }
}
