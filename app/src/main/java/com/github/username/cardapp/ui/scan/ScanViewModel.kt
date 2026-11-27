package com.github.username.cardapp.ui.scan

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.username.cardapp.data.CardFilterState
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: CardRepository,
    private val savedStateHandle: SavedStateHandle,
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

    /** False until the catalogue has loaded; a scan before then could only come back empty. */
    val catalogueReady: StateFlow<Boolean> = catalogue
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

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

    init {
        restoreScannedCards()
    }

    private val _scanStatus = MutableStateFlow<ScanStatus>(ScanStatus.Idle)
    val scanStatus: StateFlow<ScanStatus> = _scanStatus.asStateFlow()

    private val _lastOutcome = MutableStateFlow<ScanOutcome?>(null)
    val lastOutcome: StateFlow<ScanOutcome?> = _lastOutcome.asStateFlow()

    /** Main thread only. */
    private var nextOutcomeId = 0L

    private val _correctionOpen = MutableStateFlow(false)
    val correctionOpen: StateFlow<Boolean> = _correctionOpen.asStateFlow()

    // --- Scan log (debug builds only) ---
    //
    // Every scan's raw OCR, candidates and any later correction, exportable as JSON for
    // tuning the scorer offline.
    private val scanLogEnabled = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    private val scanLogLock = Any()
    private val scanLog = ArrayDeque<ScanLogEntry>()
    private val _scanLogSize = MutableStateFlow(0)
    val scanLogSize: StateFlow<Int> = _scanLogSize.asStateFlow()

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    // --- Multi-frame bursts ---
    //
    // Both scan modes read the card OcrFrameBurst.SIZE times and merge the scores rather
    // than trusting a single OCR pass. The burst decides which analyzer frames become
    // reads, spacing them out so they aren't near-duplicates.
    private val burst = OcrFrameBurst()

    private val _scanMode = MutableStateFlow(ScanMode.Manual)
    val scanMode: StateFlow<ScanMode> = _scanMode.asStateFlow()

    /** Decides when auto mode starts a burst; see [AutoScanDetector]. */
    private val autoDetector = AutoScanDetector()

    /**
     * Held while the mode changes, a burst starts or is abandoned, or the correction panel
     * opens, so that whatever cancels a burst cannot interleave with a burst starting.
     */
    private val modeLock = Any()

    /** The mode the latest burst was started in. Written under [modeLock]. */
    @Volatile private var burstMode = ScanMode.Manual

    fun toggleScanMode() = synchronized(modeLock) {
        val next = if (_scanMode.value == ScanMode.Manual) ScanMode.Auto else ScanMode.Manual
        _scanMode.value = next
        burst.cancel()
        autoDetector.reset()
        _scanStatus.value = idleStatus(next)
    }

    private fun idleStatus(mode: ScanMode): ScanStatus =
        if (mode == ScanMode.Auto) ScanStatus.AutoWatching else ScanStatus.Idle

    /**
     * Open a burst of [OcrFrameBurst.SIZE] reads in [mode]. Call with [modeLock] held.
     *
     * A watchdog abandons the burst if its reads haven't all come back in time — the camera
     * may never deliver frames (bind failure, camera taken by another app), and without it
     * the scan would spin forever.
     */
    private fun startBurstLocked(mode: ScanMode) {
        val generation = burst.restart()
        burstMode = mode
        _scanStatus.value = ScanStatus.Scanning
        viewModelScope.launch {
            delay(BURST_TIMEOUT_MS)
            synchronized(modeLock) {
                if (burst.cancelIfPending(generation)) abandonBurstLocked()
            }
        }
    }

    /** Reset after the burst in flight was dropped without a verdict. Call with [modeLock] held. */
    private fun abandonBurstLocked() {
        autoDetector.onBurstAbandoned()
        if (_scanStatus.value == ScanStatus.Scanning) _scanStatus.value = idleStatus(_scanMode.value)
    }

    /** Read [imageProxy] into the burst in flight if it wants this frame; otherwise release it. */
    private fun offerToBurst(imageProxy: ImageProxy) {
        val generation = burst.claim(imageProxy.imageInfo.timestamp)
        if (generation != null) captureAndRecognize(imageProxy, generation)
        else imageProxy.close()
    }

    /**
     * Open or close the correction panel. Opening it abandons any scan in flight: a result
     * landing mid-correction would replace the outcome being corrected.
     */
    fun setCorrectionOpen(open: Boolean) = synchronized(modeLock) {
        if (open && _lastOutcome.value == null) return@synchronized
        _correctionOpen.value = open
        if (open) {
            burst.cancel()
            abandonBurstLocked()
        }
    }

    fun requestScan() = synchronized(modeLock) {
        if (_scanMode.value != ScanMode.Manual || !canScan()) return@synchronized
        startBurstLocked(ScanMode.Manual)
    }

    private fun canScan(): Boolean = !_correctionOpen.value && catalogue.value.isNotEmpty()

    fun analyzeFrame(imageProxy: ImageProxy) {
        if (!canScan()) {
            imageProxy.close()
            return
        }
        when (_scanMode.value) {
            // Frames after the tap feed the burst until it has all its reads.
            ScanMode.Manual -> offerToBurst(imageProxy)
            ScanMode.Auto -> analyzeFrameAuto(imageProxy)
        }
    }

    // ── Auto mode ───────────────────────────────────────────────────────────────

    private fun analyzeFrameAuto(imageProxy: ImageProxy) {
        // Compute fingerprint directly from the Y (luminance) plane — no Bitmap allocation.
        val fingerprint = FrameFingerprintAnalyzer.computeFingerprint(imageProxy)
        val action = synchronized(modeLock) {
            // Re-checked under the lock: the mode or the correction panel may have changed
            // since analyzeFrame looked.
            if (_scanMode.value != ScanMode.Auto || !canScan()) return@synchronized AutoScanAction.Skip
            autoDetector.onFrame(fingerprint).also {
                when (it) {
                    AutoScanAction.StartBurst -> startBurstLocked(ScanMode.Auto)
                    AutoScanAction.Rearm -> _scanStatus.value = ScanStatus.AutoWatching
                    AutoScanAction.FeedBurst, AutoScanAction.Skip -> Unit
                }
            }
        }
        when (action) {
            // The frame that starts a burst is also its first read; later ones are fed to it.
            AutoScanAction.StartBurst, AutoScanAction.FeedBurst -> offerToBurst(imageProxy)
            AutoScanAction.Rearm, AutoScanAction.Skip -> imageProxy.close()
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

    /**
     * Score one finished read and resolve the burst if it was the last one outstanding.
     * A read that found no text at all counts as a failed frame, not a vote for nothing.
     */
    private fun onFrameRecognized(generation: Int, rawText: String?) {
        viewModelScope.launch(Dispatchers.Default) {
            val cards = catalogue.value
            val frame = if (!rawText.isNullOrBlank() && cards.isNotEmpty()) {
                OcrCardScorer.scoreFrame(rawText, cards)
            } else {
                null
            }
            val frames = burst.report(generation, frame) ?: return@launch
            val result = OcrCardScorer.combine(frames, cards.map { it.card })
            withContext(Dispatchers.Main) { finishBurst(generation, frames, result) }
        }
    }

    private suspend fun finishBurst(
        generation: Int,
        frames: List<OcrCardScorer.FrameScores>,
        result: OcrCardScorer.ScoreResult,
    ) {
        val matched = result.matched
        val status: ScanStatus
        val mode = synchronized(modeLock) {
            // A mode toggle or the correction panel opening since the last read landed
            // abandoned this burst; its verdict no longer belongs anywhere.
            if (!burst.isCurrent(generation)) return
            val mode = burstMode
            status = when {
                matched != null -> ScanStatus.Found(matched.name)
                mode == ScanMode.Auto -> ScanStatus.AutoWatching
                else -> ScanStatus.NotFound
            }
            if (mode == ScanMode.Auto) autoDetector.onBurstFinished(matched != null)
            _scanStatus.value = status
            mode
        }

        val id = nextOutcomeId++
        recordScan(id, frames, result)
        if (matched != null) addScannedCard(matched)
        _correctionOpen.value = false
        _lastOutcome.value = ScanOutcome(id, matched, result.top3)

        when {
            mode == ScanMode.Manual -> resetStatusLater(status)
            matched != null -> viewModelScope.launch {
                delay(FOUND_BANNER_MS)
                synchronized(modeLock) {
                    if (_scanMode.value == ScanMode.Auto && autoDetector.isWaitingForChange) {
                        _scanStatus.value = ScanStatus.AutoCooldown
                    }
                }
            }
        }
    }

    // ── Scanned cards list ──────────────────────────────────────────────────────

    /** Publish [entries] and save them so the pile survives process death. */
    private fun publishScanned(entries: List<ScannedEntry>) {
        _scannedCards.value = entries
        savedStateHandle[KEY_SCANNED_NAMES] = entries.map { it.card.name }.toTypedArray()
        savedStateHandle[KEY_SCANNED_COUNTS] = entries.map { it.count }.toIntArray()
    }

    /** Rebuild the pile saved before process death once the catalogue is back. */
    private fun restoreScannedCards() {
        val names = savedStateHandle.get<Array<String>>(KEY_SCANNED_NAMES) ?: return
        val counts = savedStateHandle.get<IntArray>(KEY_SCANNED_COUNTS) ?: return
        viewModelScope.launch {
            val byName = catalogue.first { it.isNotEmpty() }.associateBy { it.card.name }
            val restored = names.zip(counts.asList()).mapNotNull { (name, count) ->
                byName[name]?.let { ScannedEntry(it.card, count) }
            }
            mutateScanned { current ->
                current + restored.filter { saved -> current.none { it.card.name == saved.card.name } }
            }
        }
    }

    /** Apply a pure transform to the scanned-cards list under the mutex. */
    private suspend fun mutateScanned(transform: (List<ScannedEntry>) -> List<ScannedEntry>) {
        scannedCardsMutex.withLock {
            publishScanned(transform(_scannedCards.value))
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

    /**
     * Replace the card scan [outcomeId] added with [actualCardName]. Ignored unless that
     * scan is still the latest one, so a stale tap can't edit a different scan.
     */
    fun correctLastScan(outcomeId: Long, actualCardName: String) {
        viewModelScope.launch {
            scannedCardsMutex.withLock {
                val outcome = _lastOutcome.value?.takeIf { it.id == outcomeId } ?: return@launch
                val newCard = catalogue.value.firstOrNull { it.card.name == actualCardName }?.card
                    ?: return@launch
                val entries = _scannedCards.value
                val withoutOld = outcome.card?.let { ScannedCards.removeOne(entries, it.name) } ?: entries
                publishScanned(ScannedCards.addOrBumpToFront(withoutOld, newCard))
                _lastOutcome.value = outcome.copy(card = newCard)
            }
            _correctionOpen.value = false
            recordCorrection(outcomeId, actualCardName)
        }
    }

    fun addScannedToCollection() {
        viewModelScope.launch {
            val entries = scannedCardsMutex.withLock {
                val snapshot = _scannedCards.value.map {
                    CollectionEntryEntity(cardName = it.card.name, quantity = it.count)
                }
                if (snapshot.isNotEmpty()) {
                    publishScanned(emptyList())
                    // The committed cards are out of reach of the correction panel now.
                    _correctionOpen.value = false
                    _lastOutcome.value = null
                }
                snapshot
            }
            if (entries.isEmpty()) return@launch
            repository.addToCollection(entries)
        }
    }

    // ── Scan log ─────────────────────────────────────────────────────────────────

    private fun recordScan(
        id: Long,
        frames: List<OcrCardScorer.FrameScores>,
        result: OcrCardScorer.ScoreResult,
    ) {
        if (!scanLogEnabled) return
        val entry = ScanLogEntry(
            scanId = id,
            rawText = frames.joinToString("\n--- frame ---\n") { it.rawText },
            costCandidates = result.costCandidates,
            bestMatch = result.best?.name,
            bestScore = result.bestScore,
            top3 = result.top3,
            acceptedMatch = result.matched?.name,
        )
        synchronized(scanLogLock) {
            scanLog.addLast(entry)
            while (scanLog.size > MAX_SCAN_LOG_ENTRIES) scanLog.removeFirst()
            _scanLogSize.value = scanLog.size
        }
    }

    private fun recordCorrection(id: Long, actualCardName: String) {
        if (!scanLogEnabled) return
        synchronized(scanLogLock) {
            val index = scanLog.indexOfLast { it.scanId == id }
            if (index >= 0) scanLog[index] = scanLog[index].copy(actualCard = actualCardName)
        }
    }

    /**
     * The scan log as JSON, newest scans kept if it must be cut down: it is shared as
     * Intent text, which has to fit in a Binder transaction.
     */
    fun exportScanLog(): String {
        var entries = synchronized(scanLogLock) { scanLog.toList() }
        var json = scanLogJson.encodeToString(entries)
        while (json.length > MAX_EXPORT_CHARS && entries.size > 1) {
            entries = entries.drop((entries.size / 4).coerceAtLeast(1))
            json = scanLogJson.encodeToString(entries)
        }
        return json
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private fun resetStatusLater(status: ScanStatus) {
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

        /** Far longer than a healthy burst takes (three reads ~150 ms apart, plus OCR). */
        private const val BURST_TIMEOUT_MS = 10_000L

        private const val MAX_SCAN_LOG_ENTRIES = 100

        /** Characters, so at most ~200 KB as UTF-16 — well inside the ~1 MB Binder buffer. */
        private const val MAX_EXPORT_CHARS = 100_000

        private const val KEY_SCANNED_NAMES = "scanned_names"
        private const val KEY_SCANNED_COUNTS = "scanned_counts"

        private val scanLogJson = Json { prettyPrint = true }
    }
}
