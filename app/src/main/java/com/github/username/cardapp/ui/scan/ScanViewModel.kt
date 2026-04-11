package com.github.username.cardapp.ui.scan

import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.util.Log
import com.github.username.cardapp.data.CardRepository
import com.github.username.cardapp.data.local.CardEntity
import com.github.username.cardapp.data.local.CollectionEntryEntity
import com.google.mlkit.common.MlKit
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: CardRepository,
) : ViewModel() {

    init {
        try {
            MlKit.initialize(context)
        } catch (e: Exception) {
            Log.w("ScanViewModel", "ML Kit initialization failed", e)
        }
        repository.ensureDataLoaded()
    }

    private val allCards: StateFlow<List<CardEntity>> = repository.filteredCards(
        com.github.username.cardapp.ui.common.CardFilterState()
    ).map { list -> list.map { it.card } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val prices: StateFlow<Map<String, Double>> = repository.priceMap
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _correctionQuery = MutableStateFlow("")
    val correctionResults: StateFlow<List<CardEntity>> = _correctionQuery
        .combine(allCards) { query, cards ->
            if (query.length < 2) emptyList()
            else {
                val lower = query.lowercase()
                cards.filter { lower in it.name.lowercase() }.take(8)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateCorrectionQuery(query: String) {
        _correctionQuery.value = query
    }


    private val _scannedCards = MutableStateFlow<List<ScannedEntry>>(emptyList())
    val scannedCards: StateFlow<List<ScannedEntry>> = _scannedCards.asStateFlow()
    private val scannedCardsMutex = Mutex()

    private val _scanStatus = MutableStateFlow<ScanStatus>(ScanStatus.Idle)
    val scanStatus: StateFlow<ScanStatus> = _scanStatus.asStateFlow()

    private val _debugInfo = MutableStateFlow<ScanDebugInfo?>(null)
    val debugInfo: StateFlow<ScanDebugInfo?> = _debugInfo.asStateFlow()

    private val scanLogLock = Any()
    private val _scanLog = mutableListOf<ScanDebugInfo>()
    private val _scanLogActualCards = mutableListOf<String>()
    val scanLogSize: Int get() = synchronized(scanLogLock) { _scanLog.size }

    private val _lastScanCard = MutableStateFlow<CardEntity?>(null)
    val lastScanCard: StateFlow<CardEntity?> = _lastScanCard.asStateFlow()

    private val _unmatchedScan = MutableStateFlow(false)
    val unmatchedScan: StateFlow<Boolean> = _unmatchedScan.asStateFlow()

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

    @Volatile var correctionOpen = false

    fun requestScan() {
        if (correctionOpen) return
        _scanStatus.value = ScanStatus.Scanning
        pendingScan.set(true)
    }

    fun analyzeFrame(imageProxy: ImageProxy) {
        if (correctionOpen) { imageProxy.close(); return }
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
                        _lastScanCard.value = result
                        _unmatchedScan.value = false
                        setStatusTemporarily(ScanStatus.Found(result.name))
                    } else {
                        _lastScanCard.value = null
                        _unmatchedScan.value = true
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
                                        _lastScanCard.value = result
                                        _unmatchedScan.value = false
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
                                        _lastScanCard.value = null
                                        _unmatchedScan.value = true
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
    //
    // The OCR text is split into a "name zone" (first 1–2 lines, where the card
    // name appears) and a "body zone" (rules, flavor, artist text). Name matches
    // in the name zone score 3–6× higher than in the body, preventing rules-text
    // keywords (e.g. "Dispel", "Rubble") from outscoring the actual card name.

    private fun scoreOcrText(rawText: String): CardEntity? {
        val cards = allCards.value
        if (cards.isEmpty()) return null

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

        val info = ScanDebugInfo(
            rawText = rawText,
            costCandidates = costCandidates,
            bestCardName = best?.first?.name,
            bestScore = best?.second ?: 0,
            top3 = top3,
        )
        _debugInfo.value = info
        synchronized(scanLogLock) {
            _scanLog.add(info)
            _scanLogActualCards.add(
                if (best != null && best.second >= SCORE_THRESHOLD) best.first.name else ""
            )
        }

        return if (best != null && best.second >= SCORE_THRESHOLD) best.first else null
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
        val cardNameClean = stripPunctuation(card.name.lowercase())
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
        val cardTypeLower = card.cardType.lowercase()
        if (cardTypeLower.length > 3) {
            val tol2 = if (cardTypeLower.length >= 7) 4 else 2
            val bestDist = allTokens.minOfOrNull { levenshtein(cardTypeLower, it) } ?: Int.MAX_VALUE
            if (bestDist <= tol2) score += 10
        }

        // ── D. Subtype match (fuzzy, anywhere in text) ─────────────────────────
        if (card.subTypes.isNotEmpty()) {
            for (sub in card.subTypes.lowercase().split(Regex("[,/\\s]+")).filter { it.length > 2 }) {
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
            val rulesWords = card.rulesText.lowercase()
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
            if (idx >= 0) {
                val updated = current[idx].copy(count = current[idx].count + 1)
                current.removeAt(idx)
                current.add(0, updated)
            } else {
                current.add(0, ScannedEntry(card, 1))
            }
            _scannedCards.value = current
        }
    }

    fun correctLastScan(actualCardName: String) {
        val oldName = _lastScanCard.value?.name
        val wasUnmatched = _unmatchedScan.value

        viewModelScope.launch {
            synchronized(scanLogLock) {
                if (_scanLogActualCards.isNotEmpty()) {
                    _scanLogActualCards[_scanLogActualCards.lastIndex] = actualCardName
                }
            }

            val newCard = allCards.value.find { it.name == actualCardName } ?: return@launch

            scannedCardsMutex.withLock {
                val current = _scannedCards.value.toMutableList()

                if (!wasUnmatched && oldName != null) {
                    val oldIdx = current.indexOfFirst { it.card.name == oldName }
                    if (oldIdx >= 0) {
                        if (current[oldIdx].count <= 1) {
                            current.removeAt(oldIdx)
                        } else {
                            current[oldIdx] = current[oldIdx].copy(count = current[oldIdx].count - 1)
                        }
                    }
                }

                val newIdx = current.indexOfFirst { it.card.name == actualCardName }
                if (newIdx >= 0) {
                    val updated = current[newIdx].copy(count = current[newIdx].count + 1)
                    current.removeAt(newIdx)
                    current.add(0, updated)
                } else {
                    current.add(0, ScannedEntry(newCard, 1))
                }

                _scannedCards.value = current
            }

            _lastScanCard.value = newCard
            _unmatchedScan.value = false
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

    // ── Export scan log ──────────────────────────────────────────────────────────

    fun exportScanLog(): String {
        val entries = synchronized(scanLogLock) {
            _scanLog.mapIndexed { index, info ->
                ScanLogEntry(
                    rawText = info.rawText,
                    costCandidates = info.costCandidates,
                    bestMatch = info.bestCardName,
                    bestScore = info.bestScore,
                    top3 = info.top3,
                    actualCard = _scanLogActualCards.getOrElse(index) { "" },
                )
            }
        }
        return scanLogJson.encodeToString(entries)
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
        const val SCORE_THRESHOLD = 50
        private const val FOUND_BANNER_MS = 1200L
        private val scanLogJson = kotlinx.serialization.json.Json { prettyPrint = true }

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
}
