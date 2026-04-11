package com.github.username.cardapp.ui.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.activity.ComponentActivity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import com.github.username.cardapp.data.local.CardEntity
import com.github.username.cardapp.ui.common.CardRow
import com.github.username.cardapp.ui.theme.BurgundyAccent
import com.github.username.cardapp.ui.theme.CardAppTheme
import com.github.username.cardapp.ui.theme.CreamFaded
import com.github.username.cardapp.ui.theme.CreamMuted
import com.github.username.cardapp.ui.theme.CreamPrimary
import com.github.username.cardapp.ui.theme.GoldDark
import com.github.username.cardapp.ui.theme.GoldLight
import com.github.username.cardapp.ui.theme.GoldMuted
import com.github.username.cardapp.ui.theme.GoldPrimary
import com.github.username.cardapp.ui.theme.LeatherDark
import com.github.username.cardapp.ui.theme.LeatherDeep
import com.github.username.cardapp.ui.theme.LeatherMid
import com.github.username.cardapp.ui.theme.Typography

@Composable
fun ScanScreen(
    onBack: () -> Unit,
    onCardClick: (String) -> Unit = {},
    vm: ScanViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scannedCards by vm.scannedCards.collectAsState()
    val scanStatus by vm.scanStatus.collectAsState()
    val scanMode by vm.scanMode.collectAsState()
    val debugInfo by vm.debugInfo.collectAsState()
    val prices by vm.prices.collectAsState()
    val lastScanCard by vm.lastScanCard.collectAsState()
    val unmatchedScan by vm.unmatchedScan.collectAsState()
    val correctionResults by vm.correctionResults.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Keep screen awake while in auto-scan mode
    val activity = context as? ComponentActivity
    DisposableEffect(scanMode) {
        if (scanMode == ScanMode.Auto) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    ScanScreenContent(
        scannedCards = scannedCards,
        scanStatus = scanStatus,
        scanMode = scanMode,
        prices = prices,
        scanLogSize = vm.scanLogSize,
        hasCameraPermission = hasCameraPermission,
        onBack = onBack,
        onCardClick = onCardClick,
        onScanTap = vm::requestScan,
        onToggleScanMode = vm::toggleScanMode,
        onIncrement = vm::incrementCard,
        onDecrement = vm::decrementCard,
        onAddToCollection = vm::addScannedToCollection,
        onExportScanLog = {
            val json = vm.exportScanLog()
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_TEXT, json)
                putExtra(Intent.EXTRA_SUBJECT, "scan_log.json")
            }
            context.startActivity(Intent.createChooser(intent, "Export Scan Log"))
        },
        lastScanCard = lastScanCard,
        unmatchedScan = unmatchedScan,
        correctionResults = correctionResults,
        latestTop3 = debugInfo?.top3 ?: emptyList(),
        onCorrectLastScan = vm::correctLastScan,
        onCorrectionOpenChanged = { vm.correctionOpen = it },
        onCorrectionQueryChanged = vm::updateCorrectionQuery,
        onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
        cameraContent = {
            CameraPreviewSurface(
                onFrame = vm::analyzeFrame,
                modifier = Modifier.fillMaxSize()
            )
        },
    )
}

