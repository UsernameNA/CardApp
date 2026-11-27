package com.github.username.cardapp

import com.github.username.cardapp.ui.common.countOf
import org.junit.Assert.assertEquals
import org.junit.Test

class PluralTest {

    @Test
    fun singularOnlyForExactlyOne() {
        assertEquals("1 card", countOf(1, "card"))
        assertEquals("0 cards", countOf(0, "card"))
        assertEquals("2 cards", countOf(2, "card"))
    }

    @Test
    fun irregularPlural() {
        assertEquals("1 entry", countOf(1, "entry", "entries"))
        assertEquals("3 entries", countOf(3, "entry", "entries"))
    }
}
