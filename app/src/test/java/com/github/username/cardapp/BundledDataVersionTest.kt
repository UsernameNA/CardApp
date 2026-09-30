package com.github.username.cardapp

import com.github.username.cardapp.data.BundledDataVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BundledDataVersionTest {

    private val base = BundledDataVersion.of("[cards]", "{prices}", setOf("a", "b"))

    @Test
    fun sameInputsGiveTheSameVersion() {
        assertEquals(base, BundledDataVersion.of("[cards]", "{prices}", linkedSetOf("b", "a")))
    }

    @Test
    fun changingAnyInputChangesTheVersion() {
        assertNotEquals(base, BundledDataVersion.of("[cards2]", "{prices}", setOf("a", "b")))
        assertNotEquals(base, BundledDataVersion.of("[cards]", "{prices2}", setOf("a", "b")))
        assertNotEquals(base, BundledDataVersion.of("[cards]", "{prices}", setOf("a", "b", "c")))
    }

    @Test
    fun inputBoundariesAreNotAmbiguous() {
        assertNotEquals(
            BundledDataVersion.of("ab", "c", emptySet()),
            BundledDataVersion.of("a", "bc", emptySet()),
        )
    }
}