@Composable
private fun ScanScreenContent(
    scannedCards: List<ScannedEntry>,
    scanStatus: ScanStatus,
    scanMode: ScanMode,
    prices: Map<String, Double> = emptyMap(),
    scanLogSize: Int = 0,
    hasCameraPermission: Boolean,
    onBack: () -> Unit,
    onCardClick: (String) -> Unit = {},
    onScanTap: () -> Unit,
    onToggleScanMode: () -> Unit,
    onIncrement: (String) -> Unit,
    onDecrement: (String) -> Unit,
    onAddToCollection: () -> Unit,
    onExportScanLog: () -> Unit = {},
    lastScanCard: CardEntity? = null,
    unmatchedScan: Boolean = false,
    correctionResults: List<CardEntity> = emptyList(),
    latestTop3: List<ScanCandidate> = emptyList(),
    onCorrectLastScan: (String) -> Unit = {},
    onCorrectionOpenChanged: (Boolean) -> Unit = {},
    onCorrectionQueryChanged: (String) -> Unit = {},
    onRequestPermission: () -> Unit,
    cameraContent: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LeatherDeep),
    ) {
        // Layers 1 & 2: Camera preview + reticle, or permission denied
        if (hasCameraPermission) {
            cameraContent()
            ReticleOverlay(modifier = Modifier.fillMaxSize())
        } else {
            PermissionDeniedContent(
                onRequestAgain = onRequestPermission,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Layer 3: Scan result overlay (replaces debug overlay)
        ScanResultOverlay(
            lastScanCard = lastScanCard,
            unmatchedScan = unmatchedScan,
            top3 = latestTop3,
            correctionResults = correctionResults,
            onCorrect = onCorrectLastScan,
            onCorrectionOpenChanged = onCorrectionOpenChanged,
            onCorrectionQueryChanged = onCorrectionQueryChanged,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 8.dp, start = 56.dp, end = 16.dp),
        )

        // Layer 4: Bottom panel
        ScannedCardsPanel(
            cards = scannedCards,
            scanStatus = scanStatus,
            scanMode = scanMode,
            prices = prices,
            scanLogSize = scanLogSize,
            lastScanCardName = lastScanCard?.name,
            onCardClick = onCardClick,
            onScanTap = onScanTap,
            onToggleScanMode = onToggleScanMode,
            onIncrement = onIncrement,
            onDecrement = onDecrement,
            onAddToCollection = onAddToCollection,
            onExportScanLog = onExportScanLog,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.35f),
        )

        // Layer 5: Back button
        val backInteraction = remember { MutableInteractionSource() }
        val backPressed by backInteraction.collectIsPressedAsState()
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp)
                .size(40.dp)
                .clickable(
                    interactionSource = backInteraction,
                    indication = null,
                    onClick = onBack,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "\u2190",
                style = Typography.titleLarge.copy(
                    color = if (backPressed) GoldLight else GoldPrimary,
                ),
            )
        }
    }
}


@Composable
private fun ReticleOverlay(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "reticle")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Canvas(modifier = modifier) {
        val panelFraction = 0.35f
        val availH = size.height * (1f - panelFraction)
        val cardW = size.width * 0.9f
        val cardH = cardW / 0.72f
        val cx = size.width / 2f
        val cy = availH / 2f

        val left = cx - cardW / 2f
        val top = cy - cardH / 4f
        val right = cx + cardW / 2f
        val bracketLen = cardW * 0.18f
        val strokePx = 2.dp.toPx()
        val thinStroke = strokePx * 0.5f
        val alpha = 0.55f + pulse * 0.45f
        val goldColor = GoldPrimary.copy(alpha = alpha)
        val goldLight = GoldLight.copy(alpha = alpha * 0.85f)
        val goldFaint = GoldMuted.copy(alpha = alpha * 0.4f)
        val dSize = 4.dp.toPx()
        val tickLen = 6.dp.toPx()
        val inset = 8.dp.toPx()

        // ── Primary L-brackets ──
        // Top-left
        drawLine(goldColor, Offset(left, top + bracketLen), Offset(left, top), strokePx)
        drawLine(goldColor, Offset(left, top), Offset(left + bracketLen, top), strokePx)
        // Top-right
        drawLine(goldColor, Offset(right - bracketLen, top), Offset(right, top), strokePx)
        drawLine(goldColor, Offset(right, top), Offset(right, top + bracketLen), strokePx)

        // ── Inner accent L-brackets (thinner, inset) ──
        drawLine(
            goldFaint,
            Offset(left + inset, top + inset),
            Offset(left + inset, top + inset + bracketLen * 0.5f),
            thinStroke
        )
        drawLine(
            goldFaint,
            Offset(left + inset, top + inset),
            Offset(left + inset + bracketLen * 0.5f, top + inset),
            thinStroke
        )
        drawLine(
            goldFaint,
            Offset(right - inset, top + inset),
            Offset(right - inset, top + inset + bracketLen * 0.5f),
            thinStroke
        )
        drawLine(
            goldFaint,
            Offset(right - inset, top + inset),
            Offset(right - inset - bracketLen * 0.5f, top + inset),
            thinStroke
        )

        // ── Connecting line between brackets (faint horizontal rule) ──
        drawLine(
            goldFaint,
            Offset(left + bracketLen + dSize * 2, top),
            Offset(right - bracketLen - dSize * 2, top),
            thinStroke
        )

        // ── Corner diamonds ──
        listOf(Offset(left, top), Offset(right, top)).forEach { corner ->
            val diamondPath = Path().apply {
                moveTo(corner.x, corner.y - dSize)
                lineTo(corner.x + dSize * 0.65f, corner.y)
                lineTo(corner.x, corner.y + dSize)
                lineTo(corner.x - dSize * 0.65f, corner.y)
                close()
            }
            drawPath(diamondPath, goldLight, style = Stroke(strokePx * 0.8f))
        }

        // ── Center diamond on the connecting line ──
        val centerDiamond = Path().apply {
            val ds = dSize * 0.7f
            moveTo(cx, top - ds)
            lineTo(cx + ds * 0.65f, top)
            lineTo(cx, top + ds)
            lineTo(cx - ds * 0.65f, top)
            close()
        }
        drawPath(centerDiamond, goldColor)

        // ── Flanking tick marks along connecting line ──
        val tickPositions = listOf(0.25f, 0.4f, 0.6f, 0.75f)
        for (frac in tickPositions) {
            val tx = left + (right - left) * frac
            drawLine(
                goldFaint,
                Offset(tx, top - tickLen * 0.5f),
                Offset(tx, top + tickLen * 0.5f),
                thinStroke
            )
        }

        // ── Bracket end nubs ──
        val nubR = 1.8f
        drawCircle(goldLight, nubR, Offset(left + bracketLen, top))
        drawCircle(goldLight, nubR, Offset(left, top + bracketLen))
        drawCircle(goldLight, nubR, Offset(right - bracketLen, top))
        drawCircle(goldLight, nubR, Offset(right, top + bracketLen))

        // ── Center aim dot ──
        drawCircle(GoldLight.copy(alpha = alpha * 0.6f), 2.5.dp.toPx(), Offset(cx, cy * 1.5f))
    }
}

