package com.github.username.cardapp.ui.scan

import android.app.Application
import androidx.camera.core.ImageProxy
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.username.cardapp.data.CardRepository
import com.github.username.cardapp.data.PriceInfo
import com.github.username.cardapp.data.local.AppDatabase
import com.github.username.cardapp.data.local.CardEntity
import com.github.username.cardapp.data.local.CollectionEntryEntity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

enum class ScanMode { Manual, Auto }

sealed class ScanStatus {
    object Idle : ScanStatus()
    object Scanning : ScanStatus()
    object NotFound : ScanStatus()
    data class Found(val cardName: String) : ScanStatus()
    object AutoWatching : ScanStatus()
    object AutoCooldown : ScanStatus()
}

data class ScannedEntry(val card: CardEntity, val count: Int)

data class ScanDebugInfo(
    val rawText: String,
    val costCandidates: Set<Int>,
    val bestCardName: String?,
    val bestScore: Int,
)

class ScanViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = CardRepository(
        context = app,
        db = AppDatabase.getInstance(app),
    )

    private val allCards: StateFlow<List<CardEntity>> = repository.cards
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val prices: StateFlow<Map<String, PriceInfo>> = repository.prices

    init {
        viewModelScope.launch { repository.loadPrices() }
    }

    private val _scannedCards = MutableStateFlow<List<ScannedEntry>>(emptyList())
    val scannedCards: StateFlow<List<ScannedEntry>> = _scannedCards.asStateFlow()
    private val scannedCardsMutex = Mutex()

    private val _scanStatus = MutableStateFlow<ScanStatus>(ScanStatus.Idle)
    val scanStatus: StateFlow<ScanStatus> = _scanStatus.asStateFlow()

    private val _debugInfo = MutableStateFlow<ScanDebugInfo?>(null)
    val debugInfo: StateFlow<ScanDebugInfo?> = _debugInfo.asStateFlow()

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val pendingScan = AtomicBoolean(false)

    private val _scanMode = MutableStateFlow(ScanMode.Manual)
    val scanMode: StateFlow<ScanMode> = _scanMode.asStateFlow()

    // --- Auto mode: CV-based card detection ---
    //
    // Instead of scanning on a timer, we fingerprint each camera frame (16×16 grayscale
    // sampled from the center 50% of the Y plane) and run a three-state machine:
    //
    //   WaitingForCard  — accumulate stable frames; once the scene is still and the center
    //                     region has enough visual content, fire OCR
    //   OcrInFlight     — OCR is running; skip incoming frames
    //   WaitingForChange — a card was just scanned; wait until the fingerprint diverges
    //                      (card removed / swapped) before watching again
    private enum class AutoState { WaitingForCard, OcrInFlight, WaitingForChange }

    private val autoState = AtomicReference(AutoState.WaitingForCard)

    // Accessed from analysis executor and reset from main thread via toggleScanMode
    @Volatile private var prevFingerprint: IntArray? = null
    @Volatile private var stableFrameCount = 0

    // Written from coroutine callbacks, read from the analysis thread
    @Volatile private var scannedFingerprint: IntArray? = null
    @Volatile private var failedFingerprint: IntArray? = null

    fun toggleScanMode() {
        val next = if (_scanMode.value == ScanMode.Manual) ScanMode.Auto else ScanMode.Manual
        _scanMode.value = next
        _scanStatus.value = if (next == ScanMode.Auto) ScanStatus.AutoWatching else ScanStatus.Idle
        if (next == ScanMode.Auto) resetAutoState()
    }

    private fun resetAutoState() {
        autoState.set(AutoState.WaitingForCard)
        prevFingerprint = null
        stableFrameCount = 0
        scannedFingerprint = null
        failedFingerprint = null
    }

    fun requestScan() {
        _scanStatus.value = ScanStatus.Scanning
        pendingScan.set(true)
    }

    fun analyzeFrame(imageProxy: ImageProxy) {
        when (_scanMode.value) {
            ScanMode.Manual -> analyzeFrameManual(imageProxy)
            ScanMode.Auto -> analyzeFrameAuto(imageProxy)
        }
    }

    // ── Manual mode ─────────────────────────────────────────────────────────────

    private fun analyzeFrameManual(imageProxy: ImageProxy) {
        if (!pendingScan.compareAndSet(true, false)) {
            imageProxy.close()
            return
        }
        val bitmap = imageProxy.toBitmap()
        val sensorRotation = imageProxy.imageInfo.rotationDegrees
        imageProxy.close()

        recognizer.process(InputImage.fromBitmap(bitmap, sensorRotation))
            .addOnSuccessListener { visionText ->
                viewModelScope.launch(Dispatchers.Default) {
                    val result = scoreOcrText(visionText.text)
                    if (result != null) {
                        addScannedCard(result)
                        setStatusTemporarily(ScanStatus.Found(result.name))
                    } else {
                        setStatusTemporarily(ScanStatus.NotFound)
                    }
                }
            }
            .addOnFailureListener { setStatusTemporarily(ScanStatus.NotFound) }
    }

    // ── Auto mode ───────────────────────────────────────────────────────────────

    private fun analyzeFrameAuto(imageProxy: ImageProxy) {
        // Compute fingerprint directly from the Y (luminance) plane — no Bitmap allocation.
        val fingerprint = computeFingerprint(imageProxy)
        val state: AutoState = autoState.get()

        when (state) {
            AutoState.WaitingForCard -> {
                // Track inter-frame stability
                val prev = prevFingerprint
                if (prev != null) {
                    if (fingerprintDiff(prev, fingerprint) < STABLE_THRESHOLD) {
                        stableFrameCount++
                    } else {
                        stableFrameCount = 0
                        failedFingerprint = null   // scene changed, allow retry
                    }
                }
                prevFingerprint = fingerprint

                val hasContent = fingerprintVariance(fingerprint) > CONTENT_VARIANCE_THRESHOLD
                val notPreviouslyFailed = failedFingerprint?.let {
                    fingerprintDiff(it, fingerprint) > CHANGE_THRESHOLD
                } ?: true

                if (stableFrameCount >= STABLE_FRAMES_REQUIRED && hasContent && notPreviouslyFailed) {
                    if (autoState.compareAndSet(AutoState.WaitingForCard, AutoState.OcrInFlight)) {
                        stableFrameCount = 0
                        _scanStatus.value = ScanStatus.Scanning

                        // Only create a Bitmap when we actually need OCR
                        val bitmap = imageProxy.toBitmap()
                        val rotation = imageProxy.imageInfo.rotationDegrees
                        imageProxy.close()

                        recognizer.process(InputImage.fromBitmap(bitmap, rotation))
                            .addOnSuccessListener { visionText ->
                                viewModelScope.launch(Dispatchers.Default) {
                                    val result = scoreOcrText(visionText.text)
                                    if (result != null) {
                                        addScannedCard(result)
                                        scannedFingerprint = fingerprint
                                        autoState.set(AutoState.WaitingForChange)
                                        _scanStatus.value = ScanStatus.Found(result.name)
                                        viewModelScope.launch {
                                            delay(FOUND_BANNER_MS)
                                            if (_scanMode.value == ScanMode.Auto &&
                                                autoState.get() == AutoState.WaitingForChange
                                            ) {
                                                _scanStatus.value = ScanStatus.AutoCooldown
                                            }
                                        }
                                    } else {
                                        failedFingerprint = fingerprint
                                        autoState.set(AutoState.WaitingForCard)
                                        _scanStatus.value = ScanStatus.AutoWatching
                                    }
                                }
                            }
                            .addOnFailureListener {
                                failedFingerprint = fingerprint
                                autoState.set(AutoState.WaitingForCard)
                                _scanStatus.value = ScanStatus.AutoWatching
                            }
                        return
                    }
                }
                imageProxy.close()
            }

            AutoState.OcrInFlight -> {
                prevFingerprint = fingerprint
                imageProxy.close()
            }

            AutoState.WaitingForChange -> {
                val scanned = scannedFingerprint
                if (scanned != null && fingerprintDiff(scanned, fingerprint) > CHANGE_THRESHOLD) {
                    autoState.set(AutoState.WaitingForCard)
                    stableFrameCount = 0
                    failedFingerprint = null
                    _scanStatus.value = ScanStatus.AutoWatching
                }
                prevFingerprint = fingerprint
                imageProxy.close()
            }
        }
    }

    // ── OCR scoring ─────────────────────────────────────────────────────────────

    private fun scoreOcrText(rawText: String): CardEntity? {
        val cards = allCards.value
        if (cards.isEmpty()) return null

        val normalized = normalizeOcr(rawText.lowercase())

        val costCandidates = Regex("""\b(\d{1,2})\b""").findAll(rawText)
            .mapNotNull { it.value.toIntOrNull() }
            .filter { it in 1..15 }
            .toSet()

        val ocrTokens = normalized.split(Regex("[^a-z0-9']+")).filter { it.isNotEmpty() }
        val ocrWords = ocrTokens.filter { it.length > 3 }.toSet()

        val (best, bestScore) = cards
            .map { it to scoreCard(it, normalized, costCandidates, ocrTokens, ocrWords) }
            .maxByOrNull { it.second }
            ?: (null to 0)

        _debugInfo.value = ScanDebugInfo(
            rawText = rawText,
            costCandidates = costCandidates,
            bestCardName = best?.name,
            bestScore = bestScore,
        )

        return if (best != null && bestScore >= SCORE_THRESHOLD) best else null
    }

    // Scores a card against OCR text using five signals:
    //
    //   Name match — card names use a decorative font so OCR may garble individual
    //   characters. We score in four tiers:
    //     100 pts  exact full-name substring present in OCR text
    //      25 pts  per name-word with an exact token match in the OCR text
    //      20 pts  per name-word found as substring in the full text (catches merged words)
    //      15 pts  per name-word with a fuzzy token match within edit-distance tolerance
    //              (tolerance = 1 for 3–6 char words, 2 for 7+ char words)
    //
    //   Type match    — 10 pts if the card's type (Minion, Spell, Site, …) appears in
    //                   the OCR text; supporting signal since the type line is scanned too
    //
    //   Subtype match — 8 pts if the card's subtype (Beast, Dragon, …) appears in
    //                   the OCR text (fuzzy)
    //
    //   Cost match    — 35 pts if the card's cost appears as a number (non-site only)
    //
    //   Rules overlap — 4 pts per word shared between OCR and rules text (exact or fuzzy)
    //
    // Threshold 40: full name match always wins; partial fuzzy name + cost also wins;
    // cost alone or rules overlap alone cannot produce a false positive.
    private fun scoreCard(
        card: CardEntity,
        normalizedText: String,
        costCandidates: Set<Int>,
        ocrTokens: List<String>,
        ocrWords: Set<String>,
    ): Int {
        var score = 0

        // --- Name ---
        // Levenshtein returns doubled values (OCR-confused subs cost 1 instead of 2),
        // so tolerance thresholds are also doubled.
        // normalizeOcr is only applied to the OCR side (normalizedText); card names
        // are already correct and normalizing them corrupts names containing "cl".
        val cardNameLower = card.name.lowercase()
        if (normalizedText.contains(cardNameLower)) {
            score += 100
        } else {
            for (nameWord in cardNameLower.split(" ").filter { it.length > 2 }) {
                val tol2 = if (nameWord.length >= 7) 4 else 2  // tolerance * 2
                val bestDist = ocrTokens.minOfOrNull { levenshtein(nameWord, it) } ?: Int.MAX_VALUE
                score += when {
                    bestDist == 0 -> 25
                    // Substring check: catches OCR merging adjacent words (e.g. "darkforest")
                    nameWord.length >= 4 && normalizedText.contains(nameWord) -> 20
                    bestDist <= tol2 -> 15
                    else -> 0
                }
            }
        }

        // --- Type (both name and type use stylized fonts, so apply same fuzzy logic) ---
        val cardTypeLower = card.cardType.lowercase()
        if (cardTypeLower.length > 3) {
            val tol2 = if (cardTypeLower.length >= 7) 4 else 2
            val bestDist = ocrTokens.minOfOrNull { levenshtein(cardTypeLower, it) } ?: Int.MAX_VALUE
            if (bestDist <= tol2) score += 10
        }

        // --- Subtype ---
        if (card.subTypes.isNotEmpty()) {
            for (sub in card.subTypes.lowercase().split(Regex("[,/\\s]+")).filter { it.length > 2 }) {
                val tol2 = if (sub.length >= 7) 4 else 2
                val bestDist = ocrTokens.minOfOrNull { levenshtein(sub, it) } ?: Int.MAX_VALUE
                if (bestDist <= tol2) {
                    score += 8
                    break   // one subtype match is enough
                }
            }
        }

        // --- Cost ---
        val isSite = cardTypeLower == "site"
        if (!isSite && costCandidates.contains(card.cost)) {
            score += 35
        }

        // --- Rules text overlap (exact + fuzzy) ---
        if (card.rulesText.isNotEmpty()) {
            val rulesWords = card.rulesText.lowercase()
                .split(Regex("[^a-z0-9']+"))
                .filter { it.length > 3 }
                .toSet()
            var rulesScore = 0
            for (rw in rulesWords) {
                if (rw in ocrWords) {
                    rulesScore += 4
                } else {
                    val tol2 = if (rw.length >= 7) 4 else 2
                    val bestDist = ocrTokens.minOfOrNull { levenshtein(rw, it) } ?: Int.MAX_VALUE
                    if (bestDist <= tol2) rulesScore += 2
                }
            }
            score += rulesScore
        }

        return score
    }

    // ── Scanned cards list ──────────────────────────────────────────────────────

    fun incrementCard(cardName: String) {
        viewModelScope.launch {
            scannedCardsMutex.withLock {
                val current = _scannedCards.value.toMutableList()
                val idx = current.indexOfFirst { it.card.name == cardName }
                if (idx >= 0) current[idx] = current[idx].copy(count = current[idx].count + 1)
                _scannedCards.value = current
            }
        }
    }

    fun decrementCard(cardName: String) {
        viewModelScope.launch {
            scannedCardsMutex.withLock {
                val current = _scannedCards.value.toMutableList()
                val idx = current.indexOfFirst { it.card.name == cardName }
                if (idx < 0) return@withLock
                if (current[idx].count <= 1) current.removeAt(idx) else current[idx] = current[idx].copy(count = current[idx].count - 1)
                _scannedCards.value = current
            }
        }
    }

    private suspend fun addScannedCard(card: CardEntity) {
        scannedCardsMutex.withLock {
            val current = _scannedCards.value.toMutableList()
            val idx = current.indexOfFirst { it.card.name == card.name }
            if (idx >= 0) current[idx] = current[idx].copy(count = current[idx].count + 1)
            else current.add(ScannedEntry(card, 1))
            _scannedCards.value = current
        }
    }

    fun addScannedToCollection() {
        viewModelScope.launch {
            val entries = scannedCardsMutex.withLock {
                val snapshot = _scannedCards.value.map {
                    CollectionEntryEntity(cardName = it.card.name, quantity = it.count)
                }
                if (snapshot.isNotEmpty()) _scannedCards.value = emptyList()
                snapshot
            }
            if (entries.isEmpty()) return@launch
            repository.addToCollection(entries)
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private fun setStatusTemporarily(status: ScanStatus) {
        _scanStatus.value = status
        viewModelScope.launch {
            delay(2000)
            if (_scanStatus.value == status) _scanStatus.value = ScanStatus.Idle
        }
    }

    override fun onCleared() {
        super.onCleared()
        recognizer.close()
    }

    companion object {
        const val SCORE_THRESHOLD = 40
        private const val FOUND_BANNER_MS = 1200L

        // Frame fingerprinting — 16×16 grayscale grid sampled from center of frame
        private const val FINGERPRINT_SIZE = 16
        private const val STABLE_THRESHOLD = 8.0       // max mean pixel diff for "stable"
        private const val CHANGE_THRESHOLD = 25.0       // min mean pixel diff for "scene changed"
        private const val CONTENT_VARIANCE_THRESHOLD = 200.0 // min variance to consider "has content"
        private const val STABLE_FRAMES_REQUIRED = 5    // consecutive stable frames before OCR

        /** Sample a 16×16 grayscale fingerprint from the reticle region of the Y plane.
         *  The reticle is centered at (50%, 32.5%) of the screen (middle of the top 65%,
         *  above the bottom panel). We map that screen position to sensor coordinates
         *  using the image's rotation degrees. */
        fun computeFingerprint(imageProxy: ImageProxy): IntArray {
            val yPlane = imageProxy.planes[0]
            val yBuffer = yPlane.buffer
            val rowStride = yPlane.rowStride
            val imgW = imageProxy.width
            val imgH = imageProxy.height
            val rotation = imageProxy.imageInfo.rotationDegrees

            // Reticle center in normalized screen coords (portrait)
            val screenCx = 0.5
            val screenCy = 0.5

            // Map screen coords to sensor coords based on rotation
            val sensorCxNorm: Double
            val sensorCyNorm: Double
            when (rotation) {
                90  -> { sensorCxNorm = 1.0 - screenCy; sensorCyNorm = screenCx }
                270 -> { sensorCxNorm = screenCy;        sensorCyNorm = 1.0 - screenCx }
                180 -> { sensorCxNorm = 1.0 - screenCx;  sensorCyNorm = 1.0 - screenCy }
                else -> { sensorCxNorm = screenCx;        sensorCyNorm = screenCy }
            }

            val cropW = imgW / 2
            val cropH = imgH / 2
            val centerX = (sensorCxNorm * imgW).toInt().coerceIn(cropW / 2, imgW - cropW / 2)
            val centerY = (sensorCyNorm * imgH).toInt().coerceIn(cropH / 2, imgH - cropH / 2)
            val startX = centerX - cropW / 2
            val startY = centerY - cropH / 2

            return IntArray(FINGERPRINT_SIZE * FINGERPRINT_SIZE) { i ->
                val fx = i % FINGERPRINT_SIZE
                val fy = i / FINGERPRINT_SIZE
                val srcX = startX + fx * cropW / FINGERPRINT_SIZE
                val srcY = startY + fy * cropH / FINGERPRINT_SIZE
                yBuffer[srcY * rowStride + srcX].toInt() and 0xFF
            }
        }

        /** Mean absolute difference between two fingerprints. */
        fun fingerprintDiff(a: IntArray, b: IntArray): Double {
            var sum = 0L
            for (i in a.indices) sum += kotlin.math.abs(a[i] - b[i])
            return sum.toDouble() / a.size
        }

        /** Variance of luminance values — low means uniform (empty surface), high means content. */
        fun fingerprintVariance(fp: IntArray): Double {
            val mean = fp.average()
            return fp.sumOf { (it - mean) * (it - mean) } / fp.size
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
}
