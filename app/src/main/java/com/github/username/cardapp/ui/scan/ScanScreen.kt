package com.github.username.cardapp.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.github.username.cardapp.ui.theme.CardAppTheme
import com.github.username.cardapp.data.local.CardEntity
import com.github.username.cardapp.ui.theme.CreamFaded
import com.github.username.cardapp.ui.theme.CreamPrimary
import com.github.username.cardapp.ui.theme.GoldDark
import com.github.username.cardapp.ui.theme.GoldLight
import com.github.username.cardapp.ui.theme.GoldMuted
import com.github.username.cardapp.ui.theme.GoldPrimary
import com.github.username.cardapp.ui.theme.LeatherDeep
import com.github.username.cardapp.ui.theme.LeatherMid
import com.github.username.cardapp.ui.theme.Typography
import com.github.username.cardapp.ui.theme.rarityColor

@Composable
fun ScanScreen(onBack: () -> Unit, vm: ScanViewModel = viewModel()) {
    val context = LocalContext.current
    val scannedCards by vm.scannedCards.collectAsState()

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

    ScanScreenContent(
        scannedCards = scannedCards,
        hasCameraPermission = hasCameraPermission,
        onBack = onBack,
        onScanTap = vm::scanRandom,
        onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
    )
}

@Composable
private fun ScanScreenContent(
    scannedCards: List<CardEntity>,
    hasCameraPermission: Boolean,
    onBack: () -> Unit,
    onScanTap: () -> Unit,
    onRequestPermission: () -> Unit,
    cameraContent: @Composable () -> Unit = { CameraPreviewSurface(modifier = Modifier.fillMaxSize()) },
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

        // Layer 3: Bottom panel
        ScannedCardsPanel(
            cards = scannedCards,
            onScanTap = onScanTap,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.35f),
        )

        // Layer 4: Back button
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

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ScanScreenPreview() {
    CardAppTheme {
        ScanScreenContent(
            scannedCards = emptyList(),
            hasCameraPermission = true,
            onBack = {},
            onScanTap = {},
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

@Composable
private fun CameraPreviewSurface(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        },
        update = { previewView ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                val provider = future.get()
                val preview = CameraPreview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                    )
                } catch (e: Exception) {
                    Log.e("ScanScreen", "Camera bind failed", e)
                }
            }, ContextCompat.getMainExecutor(context))
        },
        modifier = modifier,
    )
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
    cards: List<CardEntity>,
    onScanTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            val scanInteraction = remember { MutableInteractionSource() }
            val scanPressed by scanInteraction.collectIsPressedAsState()
            Box(
                modifier = Modifier
                    .border(1.dp, if (scanPressed) GoldLight else GoldMuted, RoundedCornerShape(2.dp))
                    .background(if (scanPressed) GoldDark.copy(alpha = 0.35f) else Color.Transparent)
                    .clickable(interactionSource = scanInteraction, indication = null, onClick = onScanTap)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Text("SCAN", style = Typography.labelLarge.copy(color = if (scanPressed) GoldLight else GoldPrimary))
            }
        }

        // Scanned cards list
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(cards.reversed(), key = { index, _ -> index }) { _, card ->
                ScannedCardRow(card)
                HorizontalDivider(color = GoldDark.copy(alpha = 0.25f), thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun ScannedCardRow(card: CardEntity) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(32.dp)
                .background(rarityColor(card.rarity).copy(alpha = 0.85f)),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = card.name.uppercase(),
                style = Typography.labelMedium.copy(color = CreamPrimary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = card.cardType,
                style = Typography.bodyMedium.copy(color = CreamFaded),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
