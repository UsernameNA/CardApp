package com.github.username.cardapp.ui.scan

import com.github.username.cardapp.data.local.CardEntity

/**
 * Stateless OCR → card matching engine.
 *
 * Given the raw text returned by ML Kit and the catalogue of known cards, it scores
 * every card and returns the best match (plus debug candidates). All logic here is
 * pure — no Android, camera, or ViewModel state — so it is exercised directly by unit
 * tests. [ScanViewModel] owns the camera/OCR plumbing and delegates the actual matching
 * to [score].
 *
 * The OCR text is split into a "name zone" (first 1–2 lines, where the card name
 * appears) and a "body zone" (rules, flavor, artist text). Name matches in the name
 * zone score 3–6× higher than in the body, preventing rules-text keywords (e.g.
 * "Dispel", "Rubble") from outscoring the actual card name.
 */
object OcrCardScorer {

    const val SCORE_THRESHOLD = 50

    /** Outcome of scoring an OCR read against the card catalogue. */
    data class ScoreResult(
        val best: CardEntity?,
        val bestScore: Int,
        val top3: List<ScanCandidate>,
        val costCandidates: Set<Int>,
        /** [best] if it cleared [SCORE_THRESHOLD], otherwise null. */
        val matched: CardEntity?,
    )

    fun score(rawText: String, cards: List<CardEntity>): ScoreResult {
        if (cards.isEmpty()) return ScoreResult(null, 0, emptyList(), emptySet(), null)

        // Split into name zone (top of card) and body zone (rules/flavor/artist)
        val lines = rawText.split('\n').filter { it.isNotBlank() }

        // Skip leading artist-credit lines (OCR sometimes places "Art © ..." first)
        val firstContentIdx = lines.indexOfFirst { !isLikelyArtistCredit(it) }
        val contentLines = if (firstContentIdx > 0) lines.drop(firstContentIdx) else lines

        val nameZoneRaw: String
        val bodyZoneRaw: String
        val nameLineCount: Int
        if (contentLines.size <= 1) {
            nameZoneRaw = contentLines.firstOrNull() ?: rawText
            bodyZoneRaw = ""
            nameLineCount = contentLines.size
        } else {
            // Use first content line as name zone; if it's very short (< 12 chars),
            // include the second line too (handles leading noise/digits).
            if (contentLines[0].length < 12) {
                nameZoneRaw = contentLines[0] + " " + contentLines[1]
                nameLineCount = 2
            } else {
                nameZoneRaw = contentLines[0]
                nameLineCount = 1
            }
            bodyZoneRaw = contentLines.drop(nameLineCount).joinToString(" ")
        }

        val nameZone = normalizeOcr(nameZoneRaw.lowercase())
        val bodyZone = normalizeOcr(bodyZoneRaw.lowercase())
        val fullText = normalizeOcr(rawText.lowercase())

        // Extract cost from name area only — not rules text where damage
        // numbers (e.g. "deals 4 damage") create false cost matches
        val costArea = contentLines.take(nameLineCount + 1).joinToString(" ")
        val costCandidates = Regex("""\b(\d{1,2})\b""").findAll(costArea)
            .mapNotNull { it.value.toIntOrNull() }
            .filter { it in 1..15 }
            .toSet()

        val allTokens = tokenize(fullText)
        val allWords = allTokens.filter { it.length > 3 }.toSet()

        val scored = cards
            .map { it to scoreCard(it, nameZone, bodyZone, costCandidates, allTokens, allWords) }
            .sortedByDescending { it.second }

        val best = scored.firstOrNull()
        val top3 = scored.take(3).map { ScanCandidate(it.first.name, it.second) }
        val matched = if (best != null && best.second >= SCORE_THRESHOLD) best.first else null

        return ScoreResult(
            best = best?.first,
            bestScore = best?.second ?: 0,
            top3 = top3,
            costCandidates = costCandidates,
            matched = matched,
        )
    }

