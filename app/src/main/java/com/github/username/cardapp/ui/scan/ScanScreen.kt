package com.github.username.cardapp.ui.scan

import android.Manifest
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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.activity.ComponentActivity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.concurrent.Executors
import com.github.username.cardapp.data.local.CardEntity
import com.github.username.cardapp.ui.common.CardRow
import com.github.username.cardapp.ui.theme.CardAppTheme
import com.github.username.cardapp.ui.theme.CreamFaded
import com.github.username.cardapp.ui.theme.GoldDark
import com.github.username.cardapp.ui.theme.GoldLight
import com.github.username.cardapp.ui.theme.GoldMuted
import com.github.username.cardapp.ui.theme.GoldPrimary
import com.github.username.cardapp.ui.theme.LeatherDeep
import com.github.username.cardapp.ui.theme.LeatherMid
import com.github.username.cardapp.ui.theme.Typography

@Composable
fun ScanScreen(onBack: () -> Unit, vm: ScanViewModel = viewModel()) {
    val context = LocalContext.current
    val scannedCards by vm.scannedCards.collectAsState()
    val scanStatus by vm.scanStatus.collectAsState()
    val scanMode by vm.scanMode.collectAsState()
    val debugInfo by vm.debugInfo.collectAsState()

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
        debugInfo = debugInfo,
        hasCameraPermission = hasCameraPermission,
        onBack = onBack,
        onScanTap = vm::requestScan,
        onToggleScanMode = vm::toggleScanMode,
        onIncrement = vm::incrementCard,
        onDecrement = vm::decrementCard,
        onAddToCollection = vm::addScannedToCollection,
        onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
        cameraContent = { CameraPreviewSurface(onFrame = vm::analyzeFrame, modifier = Modifier.fillMaxSize()) },
    )
}

@Composable
private fun ScanScreenContent(
    scannedCards: List<ScannedEntry>,
    scanStatus: ScanStatus,
    scanMode: ScanMode,
    debugInfo: ScanDebugInfo?,
    hasCameraPermission: Boolean,
    onBack: () -> Unit,
    onScanTap: () -> Unit,
    onToggleScanMode: () -> Unit,
    onIncrement: (String) -> Unit,
    onDecrement: (String) -> Unit,
    onAddToCollection: () -> Unit,
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

        // Layer 3: Debug overlay (shown after any scan attempt)
        if (debugInfo != null) {
            ScanDebugOverlay(
                debugInfo = debugInfo,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 56.dp, end = 16.dp, top = 8.dp),
            )
        }

        // Layer 4: Bottom panel
        ScannedCardsPanel(
            cards = scannedCards,
            scanStatus = scanStatus,
            scanMode = scanMode,
            onScanTap = onScanTap,
            onToggleScanMode = onToggleScanMode,
            onIncrement = onIncrement,
            onDecrement = onDecrement,
            onAddToCollection = onAddToCollection,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.35f),
        )

        // Layer 5: Back button
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = GoldPrimary,
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
        val cardW = size.width * 0.58f
        val cardH = cardW / 0.72f
        val cx = size.width / 2f
        val cy = availH / 2f

        val left = cx - cardW / 2f
        val top = cy - cardH / 2f
        val right = cx + cardW / 2f
        val bottom = cy + cardH / 2f
        val bracketLen = cardW * 0.18f
        val strokePx = 2.dp.toPx()
        val alpha = 0.55f + pulse * 0.45f
        val goldColor = GoldPrimary.copy(alpha = alpha)
        val goldLight = GoldLight.copy(alpha = alpha * 0.85f)

        // Top-left
        drawLine(goldColor, Offset(left, top + bracketLen), Offset(left, top), strokePx)
        drawLine(goldColor, Offset(left, top), Offset(left + bracketLen, top), strokePx)
        // Top-right
        drawLine(goldColor, Offset(right - bracketLen, top), Offset(right, top), strokePx)
        drawLine(goldColor, Offset(right, top), Offset(right, top + bracketLen), strokePx)
        // Bottom-left
        drawLine(goldColor, Offset(left, bottom - bracketLen), Offset(left, bottom), strokePx)
        drawLine(goldColor, Offset(left, bottom), Offset(left + bracketLen, bottom), strokePx)
        // Bottom-right
        drawLine(goldColor, Offset(right - bracketLen, bottom), Offset(right, bottom), strokePx)
        drawLine(goldColor, Offset(right, bottom), Offset(right, bottom - bracketLen), strokePx)

        // Corner diamonds
        val dSize = 4.dp.toPx()
        listOf(
            Offset(left, top), Offset(right, top),
            Offset(left, bottom), Offset(right, bottom),
        ).forEach { corner ->
            val diamondPath = Path().apply {
                moveTo(corner.x, corner.y - dSize)
                lineTo(corner.x + dSize * 0.65f, corner.y)
                lineTo(corner.x, corner.y + dSize)
                lineTo(corner.x - dSize * 0.65f, corner.y)
                close()
            }
            drawPath(diamondPath, goldLight, style = Stroke(strokePx * 0.8f))
        }

        // Center aim dot
        drawCircle(GoldLight.copy(alpha = alpha * 0.6f), 2.5.dp.toPx(), Offset(cx, cy))
    }
}

