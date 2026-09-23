package com.mangareader.app

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStateTest {

    @Test
    fun sourceFailureMessage_reportsLinkageErrorsAsApiMismatch() {
        val message = sourceFailureMessage(
            NoSuchMethodError("missingMethod"),
            fallback = "Could not load source"
        )

        assertTrue(message.contains("newer source API"))
        assertTrue(message.contains("NoSuchMethodError"))
        assertTrue(message.contains("missingMethod"))
    }

    @Test
    fun sourceFailureMessage_preservesRegularExceptionMessageAndType() {
        val message = sourceFailureMessage(
            IllegalStateException("Bad response"),
            fallback = "Could not load source"
        )

        assertEquals("Bad response (IllegalStateException)", message)
    }

    @Test
    fun sourceFailureMessage_usesFallbackWhenExceptionHasNoMessage() {
        val message = sourceFailureMessage(
            RuntimeException(),
            fallback = "Could not load source"
        )

        assertEquals("Could not load source — RuntimeException", message)
    }

    @Test
    fun sourceFailureMessage_rethrowsCancellation() {
        assertThrows(CancellationException::class.java) {
            sourceFailureMessage(CancellationException("cancelled"), "fallback")
        }
    }

    @Test
    fun sourceFailureMessage_rethrowsVirtualMachineErrors() {
        assertThrows(OutOfMemoryError::class.java) {
            sourceFailureMessage(OutOfMemoryError("oom"), "fallback")
        }
    }
}
