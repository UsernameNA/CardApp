package com.github.username.cardapp.ui.scan

import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors

/**
 * Camera preview with an analyzer feeding [onFrame] on a background thread.
 *
 * [onCameraError] receives a message when the camera can't be used — binding failed, or
 * another app holds it — and null once it is streaming again.
 */
@Composable
internal fun CameraPreviewSurface(
    onFrame: (ImageProxy) -> Unit,
    onCameraError: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnFrame by rememberUpdatedState(onFrame)
    val currentOnCameraError by rememberUpdatedState(onCameraError)
    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(lifecycleOwner, previewView) {
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val preview = CameraPreview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            // CameraX defaults ImageAnalysis to 640×480, which leaves a card name
            // roughly 15px tall — below what ML Kit's Latin recognizer reads
            // reliably. 720p roughly doubles glyph height. The per-frame
            // fingerprint reads the Y plane directly so it costs nothing extra;
            // only the OCR frames of a burst are converted to Bitmap.
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(1280, 720),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                        )
                    )
                    .build()
            )
            .build()
        imageAnalysis.setAnalyzer(analysisExecutor) { image ->
            // An exception escaping here would crash the app, and an unclosed frame
            // would stall the analyzer for good.
            val frame = SingleCloseImageProxy(image)
            try {
                currentOnFrame(frame)
            } catch (e: Exception) {
                Log.e(TAG, "Frame analysis failed", e)
                frame.close()
            }
        }

        val stateObserver = Observer<CameraState> { state ->
            val error = state.error
            when {
                error != null -> currentOnCameraError(describeCameraError(error.code))
                state.type == CameraState.Type.OPEN -> currentOnCameraError(null)
            }
        }
        var provider: ProcessCameraProvider? = null
        var cameraState: LiveData<CameraState>? = null
        var disposed = false

        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (disposed) return@addListener
            try {
                val cameraProvider = future.get()
                provider = cameraProvider
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis,
                )
                cameraState = camera.cameraInfo.cameraState.also {
                    it.observe(lifecycleOwner, stateObserver)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
                currentOnCameraError("Camera unavailable")
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            cameraState?.removeObserver(stateObserver)
            imageAnalysis.clearAnalyzer()
            provider?.unbind(preview, imageAnalysis)
            analysisExecutor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

private fun describeCameraError(code: Int): String = when (code) {
    CameraState.ERROR_CAMERA_IN_USE,
    CameraState.ERROR_MAX_CAMERAS_IN_USE -> "The camera is in use by another app"
    CameraState.ERROR_CAMERA_DISABLED -> "The camera is disabled on this device"
    else -> "Camera unavailable"
}

private const val TAG = "CameraPreviewSurface"
