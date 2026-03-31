package com.github.username.cardapp

import com.github.username.cardapp.ui.common.splitForRows
import org.junit.Assert.assertEquals
import org.junit.Test

class SplitForRowsTest {

    @Test
    fun singleElementCountOne() {
        // total=1 is handled before splitForRows is called, but if it were:
        // (Air,1) → top=(Air,0), bottom=(Air,1) — topTarget=0
        // In practice total==1 is short-circuited, so this tests the math
        val (top, bottom) = splitForRows(listOf("Air" to 1))
        assertEquals(0, top.sumOf { it.second })
        assertEquals(1, bottom.sumOf { it.second })
    }

    @Test
    fun singleElementCountTwo() {
        // (Fire,2) → top=(Fire,1), bottom=(Fire,1)
        val (top, bottom) = splitForRows(listOf("Fire" to 2))
        assertEquals(listOf("Fire" to 1), top)
        assertEquals(listOf("Fire" to 1), bottom)
    }

    @Test
    fun singleElementCountThree() {
        // (Fire,3) → topTarget=1, top=(Fire,1), bottom=(Fire,2)
        val (top, bottom) = splitForRows(listOf("Fire" to 3))
        assertEquals(listOf("Fire" to 1), top)
        assertEquals(listOf("Fire" to 2), bottom)
    }

    @Test
    fun twoElementsOneEach() {
        // (Air,1), (Fire,1) → topTarget=1
        // Sorted by count: both 1, Air first
        // Air fits in top (0+1 <= 1), Fire to bottom
        val (top, bottom) = splitForRows(listOf("Air" to 1, "Fire" to 1))
        assertEquals(1, top.sumOf { it.second })
        assertEquals(1, bottom.sumOf { it.second })
    }

    @Test
    fun twoElementsTwoEach() {
        // (Air,2), (Fire,2) → topTarget=2
        // Sorted by count: both 2
        // First group fits in top (0+2 <= 2), second to bottom
        val (top, bottom) = splitForRows(listOf("Air" to 2, "Fire" to 2))
        assertEquals(2, top.sumOf { it.second })
        assertEquals(2, bottom.sumOf { it.second })
    }

    @Test
    fun twoElementsOneAndTwo() {
        // (Air,1), (Fire,2) → total=3, topTarget=1
        // Sorted by count asc: Air(1), Fire(2)
        // Air(1) fits in top (0+1 <= 1), Fire(2) to bottom
        val (top, bottom) = splitForRows(listOf("Air" to 1, "Fire" to 2))
        assertEquals(1, top.sumOf { it.second })
        assertEquals(2, bottom.sumOf { it.second })
        // Like elements stay together
        assertEquals(1, top.size)
        assertEquals(1, bottom.size)
    }

    @Test
    fun threeElementsOneEach() {
        // (Air,1), (Fire,1), (Earth,1) → total=3, topTarget=1
        val (top, bottom) = splitForRows(listOf("Air" to 1, "Fire" to 1, "Earth" to 1))
        assertEquals(1, top.sumOf { it.second })
        assertEquals(2, bottom.sumOf { it.second })
    }

    @Test
    fun fourElementsOneEach() {
        // (Air,1), (Fire,1), (Earth,1), (Water,1) → total=4, topTarget=2
        val (top, bottom) = splitForRows(
            listOf("Air" to 1, "Fire" to 1, "Earth" to 1, "Water" to 1),
        )
        assertEquals(2, top.sumOf { it.second })
        assertEquals(2, bottom.sumOf { it.second })
    }

    @Test
    fun edgeCaseAllGroupsTooLargeForTop() {
        // (Fire,3), (Earth,2) → total=5, topTarget=2
        // Sorted: Earth(2), Fire(3)
        // Earth(2) fits in top (0+2 <= 2), Fire(3) to bottom
        val (top, bottom) = splitForRows(listOf("Fire" to 3, "Earth" to 2))
        assertEquals(2, top.sumOf { it.second })
        assertEquals(3, bottom.sumOf { it.second })
    }
}
