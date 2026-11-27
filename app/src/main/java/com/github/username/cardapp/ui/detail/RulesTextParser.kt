package com.github.username.cardapp.ui.detail

/**
 * Splits card rules text into plain text and element symbols. The catalogue writes
 * elements as "(A)", "(E)", "(F)", "(W)" and numbered abilities as "(1)"…"(20)"; numbers
 * become circled digits (①), and an errata "UPDATED: " prefix becomes a trailing " *".
 */
object RulesTextParser {

    private const val ERRATA_PREFIX = "UPDATED: "
    private val circledNumber = Regex("""\(([1-9]|1\d|20)\)""")
    private val elementToken = Regex("""\(([AEFW])\)""")
    private val elementNames = mapOf("A" to "air", "E" to "earth", "F" to "fire", "W" to "water")

    fun parse(text: String): List<RulesTextSegment> {
        val cleaned = if (text.startsWith(ERRATA_PREFIX)) text.removePrefix(ERRATA_PREFIX) + " *" else text
        val normalized = circledNumber.replace(cleaned) { match ->
            (0x2460 + match.groupValues[1].toInt() - 1).toChar().toString()
        }

        val segments = mutableListOf<RulesTextSegment>()
        var start = 0
        for (match in elementToken.findAll(normalized)) {
            if (match.range.first > start) {
                segments += RulesTextSegment.Text(normalized.substring(start, match.range.first))
            }
            segments += RulesTextSegment.Element(elementNames.getValue(match.groupValues[1]))
            start = match.range.last + 1
        }
        if (start < normalized.length) segments += RulesTextSegment.Text(normalized.substring(start))
        return segments
    }
}
