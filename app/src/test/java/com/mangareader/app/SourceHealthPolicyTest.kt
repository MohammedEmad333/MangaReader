package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceHealthPolicyTest {
    @Test fun preservesInstalledSourcesEvenWhenThereAreNoFailures() {
        val entries = buildSourceHealthEntries(
            installed = listOf("a" to "Alpha", "b" to "Beta"),
            failed = emptyList(),
        )
        assertEquals(2, entries.size)
        assertTrue(entries.all { it.installed && it.failedChapters == 0 })
        assertTrue(entries.all { "not checked" in it.hint.lowercase() })
    }

    @Test fun groupsFailuresAndRetainsMissingSourcesForDiagnosis() {
        val entries = buildSourceHealthEntries(
            installed = listOf("a" to "Alpha", "a" to "Duplicate"),
            failed = listOf("a" to "HTTP 429", "a" to "timeout", "old" to "Uninstalled"),
        )
        val active = entries.first { it.id == "a" }
        val missing = entries.first { it.id == "old" }
        assertEquals(2, entries.size)
        assertEquals("Alpha", active.name)
        assertEquals(2, active.failedChapters)
        assertTrue(active.hint.contains("Rate limited"))
        assertFalse(missing.installed)
        assertEquals(1, missing.failedChapters)
        assertTrue(missing.hint.contains("not installed"))
    }

    @Test fun hintClassificationDoesNotExposeRawUrlsOrCredentials() {
        assertEquals(
            "Access blocked: check source login or Cloudflare access.",
            sourceFailureHint("https://example.test/private?token=secret HTTP 403"),
        )
        assertTrue(sourceFailureHint("HTTP 404").contains("Missing"))
        assertTrue(sourceFailureHint("SocketTimeoutException").contains("Network"))
        assertTrue(sourceFailureHint("HTTP 429").contains("Rate limited"))
        assertTrue(sourceFailureHint("random error").contains("Downloads"))
    }

    @Test fun blankIdsCannotCreateInvalidSourceEntries() {
        assertTrue(
            buildSourceHealthEntries(
                installed = listOf("" to "Blank"),
                failed = listOf("" to "timeout"),
            ).isEmpty(),
        )
    }
}
