package com.github.username.cardapp.ui.scan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val activity = LocalActivity.current
    val scannedCards by vm.scannedCards.collectAsStateWithLifecycle()
    val scanStatus by vm.scanStatus.collectAsStateWithLifecycle()
    val scanMode by vm.scanMode.collectAsStateWithLifecycle()
    val prices by vm.prices.collectAsStateWithLifecycle()
    val lastOutcome by vm.lastOutcome.collectAsStateWithLifecycle()
    val correctionOpen by vm.correctionOpen.collectAsStateWithLifecycle()
    val correctionResults by vm.correctionResults.collectAsStateWithLifecycle()
    val scanLogSize by vm.scanLogSize.collectAsStateWithLifecycle()
    val catalogueReady by vm.catalogueReady.collectAsStateWithLifecycle()

    var hasCameraPermission by remember { mutableStateOf(isCameraPermissionGranted(context)) }
    // Saveable so a rotation doesn't pop the system dialog up again.
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
        // Right after a denial, no rationale means the system will refuse to ask again.
        permanentlyDenied = !granted &&
            activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == false
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission && !permissionRequested) {
            permissionRequested = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // The user may have granted it in Settings meanwhile.
    LifecycleResumeEffect(Unit) {
        hasCameraPermission = isCameraPermissionGranted(context)
        if (hasCameraPermission) permanentlyDenied = false
        onPauseOrDispose {}
    }

    var cameraError by remember { mutableStateOf<String?>(null) }

    ScanScreenContent(
        scannedCards = scannedCards,
        scanStatus = scanStatus,
        scanMode = scanMode,
        prices = prices,
        scanLogSize = scanLogSize,
        hasCameraPermission = hasCameraPermission,
        permanentlyDenied = permanentlyDenied,
        cameraError = cameraError,
        catalogueReady = catalogueReady,
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
        lastOutcome = lastOutcome,
        correctionOpen = correctionOpen,
        correctionResults = correctionResults,
        onCorrectLastScan = vm::correctLastScan,
        onCorrectionOpenChanged = vm::setCorrectionOpen,
        onCorrectionQueryChanged = vm::updateCorrectionQuery,
        onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
        onOpenSettings = {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                ),
            )
        },
        cameraContent = {
            CameraPreviewSurface(
                onFrame = vm::analyzeFrame,
                onCameraError = { cameraError = it },
                modifier = Modifier.fillMaxSize()
            )
        },
    )
}

private fun isCameraPermissionGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

@Composable
private fun ScanScreenContent(
    scannedCards: List<ScannedEntry>,
    scanStatus: ScanStatus,
    scanMode: ScanMode,
    prices: Map<String, Double> = emptyMap(),
    scanLogSize: Int = 0,
    hasCameraPermission: Boolean,
    permanentlyDenied: Boolean = false,
    cameraError: String? = null,
    catalogueReady: Boolean = true,
    onBack: () -> Unit,
    onCardClick: (String) -> Unit = {},
    onScanTap: () -> Unit,
    onToggleScanMode: () -> Unit,
    onIncrement: (String) -> Unit,
    onDecrement: (String) -> Unit,
    onAddToCollection: () -> Unit,
    onExportScanLog: () -> Unit = {},
    lastOutcome: ScanOutcome? = null,
    correctionOpen: Boolean = false,
    correctionResults: List<CardEntity> = emptyList(),
    onCorrectLastScan: (Long, String) -> Unit = { _, _ -> },
    onCorrectionOpenChanged: (Boolean) -> Unit = {},
    onCorrectionQueryChanged: (String) -> Unit = {},
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit = {},
    cameraContent: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LeatherDeep)
            // Auto mode scans hands-free, so the screen must not time out mid-session.
            .then(if (scanMode == ScanMode.Auto) Modifier.keepScreenOn() else Modifier),
    ) {
        // Layers 1 & 2: Camera preview + reticle, or why there is no camera
        if (hasCameraPermission) {
            cameraContent()
            if (cameraError == null) {
                ReticleOverlay(modifier = Modifier.fillMaxSize())
            } else {
                CameraUnavailableMessage(
                    message = cameraError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(1f - SCANNED_PANEL_HEIGHT_FRACTION),
                )
            }
        } else {
            PermissionDeniedContent(
                permanentlyDenied = permanentlyDenied,
                onRequestAgain = onRequestPermission,
                onOpenSettings = onOpenSettings,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Layer 3: Scan result overlay
        ScanResultOverlay(
            outcome = lastOutcome,
            correctionExpanded = correctionOpen,
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
            cameraReady = hasCameraPermission && cameraError == null,
            catalogueReady = catalogueReady,
            lastOutcomeId = lastOutcome?.id,
            lastOutcomeCardName = lastOutcome?.card?.name,
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
                .fillMaxHeight(SCANNED_PANEL_HEIGHT_FRACTION),
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

@Composable
private fun CameraUnavailableMessage(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(LeatherDeep.copy(alpha = 0.85f))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "CAMERA UNAVAILABLE",
            style = Typography.labelLarge.copy(color = GoldPrimary),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = message,
            style = Typography.bodyMedium.copy(color = CreamFaded, textAlign = TextAlign.Center),
        )
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
            lastOutcome = ScanOutcome(
                id = 0,
                card = previewScannedCards().first().card,
                top3 = listOf(
                    ScanCandidate("Abundance", 135),
                    ScanCandidate("Ruby Core Werewolf", 42),
                    ScanCandidate("Volcanic Dragon", 31),
                ),
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