    // Scoring signals and their ranges:
    //
    //   Name in name zone    0–200  (dominant signal — where the card name actually appears)
    //   Name in body only    0–30   (capped — prevents rules keywords outscoring real names)
    //   Type match           0–10
    //   Subtype match        0–8
    //   Cost match           0–35
    //   Rules overlap        0–N    (3 pts per word)
    //
    // Threshold 50: a name-zone match on even a single word (50) clears it;
    // body-only matches need cost + type to reach threshold.
    private fun scoreCard(
        card: CardEntity,
        nameZone: String,
        bodyZone: String,
        costCandidates: Set<Int>,
        allTokens: List<String>,
        allWords: Set<String>,
    ): Int {
        var score = 0
        // Card-side text goes through the same normalization as the OCR text, so a
        // correctly-read "Burn" (normalized to "bum") still matches the card "Burn".
        val cardNameClean = stripPunctuation(normalizeOcr(card.name.lowercase()))
        val nameWords = cardNameClean.split(" ").filter { it.length > 1 }
        val nameZoneClean = stripPunctuation(nameZone)

        // ── A. Name in name zone (HIGH weight) ─────────────────────────────────
        var nameZoneScore = 0
        if (nameZoneClean.contains(cardNameClean)) {
            nameZoneScore = if (nameWords.size >= 2) {
                200 // multi-word full-name substring match is very reliable
            } else {
                // Single-word names: require word-boundary match for full score
                // to prevent "Blaze" matching inside "ablaze"
                if (Regex("\\b${Regex.escape(cardNameClean)}\\b").containsMatchIn(nameZoneClean)) 200
                else 60
            }
        } else {
            val nameZoneTokensClean = tokenize(nameZoneClean)
            for (word in nameWords) {
                val tol2 = if (word.length >= 7) 4 else 2
                val bestDist = nameZoneTokensClean.minOfOrNull { levenshtein(word, it) } ?: Int.MAX_VALUE
                nameZoneScore += when {
                    bestDist == 0 -> 50
                    word.length >= 3 && nameZoneClean.contains(word) -> 40 // merged tokens
                    bestDist <= tol2 -> 30
                    else -> 0
                }
            }
        }
        score += nameZoneScore

        // ── B. Name in body zone (LOW weight, capped at 30) ────────────────────
        // Only checked if name zone gave little signal.
        if (nameZoneScore < 40) {
            val bodyZoneClean = stripPunctuation(bodyZone)
            var bodyNameScore = 0
            if (bodyZoneClean.contains(cardNameClean)) {
                bodyNameScore = 30
            } else {
                for (word in nameWords) {
                    if (word.length <= 3) continue
                    // Require word-boundary match in body to avoid "spired" → "spire"
                    val pattern = "\\b${Regex.escape(word)}\\b"
                    if (Regex(pattern).containsMatchIn(bodyZoneClean)) {
                        bodyNameScore += 10
                    }
                }
            }
            score += bodyNameScore.coerceAtMost(30)
        }

        // ── C. Type match (fuzzy, anywhere in text) ────────────────────────────
        val cardTypeLower = normalizeOcr(card.cardType.lowercase())
        if (cardTypeLower.length > 3) {
            val tol2 = if (cardTypeLower.length >= 7) 4 else 2
            val bestDist = allTokens.minOfOrNull { levenshtein(cardTypeLower, it) } ?: Int.MAX_VALUE
            if (bestDist <= tol2) score += 10
        }

        // ── D. Subtype match (fuzzy, anywhere in text) ─────────────────────────
        if (card.subTypes.isNotEmpty()) {
            for (sub in normalizeOcr(card.subTypes.lowercase()).split(Regex("[,/\\s]+")).filter { it.length > 2 }) {
                val tol2 = if (sub.length >= 7) 4 else 2
                val bestDist = allTokens.minOfOrNull { levenshtein(sub, it) } ?: Int.MAX_VALUE
                if (bestDist <= tol2) { score += 8; break }
            }
        }

        // ── E. Cost match ──────────────────────────────────────────────────────
        if (cardTypeLower != "site" && costCandidates.contains(card.cost)) {
            score += 35
        }

        // ── F. Rules text overlap (exact + fuzzy, 3 pts per word) ──────────────
        if (card.rulesText.isNotEmpty()) {
            val rulesWords = normalizeOcr(card.rulesText.lowercase())
                .split(Regex("[^a-z0-9']+"))
                .filter { it.length > 3 }
                .toSet()
            var rulesScore = 0
            for (rw in rulesWords) {
                if (rw in allWords) {
                    rulesScore += 3
                } else {
                    val tol2 = if (rw.length >= 7) 4 else 2
                    val bestDist = allTokens.minOfOrNull { levenshtein(rw, it) } ?: Int.MAX_VALUE
                    if (bestDist <= tol2) rulesScore += 2
                }
            }
            score += rulesScore
        }

        return score
    }

