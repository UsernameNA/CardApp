package com.github.username.cardapp.ui.scan

import android.app.Application
import androidx.camera.core.ImageProxy
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.username.cardapp.data.CardRepository
import com.github.username.cardapp.data.local.AppDatabase
import com.github.username.cardapp.data.local.CardEntity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

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

    // Auto mode: tracks the last scanned card and how long to ignore it
    private val lastAutoAnalysisMs = AtomicLong(0L)
    @Volatile private var lastAutoScannedName: String? = null
    @Volatile private var autoScanCooldownUntil: Long = 0L

    fun toggleScanMode() {
        val next = if (_scanMode.value == ScanMode.Manual) ScanMode.Auto else ScanMode.Manual
        _scanMode.value = next
        _scanStatus.value = if (next == ScanMode.Auto) ScanStatus.AutoWatching else ScanStatus.Idle
        if (next == ScanMode.Auto) lastAutoScannedName = null
    }

    fun requestScan() {
        _scanStatus.value = ScanStatus.Scanning
        pendingScan.set(true)
    }

    fun analyzeFrame(imageProxy: ImageProxy) {
        when (_scanMode.value) {
            ScanMode.Manual -> {
                if (!pendingScan.compareAndSet(true, false)) {
                    imageProxy.close()
                    return
                }
            }
            ScanMode.Auto -> {
                // Throttle to roughly one OCR call per second to avoid overloading the recognizer
                val now = System.currentTimeMillis()
                if (now - lastAutoAnalysisMs.get() < AUTO_ANALYSIS_INTERVAL_MS) {
                    imageProxy.close()
                    return
                }
                lastAutoAnalysisMs.set(now)
            }
        }

        // toBitmap() returns the raw sensor image without applying rotation,
        // so we pass rotationDegrees to tell ML Kit how to orient it.
        // ML Kit Text Recognition v2 detects text at any angle within the image
        // automatically, so a single call handles both upright and sideways cards.
        val bitmap = imageProxy.toBitmap()
        val sensorRotation = imageProxy.imageInfo.rotationDegrees
        imageProxy.close()

        recognizer.process(InputImage.fromBitmap(bitmap, sensorRotation))
            .addOnSuccessListener { visionText ->
                viewModelScope.launch(Dispatchers.Default) {
                    matchCardName(visionText.text)
                }
            }
            .addOnFailureListener { if (_scanMode.value == ScanMode.Manual) setStatusTemporarily(ScanStatus.NotFound) }
    }

    private suspend fun matchCardName(rawText: String) {
        val cards = allCards.value
        if (cards.isEmpty()) {
            setStatusTemporarily(ScanStatus.NotFound)
            return
        }

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

        if (best != null && bestScore >= SCORE_THRESHOLD) {
            // In auto mode, skip if this is the same card we just scanned and still cooling down
            if (_scanMode.value == ScanMode.Auto &&
                best.name == lastAutoScannedName &&
                System.currentTimeMillis() < autoScanCooldownUntil
            ) return
            addScannedCard(best)
        } else {
            if (_scanMode.value == ScanMode.Manual) setStatusTemporarily(ScanStatus.NotFound)
        }
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

        if (_scanMode.value == ScanMode.Auto) {
            lastAutoScannedName = card.name
            autoScanCooldownUntil = System.currentTimeMillis() + AUTO_COOLDOWN_MS
            _scanStatus.value = ScanStatus.Found(card.name)
            viewModelScope.launch {
                delay(FOUND_BANNER_MS)
                if (_scanMode.value == ScanMode.Auto) {
                    _scanStatus.value = ScanStatus.AutoCooldown
                    delay(AUTO_COOLDOWN_MS - FOUND_BANNER_MS)
                    if (_scanMode.value == ScanMode.Auto) {
                        _scanStatus.value = ScanStatus.AutoWatching
                        lastAutoScannedName = null
                    }
                }
            }
        } else {
            setStatusTemporarily(ScanStatus.Found(card.name))
        }
    }

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
        private const val AUTO_ANALYSIS_INTERVAL_MS = 900L   // max ~1 OCR call/sec in auto mode
        private const val AUTO_COOLDOWN_MS = 2500L           // lock out same card for 2.5s after scan
        private const val FOUND_BANNER_MS = 1200L            // how long to show "FOUND!" before transitioning

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
