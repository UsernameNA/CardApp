package com.github.username.cardapp.ui.scan

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import java.util.concurrent.atomic.AtomicBoolean

/**
 * An [ImageProxy] whose [close] only takes effect once, so the analyzer can close a frame
 * as a safety net without knowing whether its consumer already did. CameraX's own proxies
 * notify their close listeners on every call, which would re-trigger its frame delivery.
 */
@OptIn(ExperimentalGetImage::class)
internal class SingleCloseImageProxy(private val delegate: ImageProxy) : ImageProxy by delegate {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (!closed.getAndSet(true)) delegate.close()
    }
}