    // Common OCR confusion pairs — characters frequently misread as each other.
    // Used to reduce substitution cost in weighted Levenshtein.
    private val OCR_CONFUSIONS: Set<Long> = buildSet {
        fun pair(a: Char, b: Char) { add(packChars(a, b)); add(packChars(b, a)) }
        pair('0', 'o'); pair('0', 'O')
        pair('1', 'l'); pair('1', 'i'); pair('l', 'i')
        pair('5', 's'); pair('5', 'S')
        pair('8', 'b'); pair('8', 'B')
        pair('g', 'q'); pair('g', '9')
        pair('c', 'e')
        pair('u', 'v')
        pair('n', 'h')
    }

    private fun packChars(a: Char, b: Char): Long =
        (a.lowercaseChar().code.toLong() shl 16) or b.lowercaseChar().code.toLong()

    /** Detect OCR lines that are artist credits rather than card content.
     *  Common patterns: "Art © Name", "At Name" (garbled Art©), "AtOName" (merged Art©). */
    fun isLikelyArtistCredit(line: String): Boolean {
        if ('©' in line || '®' in line) return true
        val trimmed = line.trim()
        if (trimmed.length > 35) return false
        val lower = trimmed.lowercase()
        // "AtO..." — OCR garble of "Art©" where © merges into next characters
        if (lower.startsWith("ato") && trimmed.length < 20) return true
        val firstWord = lower.split(Regex("\\s+"), limit = 2).firstOrNull() ?: return false
        // Common OCR garbles of "Art ©" as standalone first word
        return firstWord in setOf("art", "at", "aut", "att", "arto")
    }

    /** Tokenize text into lowercase alphanumeric words. */
    fun tokenize(text: String): List<String> =
        text.split(Regex("[^a-z0-9']+")).filter { it.isNotEmpty() }

    /** Strip punctuation that OCR may omit/garble (hyphens, apostrophes, !, etc.)
     *  so "Cave-in" matches "Cave In" and "Castle's Ablaze!" matches "Castles Ablaze". */
    fun stripPunctuation(text: String): String =
        text.replace('-', ' ').replace(Regex("['!.,;:?]"), "")

    /**
     * Normalize OCR text by fixing common misreads:
     *   - "rn" → "m" (very frequent OCR error with serif/decorative fonts)
     *   - "vv" → "w"
     *   - "cl" → "d" (decorative font artifact)
     */
    fun normalizeOcr(text: String): String = text
        .replace("rn", "m")
        .replace("vv", "w")
        .replace("cl", "d")

    // Weighted Levenshtein edit distance. Substitutions between commonly confused
    // OCR characters cost 0.5 instead of 1, so a single OCR misread doesn't burn
    // a full edit-distance unit. Result is doubled and returned as Int to avoid
    // floating-point: effective distance = return value / 2. Callers compare
    // against tolerance * 2.
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length * 2
        if (b.isEmpty()) return a.length * 2
        var prev = IntArray(b.length + 1) { it * 2 }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i * 2
            for (j in 1..b.length) {
                curr[j] = if (a[i - 1] == b[j - 1]) {
                    prev[j - 1]
                } else {
                    val subCost = if (packChars(a[i - 1], b[j - 1]) in OCR_CONFUSIONS) 1 else 2
                    subCost + minOf(prev[j], curr[j - 1], prev[j - 1])
                }
            }
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[b.length]
    }
}
