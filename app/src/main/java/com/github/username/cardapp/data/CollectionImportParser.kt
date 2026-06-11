package com.github.username.cardapp.data

import com.github.username.cardapp.data.local.CollectionEntryEntity

/**
 * Parses collection import text ("<quantity> <card name>" per line) and validates
 * each name against the card catalog, so unknown names surface to the user instead
 * of violating the collection table's foreign key.
 */
object CollectionImportParser {

    fun parse(text: String, validNames: Collection<String>): CollectionImportResult {
        val canonicalByLower = validNames.associateBy { it.lowercase() }
        val quantities = linkedMapOf<String, Int>()
        val unmatched = mutableListOf<String>()

        for (rawLine in text.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            val spaceIndex = line.indexOf(' ')
            val qty = if (spaceIndex < 1) null else line.substring(0, spaceIndex).toIntOrNull()
            val name = if (spaceIndex < 1) "" else line.substring(spaceIndex + 1).trim()
            val canonical = canonicalByLower[name.lowercase()]
            if (qty == null || qty <= 0 || canonical == null) {
                unmatched += line
                continue
            }
            quantities[canonical] = (quantities[canonical] ?: 0) + qty
        }

        val entries = quantities.map { (name, qty) -> CollectionEntryEntity(cardName = name, quantity = qty) }
        return CollectionImportResult(entries = entries, unmatched = unmatched)
    }
}
