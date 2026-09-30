package com.github.username.cardapp.ui.scan

import com.github.username.cardapp.data.local.CardEntity

/**
 * Stateless OCR → card matching engine.
 *
 * Given the raw text returned by ML Kit and the catalogue of known cards, it scores
 * every card and returns the best match (plus debug candidates). All logic here is
 * pure — no Android, camera, or ViewModel state — so it is exercised directly by unit
 * tests. [ScanViewModel] owns the camera/OCR plumbing and delegates the actual matching
 * to [prepare] once per catalogue, then [scoreFrame] per read and [combine] per scan.
 *
 * The OCR text is split into a "name zone" (first 1–2 lines, where the card name
 * appears) and a "body zone" (rules, flavor, artist text). Name matches in the name
 * zone score 3–6× higher than in the body, preventing rules-text keywords (e.g.
 * "Dispel", "Rubble") from outscoring the actual card name.
 *
 * A scan reads the same card several times ([scoreFrame] per frame, then [combine]),
 * so a misread in one read is outvoted by the others.
 */
object OcrCardScorer {

    const val SCORE_THRESHOLD = 50

    /**
     * Minimum lead over the runner-up, as a percentage of the winning score, required to
     * accept a match automatically.
     *
     * Clearing [SCORE_THRESHOLD] only says the winner looks plausible — it says nothing
     * about whether a *second* card looks equally plausible. Measured across the catalogue
     * with simulated OCR noise, requiring a 5% lead removed every confidently-wrong
     * auto-match, at the cost of ~3% of correct ones. Those instead fall through to the
     * correction panel, which already lists the top-3 candidates.
     */
    const val SCORE_MARGIN_PERCENT = 5

    /** Outcome of scoring an OCR read against the card catalogue. */
    data class ScoreResult(
        val best: CardEntity?,
        val bestScore: Int,
        val top3: List<ScanCandidate>,
        val costCandidates: Set<Int>,
        /** [best] if it cleared both [SCORE_THRESHOLD] and [SCORE_MARGIN_PERCENT], else null. */
        val matched: CardEntity?,
    )

    /** Per-card scores from a single OCR read, ready to be merged by [combine]. */
    data class FrameScores(
        val rawText: String,
        /** Card name → score. Card names are unique (they are the Room primary key). */
        val scores: Map<String, Int>,
        val costCandidates: Set<Int>,
    )

    /**
     * A card's text normalized, tokenized and compiled into patterns once, so scoring a
     * read only does work on the OCR side. Build with [prepare].
     */
    class PreparedCard internal constructor(val card: CardEntity) {
        // Card-side text goes through the same normalization as the OCR text, so a
        // correctly-read "Burn" (normalized to "bum") still matches the card "Burn".
        internal val nameClean: String = stripPunctuation(normalizeOcr(card.name.lowercase()))
        internal val nameWords: List<String> = nameClean.split(" ").filter { it.length > 1 }

        /** Whole-word match for single-word names, so "Blaze" doesn't match inside "ablaze". */
        internal val singleWordName: Regex? =
            if (nameWords.size >= 2) null else Regex("\\b${Regex.escape(nameClean)}\\b")

        /** Whole-word patterns for the name words checked in the body zone. */
        internal val bodyNameWords: List<Regex> =
            nameWords.filter { it.length > 3 }.map { Regex("\\b${Regex.escape(it)}\\b") }

        internal val type: String = normalizeOcr(card.cardType.lowercase())
        internal val isSite: Boolean = type == "site"

        internal val subTypes: List<String> =
            if (card.subTypes.isEmpty()) emptyList()
            else normalizeOcr(card.subTypes.lowercase()).split(SUBTYPE_SEPARATORS).filter { it.length > 2 }

        internal val rulesWords: Set<String> =
            if (card.rulesText.isEmpty()) emptySet()
            else normalizeOcr(card.rulesText.lowercase()).split(NON_WORD).filter { it.length > 3 }.toSet()
    }

    /** Prepare the catalogue for scoring. Do this once, not per read. */
    fun prepare(cards: List<CardEntity>): List<PreparedCard> = cards.map(::PreparedCard)

