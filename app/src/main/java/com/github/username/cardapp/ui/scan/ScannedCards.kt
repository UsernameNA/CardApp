package com.github.username.cardapp.ui.scan

import com.github.username.cardapp.data.local.CardEntity

/**
 * Pure operations on the running list of scanned cards.
 *
 * Each function takes the current list and returns a new one, so the logic is free of
 * coroutine/locking concerns and is unit-tested directly. [ScanViewModel] applies these
 * under its mutex. Note the deliberate distinction: manual +/- edits adjust counts in
 * place ([incrementExisting] / [removeOne]), while a fresh scan bumps the card to the
 * front ([addOrBumpToFront]).
 */
object ScannedCards {

    /** Increment an existing entry's count without reordering. No-op if the card is absent. */
    fun incrementExisting(entries: List<ScannedEntry>, cardName: String): List<ScannedEntry> {
        val idx = entries.indexOfFirst { it.card.name == cardName }
        if (idx < 0) return entries
        return entries.toMutableList().apply {
            this[idx] = this[idx].copy(count = this[idx].count + 1)
        }
    }

    /** Remove one copy: decrement the count, or drop the entry when it would hit zero.
     *  No-op if the card is absent. */
    fun removeOne(entries: List<ScannedEntry>, cardName: String): List<ScannedEntry> {
        val idx = entries.indexOfFirst { it.card.name == cardName }
        if (idx < 0) return entries
        return entries.toMutableList().apply {
            if (this[idx].count <= 1) removeAt(idx)
            else this[idx] = this[idx].copy(count = this[idx].count - 1)
        }
    }

    /** Add a freshly scanned card at the front, or bump an existing entry to the front
     *  with its count incremented. */
    fun addOrBumpToFront(entries: List<ScannedEntry>, card: CardEntity): List<ScannedEntry> {
        val idx = entries.indexOfFirst { it.card.name == card.name }
        return entries.toMutableList().apply {
            val entry = if (idx >= 0) removeAt(idx).let { it.copy(count = it.count + 1) }
            else ScannedEntry(card, 1)
            add(0, entry)
        }
    }
}