@Composable
private fun ScannedCardsPanel(
    cards: List<ScannedEntry>,
    scanStatus: ScanStatus,
    scanMode: ScanMode,
    onScanTap: () -> Unit,
    onToggleScanMode: () -> Unit,
    onIncrement: (String) -> Unit,
    onDecrement: (String) -> Unit,
    onAddToCollection: () -> Unit,
    modifier: Modifier = Modifier,
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
            Box(
                modifier = Modifier
                    .border(1.dp, if (autoActive) GoldPrimary else GoldDark, RoundedCornerShape(2.dp))
                    .background(if (autoActive) GoldDark.copy(alpha = 0.35f) else Color.Transparent)
                    .clickable(onClick = onToggleScanMode)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text("AUTO", style = Typography.labelLarge.copy(color = if (autoActive) GoldPrimary else GoldDark))
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
                        .border(1.dp, if (scanPressed) GoldLight else GoldMuted, RoundedCornerShape(2.dp))
                        .background(if (scanPressed) GoldDark.copy(alpha = 0.35f) else Color.Transparent)
                        .clickable(
                            interactionSource = scanInteraction,
                            indication = null,
                            enabled = !scanBusy,
                            onClick = onScanTap,
                        )
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Text(buttonLabel, style = Typography.labelLarge.copy(color = if (scanPressed) GoldLight else buttonColor))
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

        // Add to collection button (only visible when there are scanned cards)
        if (cards.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .border(1.dp, GoldPrimary, RoundedCornerShape(2.dp))
                    .clickable(onClick = onAddToCollection)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("ADD TO COLLECTION", style = Typography.labelLarge.copy(color = GoldPrimary))
            }
        }

        // Scanned cards list (newest first)
        val listState = rememberLazyListState()
        LaunchedEffect(cards.size) {
            if (cards.isNotEmpty()) listState.animateScrollToItem(0)
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(cards.reversed(), key = { _, entry -> entry.card.name }) { _, entry ->
                CardRow(
                    card = entry.card,
                    count = entry.count,
                    isSelected = selectedCardName == entry.card.name,
                    onToggle = {
                        selectedCardName = if (selectedCardName == entry.card.name) null else entry.card.name
                    },
                    onLongPress = { selectedCardName = entry.card.name },
                    onIncrement = { onIncrement(entry.card.name) },
                    onDecrement = {
                        if (entry.count <= 1) selectedCardName = null
                        onDecrement(entry.card.name)
                    },
                )
                HorizontalDivider(color = GoldDark.copy(alpha = 0.25f), thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun ScanDebugOverlay(debugInfo: ScanDebugInfo, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(LeatherDeep.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
            .border(0.5.dp, GoldDark.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("OCR TEXT", style = Typography.labelSmall.copy(color = GoldMuted))
        Text(
            text = debugInfo.rawText.take(200).replace('\n', ' '),
            style = Typography.bodySmall.copy(color = CreamFaded),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "COSTS DETECTED  ${debugInfo.costCandidates.sorted().joinToString(" ").ifEmpty { "none" }}",
            style = Typography.labelSmall.copy(color = GoldMuted),
        )
        val scoreColor = if (debugInfo.bestScore >= ScanViewModel.SCORE_THRESHOLD) GoldLight else CreamFaded
        Text(
            text = "BEST  ${debugInfo.bestCardName ?: "—"}  (${debugInfo.bestScore})",
            style = Typography.labelSmall.copy(color = scoreColor),
        )
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
        Box(
            modifier = Modifier
                .border(1.dp, GoldMuted, RoundedCornerShape(2.dp))
                .clickable(onClick = onRequestAgain)
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(
                text = "GRANT PERMISSION",
                style = Typography.labelMedium.copy(color = GoldPrimary),
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

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ScanScreenPreview() {
    CardAppTheme {
        ScanScreenContent(
            scannedCards = previewScannedCards(),
            scanStatus = ScanStatus.Idle,
            scanMode = ScanMode.Manual,
            debugInfo = ScanDebugInfo(
                rawText = "Abundance\nWhen this enters play, draw a card.",
                costCandidates = setOf(3),
                bestCardName = "Abundance",
                bestScore = 135,
            ),
            hasCameraPermission = true,
            onBack = {},
            onScanTap = {},
            onToggleScanMode = {},
            onIncrement = {},
            onDecrement = {},
            onAddToCollection = {},
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
            airThreshold = 0, earthThreshold = 2, fireThreshold = 0, waterThreshold = 0,
        ),
        count = 2,
    ),
    ScannedEntry(
        card = CardEntity(
            name = "Ruby Core Werewolf", primarySlug = "ruby-core-werewolf-alpha", elements = "Fire",
            subTypes = "Beast", cardType = "Minion", rarity = "Ordinary", cost = 4,
            attack = 4, defence = 3, life = null, rulesText = "",
            airThreshold = 0, earthThreshold = 0, fireThreshold = 1, waterThreshold = 0,
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
