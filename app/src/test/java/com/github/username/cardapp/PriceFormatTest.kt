package com.github.username.cardapp

import com.github.username.cardapp.ui.common.PriceFormat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class PriceFormatTest {

    private lateinit var originalLocale: Locale

    @Before
    fun useCommaDecimalLocale() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun fullUsesDotDecimalRegardlessOfLocale() {
        assertEquals("$1.50", PriceFormat.full(1.5))
        assertEquals("$0.05", PriceFormat.full(0.05))
        assertEquals("$1234.57", PriceFormat.full(1234.567))
    }

    @Test
    fun compactShowsWholeDollarsFromOneThousand() {
        assertEquals("$1000", PriceFormat.compact(1000.0))
        assertEquals("$2499", PriceFormat.compact(2499.99))
    }

    @Test
    fun compactShowsCentsPreciselyBetweenOneAndOneThousand() {
        assertEquals("$1.00", PriceFormat.compact(1.0))
        assertEquals("$12.30", PriceFormat.compact(12.3))
        assertEquals("$999.99", PriceFormat.compact(999.99))
    }

    @Test
    fun compactShowsCentsBelowOneDollar() {
        assertEquals("50¢", PriceFormat.compact(0.5))
        assertEquals("5¢", PriceFormat.compact(0.05))
        assertEquals("0¢", PriceFormat.compact(0.0))
    }

    @Test
    fun compactRoundsToTheNearestCent() {
        // 0.29 * 100 is 28.999…; truncating would show 28¢.
        assertEquals("29¢", PriceFormat.compact(0.29))
        assertEquals("$1.00", PriceFormat.compact(0.999))
    }
}
