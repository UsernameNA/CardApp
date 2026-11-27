package com.github.username.cardapp.ui.scan

import com.github.username.cardapp.data.local.CardEntity

/**
 * The result of one scan, as shown in the result overlay.
 *
 * [id] is unique per scan, so scanning the same card twice (or missing twice) is still
 * two distinct outcomes; UI effects key on it rather than on the card. A correction keeps
 * the id and replaces [card].
 */
data class ScanOutcome(
    val id: Long,
    /** The card added to the pile; null when nothing matched confidently. */
    val card: CardEntity?,
    /** Best candidates, offered in the correction panel. */
    val top3: List<ScanCandidate>,
)
