package com.temankuliah.app

import com.temankuliah.app.domain.rethrowIfCancellation
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class CancellationRethrowTest {
    @Test
    fun `cancellation is rethrown so catch Exception cannot swallow it`() {
        val cancel = CancellationException("screen closed")
        val thrown = assertThrows(CancellationException::class.java) { rethrowIfCancellation(cancel) }
        assertSame(cancel, thrown)
    }

    @Test
    fun `ordinary failures are not rethrown`() {
        rethrowIfCancellation(IOException("offline"))
        rethrowIfCancellation(IllegalStateException("bad state"))
    }
}
