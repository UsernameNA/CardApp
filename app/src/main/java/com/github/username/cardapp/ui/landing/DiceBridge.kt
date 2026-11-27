package com.github.username.cardapp.ui.landing

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

/**
 * Fallback for WebViews without WebMessageListener support. Exposes the same
 * `postMessage(string)` shape the dice page calls on the injected listener object.
 */
internal class DiceBridge(private val onMessage: (String) -> Unit) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun postMessage(message: String) {
        mainHandler.post { onMessage(message) }
    }
}
