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
        val bitmap: Bitmap
        val sensorRotation: Int
        try {
            bitmap = imageProxy.toBitmap()
            sensorRotation = imageProxy.imageInfo.rotationDegrees
        } catch (e: Exception) {
            // Without the finally-close a thrown toBitmap() would starve the
            // analyzer of buffers and freeze the scan pipeline permanently.
            Log.w("ScanViewModel", "Failed to read camera frame", e)
            setStatusTemporarily(ScanStatus.NotFound)
            return
        } finally {
            imageProxy.close()
        }

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

                        // Only create a Bitmap when we actually need OCR
                        val bitmap: Bitmap
                        val rotation: Int
                        try {
                            bitmap = imageProxy.toBitmap()
                            rotation = imageProxy.imageInfo.rotationDegrees
                        } catch (e: Exception) {
                            Log.w("ScanViewModel", "Failed to read camera frame", e)
                            autoState.set(AutoState.WaitingForCard)
                            _scanStatus.value = ScanStatus.AutoWatching
                            return
                        } finally {
                            imageProxy.close()
                        }

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
    // The pure matching logic lives in [OcrCardScorer]; this wrapper feeds it the
    // current catalogue snapshot and records the result for debug/export state.

    private fun scoreOcrText(rawText: String): CardEntity? {
        val cards = allCards.value
        if (cards.isEmpty()) return null

        val result = OcrCardScorer.score(rawText, cards)

        val info = ScanDebugInfo(
            rawText = rawText,
            costCandidates = result.costCandidates,
            bestCardName = result.best?.name,
            bestScore = result.bestScore,
            top3 = result.top3,
        )
        _debugInfo.value = info
        synchronized(scanLogLock) {
            _scanLog.add(info)
            _scanLogActualCards.add(result.matched?.name ?: "")
        }

        return result.matched
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

            val newCard = allCards.value.find { it.name == actualCardName } ?: return@launch

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
