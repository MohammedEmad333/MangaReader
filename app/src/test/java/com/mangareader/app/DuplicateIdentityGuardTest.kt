package com.mangareader.app

import org.junit.Assert.assertEquals
import org.junit.Test

class DuplicateIdentityGuardTest {
    @Test
    fun distinctByKeepsFirstStableIdentity() {
        val values = listOf(
            "a" to "first",
            "a" to "duplicate",
            "b" to "second",
        )

        val unique = values.distinctBy { it.first }

        assertEquals(listOf("a" to "first", "b" to "second"), unique)
    }
}
