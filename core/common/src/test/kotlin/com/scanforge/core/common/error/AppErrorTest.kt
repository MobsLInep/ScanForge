package com.scanforge.core.common.error

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class AppErrorTest {

    @Test
    fun `from maps IOException to Io and keeps the cause`() {
        val cause = IOException("boom")
        val error = AppError.from(cause)
        assertTrue(error is AppError.Io)
        assertSame(cause, error.cause)
    }

    @Test
    fun `from maps unknown throwables to Unknown`() {
        val cause = IllegalStateException("nope")
        val error = AppError.from(cause)
        assertTrue(error is AppError.Unknown)
        assertSame(cause, error.cause)
    }

    @Test
    fun `NotFound carries the missing id`() {
        assertEquals("doc-1", (AppError.NotFound("doc-1")).id)
    }
}
