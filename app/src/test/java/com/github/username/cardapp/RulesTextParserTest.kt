package com.github.username.cardapp

import com.github.username.cardapp.ui.detail.RulesTextParser
import com.github.username.cardapp.ui.detail.RulesTextSegment.Element
import com.github.username.cardapp.ui.detail.RulesTextSegment.Text
import org.junit.Assert.assertEquals
import org.junit.Test

class RulesTextParserTest {

    @Test
    fun plainTextIsOneSegment() {
        assertEquals(listOf(Text("Genesis → Draw a spell.")), RulesTextParser.parse("Genesis → Draw a spell."))
    }

    @Test
    fun emptyTextHasNoSegments() {
        assertEquals(emptyList<Any>(), RulesTextParser.parse(""))
    }

    @Test
    fun elementTokensBecomeNamedSymbols() {
        assertEquals(
            listOf(Text("Costs "), Element("air"), Element("earth"), Text(" less, or "), Element("fire"), Text(" and "), Element("water")),
            RulesTextParser.parse("Costs (A)(E) less, or (F) and (W)"),
        )
    }

    @Test
    fun leadingAndTrailingTokensLeaveNoEmptyText() {
        assertEquals(listOf(Element("fire"), Text(": deal 1"), Element("water")), RulesTextParser.parse("(F): deal 1(W)"))
    }

    @Test
    fun unknownLetterTokensStayText() {
        assertEquals(listOf(Text("Tap (T) or (a)")), RulesTextParser.parse("Tap (T) or (a)"))
    }

    @Test
    fun numbersOneToTwentyBecomeCircledDigits() {
        assertEquals(
            listOf(Text("① Draw. ② Discard. ⑳ Win. (21) Lose. (0) Tie.")),
            RulesTextParser.parse("(1) Draw. (2) Discard. (20) Win. (21) Lose. (0) Tie."),
        )
    }

    @Test
    fun newlinesArePreserved() {
        assertEquals(
            listOf(Text("Spellcaster\nGenesis → "), Element("air"), Text("\nDraw")),
            RulesTextParser.parse("Spellcaster\nGenesis → (A)\nDraw"),
        )
    }

    @Test
    fun errataPrefixBecomesTrailingAsterisk() {
        assertEquals(listOf(Text("Airborne *")), RulesTextParser.parse("UPDATED: Airborne"))
    }

    @Test
    fun updatedElsewhereIsLeftAlone() {
        assertEquals(listOf(Text("Not UPDATED: here")), RulesTextParser.parse("Not UPDATED: here"))
    }
}
