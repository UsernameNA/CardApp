package com.github.username.cardapp.data

import com.github.username.cardapp.data.local.CollectionEntryEntity

/**
 * Parses collection import text ("<quantity> <card name>" per line, optionally
 * "<quantity>x <card name>") and validates each name against the card catalog, so
 * unknown names surface to the user instead of being stored as orphaned entries.
 *
 * Names match ignoring case, diacritics and curly-vs-straight quotes ([TextFold]).
 * Quantities are capped at [CollectionEntryEntity.MAX_QUANTITY] per card.
 */
object CollectionImportParser {

    // Nine digits always fit in an Int; longer numbers are rejected as malformed.
    private val linePattern = Regex("""^(\d{1,9})(\s*[xX])?\s+(\S.*)$""")
    private val whitespace = Regex("\\s+")
    private const val BYTE_ORDER_MARK = "\uFEFF"

    private fun matchKey(name: String): String = TextFold.fold(name).trim().replace(whitespace, " ")

    fun parse(text: String, validNames: Collection<String>): CollectionImportResult {
        val canonicalByKey = validNames.associateBy(::matchKey)
        val quantities = linkedMapOf<String, Int>()
        val unmatched = mutableListOf<String>()

        for (rawLine in text.removePrefix(BYTE_ORDER_MARK).lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            val match = linePattern.matchEntire(line)
            val qty = match?.groupValues?.get(1)?.toInt()
            val canonical = match?.let { m ->
                val name = m.groupValues[3]
                val marker = m.groupValues[2]
                // "3 X Card" may be a quantity marker or a card whose name starts with "X ".
                canonicalByKey[matchKey(name)]
                    ?: if (marker.isNotEmpty()) canonicalByKey[matchKey(marker + " " + name)] else null
            }
            if (qty == null || qty <= 0 || canonical == null) {
                unmatched += line
                continue
            }
            quantities[canonical] = ((quantities[canonical] ?: 0) + qty)
                .coerceAtMost(CollectionEntryEntity.MAX_QUANTITY)
        }

        val entries = quantities.map { (name, qty) -> CollectionEntryEntity(cardName = name, quantity = qty) }
        return CollectionImportResult(entries = entries, unmatched = unmatched)
    }
}