@Composable
private fun ScannedCardsPanel(
    cards: List<ScannedEntry>,
    scanStatus: ScanStatus,
    scanMode: ScanMode,
    modifier: Modifier = Modifier,
    prices: Map<String, Double> = emptyMap(),
    scanLogSize: Int = 0,
    onCardClick: (String) -> Unit = {},
    onScanTap: () -> Unit = {},
    onToggleScanMode: () -> Unit = {},
    onIncrement: (String) -> Unit = {},
    onDecrement: (String) -> Unit = {},
    onAddToCollection: () -> Unit = {},
    onExportScanLog: () -> Unit = {},
    lastScanCardName: String? = null,
) {
    var selectedCardName by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .background(LeatherMid.copy(alpha = 0.93f))
            .drawWithContent {
                drawContent()
                drawLine(
                    GoldMuted.copy(alpha = 0.6f),
                    Offset(0f, 0f),
                    Offset(size.width, 0f),
                    1.dp.toPx(),
                )
            },
    ) {
        // Panel header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                thickness = 0.7.dp,
                color = GoldMuted.copy(alpha = 0.45f),
            )
            Text(
                text = "  SCANNED  ",
                style = Typography.labelLarge.copy(color = GoldPrimary),
            )
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                thickness = 0.7.dp,
                color = GoldMuted.copy(alpha = 0.45f),
            )
            Spacer(Modifier.width(12.dp))

            // AUTO mode toggle
            val autoActive = scanMode == ScanMode.Auto
            val autoInteraction = remember { MutableInteractionSource() }
            val autoPressed by autoInteraction.collectIsPressedAsState()
            Box(
                modifier = Modifier
                    .border(
                        1.dp,
                        if (autoPressed) GoldLight else if (autoActive) GoldPrimary else GoldDark,
                        RoundedCornerShape(2.dp),
                    )
                    .background(if (autoPressed || autoActive) GoldDark.copy(alpha = 0.35f) else Color.Transparent)
                    .clickable(
                        interactionSource = autoInteraction,
                        indication = null,
                        onClick = onToggleScanMode,
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    "AUTO",
                    style = Typography.labelLarge.copy(
                        color = if (autoPressed) GoldLight else if (autoActive) GoldPrimary else GoldDark,
                    ),
                )
            }

            Spacer(Modifier.width(8.dp))

            if (scanMode == ScanMode.Manual) {
                // Manual mode: normal SCAN button
                val scanInteraction = remember { MutableInteractionSource() }
                val scanPressed by scanInteraction.collectIsPressedAsState()
                val scanBusy = scanStatus == ScanStatus.Scanning
                val (buttonLabel, buttonColor) = when (scanStatus) {
                    ScanStatus.Idle -> "SCAN" to GoldPrimary
                    ScanStatus.Scanning -> "..." to GoldMuted
                    ScanStatus.NotFound -> "NOT FOUND" to CreamFaded
                    is ScanStatus.Found -> "FOUND!" to GoldLight
                    else -> "SCAN" to GoldPrimary
                }
                Box(
                    modifier = Modifier
                        .border(
                            1.dp,
                            if (scanPressed) GoldLight else GoldMuted,
                            RoundedCornerShape(2.dp)
                        )
                        .background(if (scanPressed) GoldDark.copy(alpha = 0.35f) else Color.Transparent)
                        .clickable(
                            interactionSource = scanInteraction,
                            indication = null,
                            enabled = !scanBusy,
                            onClick = onScanTap,
                        )
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Text(
                        buttonLabel,
                        style = Typography.labelLarge.copy(color = if (scanPressed) GoldLight else buttonColor)
                    )
                }
            } else {
                // Auto mode: show current watch status
                val (statusLabel, statusColor) = when (scanStatus) {
                    ScanStatus.AutoWatching -> "WATCHING" to GoldMuted
                    ScanStatus.Scanning -> "SCANNING..." to GoldPrimary
                    ScanStatus.AutoCooldown -> "NEXT..." to CreamFaded
                    is ScanStatus.Found -> "FOUND!" to GoldLight
                    else -> "WATCHING" to GoldMuted
                }
                Text(
                    text = statusLabel,
                    style = Typography.labelLarge.copy(color = statusColor),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                )
            }
        }

        // Action buttons row
        if (cards.isNotEmpty() || scanLogSize > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (cards.isNotEmpty()) {
                    val addInteraction = remember { MutableInteractionSource() }
                    val addPressed by addInteraction.collectIsPressedAsState()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .border(
                                1.dp,
                                if (addPressed) GoldLight else GoldPrimary,
                                RoundedCornerShape(2.dp),
                            )
                            .background(if (addPressed) GoldDark.copy(alpha = 0.35f) else Color.Transparent)
                            .clickable(
                                interactionSource = addInteraction,
                                indication = null,
                                onClick = onAddToCollection,
                            )
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "ADD TO COLLECTION",
                            style = Typography.labelLarge.copy(
                                color = if (addPressed) GoldLight else GoldPrimary,
                            ),
                        )
                    }
                }
                if (scanLogSize > 0) {
                    val exportInteraction = remember { MutableInteractionSource() }
                    val exportPressed by exportInteraction.collectIsPressedAsState()
                    Box(
                        modifier = Modifier
                            .then(if (cards.isEmpty()) Modifier.fillMaxWidth() else Modifier)
                            .border(
                                1.dp,
                                if (exportPressed) GoldLight else GoldMuted,
                                RoundedCornerShape(2.dp),
                            )
                            .background(if (exportPressed) GoldDark.copy(alpha = 0.35f) else Color.Transparent)
                            .clickable(
                                interactionSource = exportInteraction,
                                indication = null,
                                onClick = onExportScanLog,
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "EXPORT ($scanLogSize)",
                            style = Typography.labelLarge.copy(
                                color = if (exportPressed) GoldLight else GoldMuted,
                            ),
                        )
                    }
                }
            }
        }

        // Scanned cards list (newest first)
        val listState = rememberLazyListState()
        LaunchedEffect(lastScanCardName, cards.size) {
            if (cards.isNotEmpty()) listState.animateScrollToItem(0)
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(cards, key = { _, entry -> entry.card.name }) { _, entry ->
                CardRow(
                    card = entry.card,
                    count = entry.count,
                    isSelected = selectedCardName == entry.card.name,
                    onToggle = {
                        selectedCardName =
                            if (selectedCardName == entry.card.name) null else entry.card.name
                    },
                    onLongPress = { onCardClick(entry.card.name) },
                    onIncrement = { onIncrement(entry.card.name) },
                    onDecrement = {
                        if (entry.count <= 1) selectedCardName = null
                        onDecrement(entry.card.name)
                    },
                    marketPrice = prices[entry.card.name],
                )
                HorizontalDivider(color = GoldDark.copy(alpha = 0.25f), thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun ScanResultOverlay(
    lastScanCard: CardEntity?,
    unmatchedScan: Boolean,
    top3: List<ScanCandidate>,
    correctionResults: List<CardEntity>,
    onCorrect: (String) -> Unit,
    onCorrectionOpenChanged: (Boolean) -> Unit,
    onCorrectionQueryChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Track whether a scan result is active (matched or unmatched)
    val hasResult = lastScanCard != null || unmatchedScan
    var correctionExpanded by remember { mutableStateOf(false) }
    val overlayAlpha = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // Each new scan: pop in, auto-fade after delay
    LaunchedEffect(lastScanCard, unmatchedScan) {
        if (hasResult) {
            correctionExpanded = false
            onCorrectionOpenChanged(false)
            overlayAlpha.snapTo(1f)
            delay(3000)
            if (!correctionExpanded) {
                overlayAlpha.animateTo(0f, tween(500))
            }
        } else {
            overlayAlpha.snapTo(0f)
        }
    }

    if (overlayAlpha.value == 0f && !hasResult) return

    val overlayInteraction = remember { MutableInteractionSource() }
    val overlayPressed by overlayInteraction.collectIsPressedAsState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = overlayAlpha.value }
            .background(
                if (overlayPressed) LeatherMid.copy(alpha = 0.95f)
                else LeatherDeep.copy(alpha = 0.9f),
                RoundedCornerShape(4.dp),
            )
            .border(
                0.5.dp,
                if (overlayPressed) GoldMuted else GoldDark.copy(alpha = 0.4f),
                RoundedCornerShape(4.dp),
            )
            .clickable(
                interactionSource = overlayInteraction,
                indication = null,
            ) {
                correctionExpanded = !correctionExpanded
                onCorrectionOpenChanged(correctionExpanded)
                if (correctionExpanded) {
                    scope.launch { overlayAlpha.snapTo(1f) }
                }
            }
            .padding(10.dp),
    ) {
        if (lastScanCard != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AsyncImage(
                    model = "file:///android_asset/images/${lastScanCard.primarySlug}.webp",
                    contentDescription = lastScanCard.name,
                    modifier = Modifier
                        .size(width = 48.dp, height = 67.dp)
                        .border(0.5.dp, GoldDark, RoundedCornerShape(2.dp)),
                )
                Column {
                    Text(
                        text = lastScanCard.name.uppercase(),
                        style = Typography.labelMedium.copy(color = CreamPrimary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Tap to correct",
                        style = Typography.labelSmall.copy(color = GoldMuted),
                    )
                }
            }
        } else {
            Text(
                text = "NO MATCH \u2014 tap to correct",
                style = Typography.labelMedium.copy(color = BurgundyAccent),
            )
        }

        AnimatedVisibility(visible = correctionExpanded) {
            CorrectionPanel(
                top3 = top3,
                correctionResults = correctionResults,
                currentCardName = lastScanCard?.name,
                onCorrect = onCorrect,
                onQueryChanged = onCorrectionQueryChanged,
            )
        }
    }
}

@Composable
private fun PermissionDeniedContent(
    onRequestAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "CAMERA ACCESS REQUIRED",
            style = Typography.labelLarge.copy(color = GoldPrimary),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Grant camera permission to scan cards",
            style = Typography.bodyMedium.copy(
                color = CreamFaded,
                textAlign = TextAlign.Center,
            ),
        )
        Spacer(Modifier.height(24.dp))
        val permInteraction = remember { MutableInteractionSource() }
        val permPressed by permInteraction.collectIsPressedAsState()
        Box(
            modifier = Modifier
                .border(
                    1.dp,
                    if (permPressed) GoldLight else GoldMuted,
                    RoundedCornerShape(2.dp),
                )
                .background(if (permPressed) GoldDark.copy(alpha = 0.35f) else Color.Transparent)
                .clickable(
                    interactionSource = permInteraction,
                    indication = null,
                    onClick = onRequestAgain,
                )
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(
                text = "GRANT PERMISSION",
                style = Typography.labelMedium.copy(
                    color = if (permPressed) GoldLight else GoldPrimary,
                ),
            )
        }
    }
}

