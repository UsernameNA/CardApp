package com.github.username.cardapp.ui.scan

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.util.Log
import com.github.username.cardapp.data.CardRepository
import com.github.username.cardapp.data.local.CardEntity
import com.github.username.cardapp.data.local.CollectionEntryEntity
import com.github.username.cardapp.ui.common.CardFilterState
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /** The catalogue, prepared for OCR scoring whenever it changes rather than per read. */
    private val catalogue: StateFlow<List<OcrCardScorer.PreparedCard>> = repository.filteredCards(CardFilterState())
        .map { list -> OcrCardScorer.prepare(list.map { it.card }) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val prices: StateFlow<Map<String, Double>> = repository.priceMap
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _correctionQuery = MutableStateFlow("")
    val correctionResults: StateFlow<List<CardEntity>> = _correctionQuery
        .combine(catalogue) { query, cards ->
            if (query.length < 2) emptyList()
            else {
                val lower = query.lowercase()
                cards.asSequence().map { it.card }.filter { lower in it.name.lowercase() }.take(8).toList()
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
    /** Per scan, the card the user picked in the correction panel; "" when never corrected. */
    private val _scanLogActualCards = mutableListOf<String>()
    val scanLogSize: Int get() = synchronized(scanLogLock) { _scanLog.size }

    private val _lastScanCard = MutableStateFlow<CardEntity?>(null)
    val lastScanCard: StateFlow<CardEntity?> = _lastScanCard.asStateFlow()

    private val _unmatchedScan = MutableStateFlow(false)
    val unmatchedScan: StateFlow<Boolean> = _unmatchedScan.asStateFlow()

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    // --- Multi-frame bursts ---
    //
    // Both scan modes read the card OcrFrameBurst.SIZE times and merge the scores rather
    // than trusting a single OCR pass. The burst decides which analyzer frames become
    // reads, spacing them out so they aren't near-duplicates.
    private val burst = OcrFrameBurst()

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

    /** Fingerprint of the frame that triggered the burst in flight. */
    @Volatile private var burstFingerprint: IntArray? = null

    fun toggleScanMode() {
        val next = if (_scanMode.value == ScanMode.Manual) ScanMode.Auto else ScanMode.Manual
        _scanMode.value = next
        _scanStatus.value = if (next == ScanMode.Auto) ScanStatus.AutoWatching else ScanStatus.Idle
        cancelBurst()
        if (next == ScanMode.Auto) resetAutoState()
    }

    private fun resetAutoState() {
        autoState.set(AutoState.WaitingForCard)
        prevFingerprint = null
        stableFrameCount = 0
        scannedFingerprint = null
        failedFingerprint = null
    }

    /** Drop any burst in flight; its late frames will report against a stale generation. */
    private fun cancelBurst() = burst.cancel()

    /** Open a burst of [OcrFrameBurst.SIZE] reads. */
    private fun startBurst() {
        burst.restart()
    }

    /** Read [imageProxy] into the burst in flight if it wants this frame; otherwise release it. */
    private fun offerToBurst(imageProxy: ImageProxy) {
        val generation = burst.claim(imageProxy.imageInfo.timestamp)
        if (generation != null) captureAndRecognize(imageProxy, generation)
        else imageProxy.close()
    }

    @Volatile var correctionOpen = false

    fun requestScan() {
        if (correctionOpen) return
        _scanStatus.value = ScanStatus.Scanning
        startBurst()
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
        // Frames after the tap feed the burst until it has all its reads.
        offerToBurst(imageProxy)
    }

    // ── Auto mode ───────────────────────────────────────────────────────────────

    private fun analyzeFrameAuto(imageProxy: ImageProxy) {
        // Compute fingerprint directly from the Y (luminance) plane — no Bitmap allocation.
        val fingerprint = FrameFingerprintAnalyzer.computeFingerprint(imageProxy)
        val state: AutoState = autoState.get()

        when (state) {
            AutoState.WaitingForCard -> {
                // Track inter-frame stability
                val prev = prevFingerprint
                if (prev != null) {
                    if (FrameFingerprintAnalyzer.isStable(prev, fingerprint)) {
                        stableFrameCount++
                    } else {
                        stableFrameCount = 0
                        failedFingerprint = null   // scene changed, allow retry
                    }
                }
                prevFingerprint = fingerprint

                val hasContent = FrameFingerprintAnalyzer.hasContent(fingerprint)
                val notPreviouslyFailed = failedFingerprint?.let {
                    FrameFingerprintAnalyzer.hasChanged(it, fingerprint)
                } ?: true

                if (stableFrameCount >= FrameFingerprintAnalyzer.STABLE_FRAMES_REQUIRED && hasContent && notPreviouslyFailed) {
                    if (autoState.compareAndSet(AutoState.WaitingForCard, AutoState.OcrInFlight)) {
                        stableFrameCount = 0
                        _scanStatus.value = ScanStatus.Scanning
                        burstFingerprint = fingerprint
                        startBurst()
                        // This frame is the burst's first read; the remaining ones are
                        // claimed by later frames in the OcrInFlight branch.
                        offerToBurst(imageProxy)
                        return
                    }
                }
                imageProxy.close()
            }

            AutoState.OcrInFlight -> {
                prevFingerprint = fingerprint
                offerToBurst(imageProxy)
            }

            AutoState.WaitingForChange -> {
                val scanned = scannedFingerprint
                if (scanned != null && FrameFingerprintAnalyzer.hasChanged(scanned, fingerprint)) {
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
    // The pure matching logic lives in [OcrCardScorer]. A scan runs OcrFrameBurst.SIZE
    // reads; each one is scored as it lands and the burst is resolved once the last
    // report arrives.

    /**
     * Run one OCR read of [imageProxy] into burst [generation].
     *
     * Always closes the proxy — a leaked one starves the analyzer of buffers and freezes
     * the pipeline permanently — and always reports, so a failed frame cannot stall the
     * burst.
     */
    private fun captureAndRecognize(imageProxy: ImageProxy, generation: Int) {
        val bitmap: Bitmap
        val rotation: Int
        try {
            bitmap = imageProxy.toBitmap()
            rotation = imageProxy.imageInfo.rotationDegrees
        } catch (e: Exception) {
            Log.w("ScanViewModel", "Failed to read camera frame", e)
            onFrameRecognized(generation, null)
            return
        } finally {
            imageProxy.close()
        }

        recognizer.process(InputImage.fromBitmap(bitmap, rotation))
            .addOnSuccessListener { onFrameRecognized(generation, it.text) }
            .addOnFailureListener { onFrameRecognized(generation, null) }
    }

    /** Score one finished read and resolve the burst if it was the last one outstanding. */
    private fun onFrameRecognized(generation: Int, rawText: String?) {
        viewModelScope.launch(Dispatchers.Default) {
            val cards = catalogue.value
            val frame = if (rawText != null && cards.isNotEmpty()) {
                OcrCardScorer.scoreFrame(rawText, cards)
            } else {
                null
            }
            val frames = burst.report(generation, frame) ?: return@launch
            finishBurst(frames, cards.map { it.card })
        }
    }

    private suspend fun finishBurst(
        frames: List<OcrCardScorer.FrameScores>,
        cards: List<CardEntity>,
    ) {
        val result = OcrCardScorer.combine(frames, cards)
        recordScan(frames, result)

        val matched = result.matched
        if (matched != null) {
            addScannedCard(matched)
            _lastScanCard.value = matched
            _unmatchedScan.value = false
        } else {
            _lastScanCard.value = null
            _unmatchedScan.value = true
        }

        if (_scanMode.value == ScanMode.Auto) finishAutoBurst(matched)
        else setStatusTemporarily(
            if (matched != null) ScanStatus.Found(matched.name) else ScanStatus.NotFound
        )
    }

    private fun finishAutoBurst(matched: CardEntity?) {
        val fingerprint = burstFingerprint
        if (matched != null) {
            scannedFingerprint = fingerprint
            autoState.set(AutoState.WaitingForChange)
            _scanStatus.value = ScanStatus.Found(matched.name)
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

    /** Publish the burst outcome to the debug overlay and the exportable scan log. */
    private fun recordScan(
        frames: List<OcrCardScorer.FrameScores>,
        result: OcrCardScorer.ScoreResult,
    ) {
        val info = ScanDebugInfo(
            rawText = frames.joinToString("\n--- frame ---\n") { it.rawText },
            costCandidates = result.costCandidates,
            bestCardName = result.best?.name,
            bestScore = result.bestScore,
            top3 = result.top3,
            acceptedCardName = result.matched?.name,
        )
        _debugInfo.value = info
        synchronized(scanLogLock) {
            _scanLog.add(info)
            _scanLogActualCards.add("")
        }
    }

    // ── Scanned cards list ──────────────────────────────────────────────────────

    /** Apply a pure transform to the scanned-cards list under the mutex. */
    private suspend fun mutateScanned(transform: (List<ScannedEntry>) -> List<ScannedEntry>) {
        scannedCardsMutex.withLock {
            _scannedCards.value = transform(_scannedCards.value)
        }
    }

    private fun launchScannedMutation(transform: (List<ScannedEntry>) -> List<ScannedEntry>) {
        viewModelScope.launch { mutateScanned(transform) }
    }

    fun incrementCard(cardName: String) =
        launchScannedMutation { ScannedCards.incrementExisting(it, cardName) }

    fun decrementCard(cardName: String) =
        launchScannedMutation { ScannedCards.removeOne(it, cardName) }

    private suspend fun addScannedCard(card: CardEntity) =
        mutateScanned { ScannedCards.addOrBumpToFront(it, card) }

    fun correctLastScan(actualCardName: String) {
        val oldName = _lastScanCard.value?.name
        val wasUnmatched = _unmatchedScan.value

        viewModelScope.launch {
            synchronized(scanLogLock) {
                if (_scanLogActualCards.isNotEmpty()) {
                    _scanLogActualCards[_scanLogActualCards.lastIndex] = actualCardName
                }
            }

            val newCard = catalogue.value.firstOrNull { it.card.name == actualCardName }?.card ?: return@launch

            mutateScanned { entries ->
                val withoutOld =
                    if (!wasUnmatched && oldName != null) ScannedCards.removeOne(entries, oldName)
                    else entries
                ScannedCards.addOrBumpToFront(withoutOld, newCard)
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
                    acceptedMatch = info.acceptedCardName,
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
        recognizer.close()
    }

    companion object {
        private const val FOUND_BANNER_MS = 1200L
        private val scanLogJson = kotlinx.serialization.json.Json { prettyPrint = true }
    }
}