    /** Score every card against one OCR read. */
    fun scoreFrame(rawText: String, cards: List<PreparedCard>): FrameScores {
        if (cards.isEmpty()) return FrameScores(rawText, emptyMap(), emptySet())

        val ctx = buildContext(rawText)
        val scores = HashMap<String, Int>(cards.size * 2)
        for (card in cards) scores[card.card.name] = scoreCard(card, ctx)
        return FrameScores(rawText, scores, ctx.costCandidates)
    }

    /**
     * Merge the reads of a multi-frame burst into a single verdict.
     *
     * Scores are averaged rather than summed so [SCORE_THRESHOLD] keeps the same meaning
     * regardless of how many frames were captured.
     */
    fun combine(frames: List<FrameScores>, cards: List<CardEntity>): ScoreResult {
        val usable = frames.filter { it.scores.isNotEmpty() }
        if (usable.isEmpty() || cards.isEmpty()) {
            return ScoreResult(null, 0, emptyList(), emptySet(), null)
        }

        val totals = HashMap<String, Int>(cards.size * 2)
        for (frame in usable) {
            for ((name, value) in frame.scores) totals[name] = (totals[name] ?: 0) + value
        }

        val scored = cards
            .map { it to (totals[it.name] ?: 0) / usable.size }
            .sortedByDescending { it.second }

        val best = scored.firstOrNull()
        val runnerUp = scored.getOrNull(1)?.second ?: 0
        val top3 = scored.take(3).map { ScanCandidate(it.first.name, it.second) }

        // Integer form of: (best - runnerUp) >= best * (SCORE_MARGIN_PERCENT / 100)
        val matched = best
            ?.takeIf { it.second >= SCORE_THRESHOLD }
            ?.takeIf { (it.second - runnerUp) * 100 >= it.second * SCORE_MARGIN_PERCENT }
            ?.first

        return ScoreResult(
            best = best?.first,
            bestScore = best?.second ?: 0,
            top3 = top3,
            costCandidates = usable.first().costCandidates,
            matched = matched,
        )
    }

    /** Convenience single-frame entry point; prepares [cards] on every call. */
    fun score(rawText: String, cards: List<CardEntity>): ScoreResult =
        combine(listOf(scoreFrame(rawText, prepare(cards))), cards)

    /**
     * Everything derived from one OCR read that is shared across all ~1100 card
     * comparisons: the split zones, the token lists, and memos of edit distances.
     *
     * The memo matters. Rules-text words repeat heavily across the catalogue, so without
     * it the nearest-token search runs cards × words × tokens times per frame — and a
     * burst multiplies that by the frame count.
     */
    private class FrameContext(
        nameZone: String,
        bodyZone: String,
        fullText: String,
        val costCandidates: Set<Int>,
    ) {
        val nameZoneClean: String = stripPunctuation(nameZone)
        val nameZoneTokens: List<String> = tokenize(nameZoneClean)
        val bodyZoneClean: String = stripPunctuation(bodyZone)

        private val allTokens: List<String> = tokenize(fullText)
        val allWords: Set<String> = allTokens.filter { it.length > 3 }.toSet()

        private val nearestMemo = HashMap<String, Int>()
        private val nearestInNameZoneMemo = HashMap<String, Int>()

        /** Edit distance from [word] to the nearest token anywhere in the read. */
        fun nearest(word: String): Int = nearestMemo.getOrPut(word) {
            allTokens.minOfOrNull { levenshtein(word, it) } ?: Int.MAX_VALUE
        }

        /** Edit distance from [word] to the nearest token in the name zone. */
        fun nearestInNameZone(word: String): Int = nearestInNameZoneMemo.getOrPut(word) {
            nameZoneTokens.minOfOrNull { levenshtein(word, it) } ?: Int.MAX_VALUE
        }
    }

