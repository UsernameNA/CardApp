package com.github.username.cardapp.ui.landing

import android.net.Uri
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream

/**
 * Serves the dice page from bundled assets and nothing else: any request the
 * asset loader doesn't handle gets an empty 404, and the page can't navigate away.
 */
internal class DiceWebViewClient(
    private val assetLoader: WebViewAssetLoader,
    private val pageUrl: Uri,
    private val onRenderProcessGone: () -> Unit,
) : WebViewClient() {

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
        assetLoader.shouldInterceptRequest(request.url)
            ?: WebResourceResponse(
                "text/plain",
                "utf-8",
                404,
                "Not Found",
                emptyMap(),
                ByteArrayInputStream(ByteArray(0)),
            )

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
        request.url != pageUrl

    // A dead renderer leaves the WebView unusable; returning true (after
    // tearing it down) keeps the app alive instead of crashing with it
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        destroyDiceWebView(view)
        onRenderProcessGone()
        return true
    }
}