@Composable
private fun CameraPreviewSurface(onFrame: (ImageProxy) -> Unit, modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                val provider = future.get()
                val preview = CameraPreview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor, onFrame) }
                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis,
                    )
                } catch (e: Exception) {
                    Log.e("ScanScreen", "Camera bind failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = modifier,
    )
}

@Composable
private fun CorrectionPanel(
    top3: List<ScanCandidate>,
    correctionResults: List<CardEntity>,
    currentCardName: String?,
    onCorrect: (String) -> Unit,
    onQueryChanged: (String) -> Unit,
) {
    var searchText by remember { mutableStateOf(TextFieldValue("")) }
    LaunchedEffect(searchText.text) {
        if (searchText.text.length < 2) {
            onQueryChanged("")
        } else {
            delay(200)
            onQueryChanged(searchText.text)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LeatherDeep.copy(alpha = 0.8f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Candidate chips
        val candidates = top3.filter { it.name != currentCardName }
        if (candidates.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (candidate in candidates) {
                    val chipInteraction = remember { MutableInteractionSource() }
                    val chipPressed by chipInteraction.collectIsPressedAsState()
                    Box(
                        modifier = Modifier
                            .border(
                                1.dp,
                                if (chipPressed) GoldLight else GoldMuted,
                                RoundedCornerShape(2.dp),
                            )
                            .background(if (chipPressed) GoldDark.copy(alpha = 0.35f) else LeatherMid)
                            .clickable(
                                interactionSource = chipInteraction,
                                indication = null,
                            ) { onCorrect(candidate.name) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = "${candidate.name} (${candidate.score})",
                            style = Typography.labelSmall.copy(
                                color = if (chipPressed) CreamPrimary else CreamMuted,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // Search field
        BasicTextField(
            value = searchText,
            onValueChange = { searchText = it },
            textStyle = Typography.bodyMedium.copy(color = CreamPrimary),
            singleLine = true,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GoldDark, RoundedCornerShape(2.dp))
                        .background(LeatherDark, RoundedCornerShape(2.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    if (searchText.text.isEmpty()) {
                        Text(
                            text = "Search cards\u2026",
                            style = Typography.bodyMedium.copy(color = GoldDark),
                        )
                    }
                    innerTextField()
                }
            },
        )

        // Search results dropdown
        for (card in correctionResults) {
            val resultInteraction = remember { MutableInteractionSource() }
            val resultPressed by resultInteraction.collectIsPressedAsState()
            Text(
                text = card.name,
                style = Typography.bodyMedium.copy(
                    color = if (resultPressed) CreamPrimary else CreamMuted,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (resultPressed) GoldDark.copy(alpha = 0.2f) else Color.Transparent)
                    .clickable(
                        interactionSource = resultInteraction,
                        indication = null,
                    ) { onCorrect(card.name) }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ScanScreenPreview() {
    CardAppTheme {
        ScanScreenContent(
            scannedCards = previewScannedCards(),
            scanStatus = ScanStatus.Idle,
            scanMode = ScanMode.Manual,
            hasCameraPermission = true,
            onBack = {},
            onScanTap = {},
            onToggleScanMode = {},
            onIncrement = {},
            onDecrement = {},
            onAddToCollection = {},
            lastScanCard = previewScannedCards().first().card,
            latestTop3 = listOf(
                ScanCandidate("Abundance", 135),
                ScanCandidate("Ruby Core Werewolf", 42),
                ScanCandidate("Volcanic Dragon", 31),
            ),
            onRequestPermission = {},
            cameraContent = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0D1117)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "[ CAMERA ]",
                        style = Typography.labelLarge.copy(color = CreamFaded.copy(alpha = 0.3f)),
                    )
                }
            },
        )
    }
}

private fun previewScannedCards() = listOf(
    ScannedEntry(
        card = CardEntity(
            name = "Abundance", primarySlug = "abundance-alpha", elements = "Earth",
            subTypes = "", cardType = "Spell", rarity = "Elite", cost = 3,
            attack = 0, defence = 0, life = null, rulesText = "When this enters play, draw a card.",
            airThreshold = 2, earthThreshold = 1, fireThreshold = 0, waterThreshold = 1,
        ),
        count = 2,
    ),
    ScannedEntry(
        card = CardEntity(
            name = "Ruby Core Werewolf",
            primarySlug = "ruby-core-werewolf-alpha",
            elements = "Fire",
            subTypes = "Beast",
            cardType = "Minion",
            rarity = "Ordinary",
            cost = 4,
            attack = 4,
            defence = 3,
            life = null,
            rulesText = "",
            airThreshold = 0,
            earthThreshold = 0,
            fireThreshold = 1,
            waterThreshold = 0,
        ),
        count = 1,
    ),
    ScannedEntry(
        card = CardEntity(
            name = "Volcanic Dragon", primarySlug = "volcanic-dragon-alpha", elements = "Fire",
            subTypes = "Dragon", cardType = "Minion", rarity = "Unique", cost = 8,
            attack = 7, defence = 7, life = null, rulesText = "Airborne",
            airThreshold = 0, earthThreshold = 0, fireThreshold = 3, waterThreshold = 0,
        ),
        count = 3,
    ),
)
