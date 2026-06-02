package com.github.username.cardapp.ui.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.github.username.cardapp.data.local.CardEntity
import com.github.username.cardapp.ui.theme.CardAppTheme
import com.github.username.cardapp.ui.theme.CreamFaded
import com.github.username.cardapp.ui.theme.GoldLight
import com.github.username.cardapp.ui.theme.GoldPrimary
import com.github.username.cardapp.ui.theme.LeatherDeep
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
                text = "←",
                style = Typography.titleLarge.copy(
                    color = if (backPressed) GoldLight else GoldPrimary,
                ),
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