    private fun buildContext(rawText: String): FrameContext {
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

        // Extract cost from name area only — not rules text where damage
        // numbers (e.g. "deals 4 damage") create false cost matches
        val costArea = contentLines.take(nameLineCount + 1).joinToString(" ")
        val costCandidates = COST_NUMBER.findAll(costArea)
            .mapNotNull { it.value.toIntOrNull() }
            .filter { it in 1..15 }
            .toSet()

        return FrameContext(
            nameZone = normalizeOcr(nameZoneRaw.lowercase()),
            bodyZone = normalizeOcr(bodyZoneRaw.lowercase()),
            fullText = normalizeOcr(rawText.lowercase()),
            costCandidates = costCandidates,
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
    private fun scoreCard(card: PreparedCard, ctx: FrameContext): Int {
        var score = 0
        val cardNameClean = card.nameClean
        val nameWords = card.nameWords
        val nameZoneClean = ctx.nameZoneClean

        // ── A. Name in name zone (HIGH weight) ─────────────────────────────────
        var nameZoneScore = 0
        if (nameZoneClean.contains(cardNameClean)) {
            // A multi-word full-name substring match is very reliable; a single-word
            // name needs a whole-word match for the full score.
            val singleWord = card.singleWordName
            nameZoneScore = if (singleWord == null || singleWord.containsMatchIn(nameZoneClean)) 200 else 60
        } else {
            for (word in nameWords) {
                val tol2 = if (word.length >= 7) 4 else 2
                val bestDist = ctx.nearestInNameZone(word)
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
            val bodyZoneClean = ctx.bodyZoneClean
            var bodyNameScore = 0
            if (bodyZoneClean.contains(cardNameClean)) {
                bodyNameScore = 30
            } else {
                // Whole-word match in body to avoid "spired" → "spire"
                for (pattern in card.bodyNameWords) {
                    if (pattern.containsMatchIn(bodyZoneClean)) bodyNameScore += 10
                }
            }
            score += bodyNameScore.coerceAtMost(30)
        }

        // ── C. Type match (fuzzy, anywhere in text) ────────────────────────────
        val cardType = card.type
        if (cardType.length > 3) {
            val tol2 = if (cardType.length >= 7) 4 else 2
            if (ctx.nearest(cardType) <= tol2) score += 10
        }

        // ── D. Subtype match (fuzzy, anywhere in text) ─────────────────────────
        for (sub in card.subTypes) {
            val tol2 = if (sub.length >= 7) 4 else 2
            if (ctx.nearest(sub) <= tol2) { score += 8; break }
        }

        // ── E. Cost match ──────────────────────────────────────────────────────
        if (!card.isSite && ctx.costCandidates.contains(card.card.cost)) {
            score += 35
        }

        // ── F. Rules text overlap (exact + fuzzy, 3 pts per word) ──────────────
        var rulesScore = 0
        for (rw in card.rulesWords) {
            if (rw in ctx.allWords) {
                rulesScore += 3
            } else {
                val tol2 = if (rw.length >= 7) 4 else 2
                if (ctx.nearest(rw) <= tol2) rulesScore += 2
            }
        }
        score += rulesScore

        return score
    }

    private val NON_WORD = Regex("[^a-z0-9']+")
    private val SUBTYPE_SEPARATORS = Regex("[,/\\s]+")
    private val WHITESPACE = Regex("\\s+")
    private val COST_NUMBER = Regex("""\b(\d{1,2})\b""")
    private val STRIPPED_PUNCTUATION = Regex("['‘’ʼ!.,;:?]")
    private val ARTIST_CREDIT_WORDS = setOf("art", "at", "aut", "att", "arto")

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
        val firstWord = lower.split(WHITESPACE, limit = 2).firstOrNull() ?: return false
        // Common OCR garbles of "Art ©" as standalone first word
        return firstWord in ARTIST_CREDIT_WORDS
    }

    /** Tokenize text into lowercase alphanumeric words. */
    fun tokenize(text: String): List<String> =
        text.split(NON_WORD).filter { it.isNotEmpty() }

    /** Strip punctuation that OCR may omit/garble (hyphens, apostrophes, !, etc.)
     *  so "Cave-in" matches "Cave In" and "Castle's Ablaze!" matches "Castles Ablaze".
     *  Both straight and typographic apostrophes are removed — ML Kit usually emits the
     *  curly form, while the catalogue uses the straight one. */
    fun stripPunctuation(text: String): String =
        text.replace('-', ' ').replace(STRIPPED_PUNCTUATION, "")

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
