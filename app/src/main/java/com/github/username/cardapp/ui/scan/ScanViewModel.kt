package com.github.username.cardapp.ui.scan

import android.app.Application
import androidx.camera.core.ImageProxy
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.username.cardapp.data.CardRepository
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

        val normalized = rawText.lowercase()

        val costCandidates = Regex("""\b(\d{1,2})\b""").findAll(rawText)
            .mapNotNull { it.value.toIntOrNull() }
            .filter { it in 1..15 }
            .toSet()

        val ocrTokens = normalized.split(Regex("[^a-z0-9']+")).filter { it.isNotEmpty() }
        val ocrWords = ocrTokens.filter { it.length > 4 }.toSet()

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

    // Scores a card against OCR text using four signals:
    //
    //   Name match — card names use a decorative font so OCR may garble individual
    //   characters. We score in three tiers:
    //     100 pts  exact full-name substring present
    //      25 pts  per name-word with an exact token match in the OCR text
    //      15 pts  per name-word with a fuzzy token match within edit-distance tolerance
    //              (tolerance = 1 for 4–6 char words, 2 for 7+ char words)
    //
    //   Type match    — 10 pts if the card's type (Minion, Spell, Site, …) appears in
    //                   the OCR text; supporting signal since the type line is scanned to
    //
    //   Cost match    — 35 pts if the card's cost appears as a number (non-site only)
    //
    //   Rules overlap — 4 pts per significant word shared between OCR and rules text
    //                   (rules text uses a cleaner font; exact matching is fine here)
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
        val cardNameLower = card.name.lowercase()
        if (normalizedText.contains(cardNameLower)) {
            score += 100
        } else {
            for (nameWord in cardNameLower.split(" ").filter { it.length > 3 }) {
                val tolerance = if (nameWord.length >= 7) 2 else 1
                val bestDist = ocrTokens.minOfOrNull { levenshtein(nameWord, it) } ?: Int.MAX_VALUE
                score += when {
                    bestDist == 0 -> 25
                    bestDist <= tolerance -> 15
                    else -> 0
                }
            }
        }

        // --- Type (both name and type use stylized fonts, so apply same fuzzy logic) ---
        val cardTypeLower = card.cardType.lowercase()
        if (cardTypeLower.length > 3) {
            val tolerance = if (cardTypeLower.length >= 7) 2 else 1
            val bestDist = ocrTokens.minOfOrNull { levenshtein(cardTypeLower, it) } ?: Int.MAX_VALUE
            if (bestDist <= tolerance) score += 10
        }

        // --- Cost ---
        val isSite = card.cardType.lowercase() == "site"
        if (!isSite && costCandidates.contains(card.cost)) {
            score += 35
        }

        // --- Rules text overlap ---
        if (card.rulesText.isNotEmpty()) {
            val rulesWords = card.rulesText.lowercase()
                .split(Regex("[^a-z0-9']+"))
                .filter { it.length > 4 }
                .toSet()
            score += rulesWords.intersect(ocrWords).size * 4
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

        // Standard Levenshtein edit distance (substitution, insertion, deletion each cost 1).
        // Two pre-allocated rows are swapped each iteration — no heap allocations in the loop.
        fun levenshtein(a: String, b: String): Int {
            if (a == b) return 0
            if (a.isEmpty()) return b.length
            if (b.isEmpty()) return a.length
            var prev = IntArray(b.length + 1) { it }
            var curr = IntArray(b.length + 1)
            for (i in 1..a.length) {
                curr[0] = i
                for (j in 1..b.length) {
                    curr[j] = if (a[i - 1] == b[j - 1]) {
                        prev[j - 1]
                    } else {
                        1 + minOf(prev[j], curr[j - 1], prev[j - 1])
                    }
                }
                val tmp = prev; prev = curr; curr = tmp
            }
            return prev[b.length]
        }
    }
}
