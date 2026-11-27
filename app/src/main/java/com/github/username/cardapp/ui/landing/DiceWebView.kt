package com.github.username.cardapp.ui.landing

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.WebView
import androidx.core.net.toUri
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONException
import org.json.JSONObject

// WebViewAssetLoader intercepts this origin in-process; nothing goes to the network
private const val DICE_ORIGIN = "https://${WebViewAssetLoader.DEFAULT_DOMAIN}"
private const val DICE_PAGE_URL = "$DICE_ORIGIN/assets/dice/dice_bridge.html"
private const val BRIDGE_OBJECT = "DiceBridge"

@SuppressLint("SetJavaScriptEnabled")
internal fun createDiceWebView(
    context: Context,
    onReady: () -> Unit,
    onValue: (Int) -> Unit,
    onRenderProcessGone: () -> Unit,
): WebView = WebView(context).apply {
    settings.javaScriptEnabled = true
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    setBackgroundColor(android.graphics.Color.TRANSPARENT)
    webViewClient = DiceWebViewClient(
        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build(),
        pageUrl = DICE_PAGE_URL.toUri(),
        onRenderProcessGone = onRenderProcessGone,
    )

    val onMessage = { message: String -> dispatchDiceMessage(message, onReady, onValue) }
    if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
        WebViewCompat.addWebMessageListener(this, BRIDGE_OBJECT, setOf(DICE_ORIGIN)) { _, message, _, isMainFrame, _ ->
            val data = message.data
            if (isMainFrame && data != null) onMessage(data)
        }
    } else {
        addJavascriptInterface(DiceBridge(onMessage), BRIDGE_OBJECT)
    }

    // Pre-layout to screen size so the page sees real dimensions even if it
    // finishes loading before the view is attached
    val dm = context.resources.displayMetrics
    layout(0, 0, dm.widthPixels, dm.heightPixels)
    loadUrl(DICE_PAGE_URL)
}

internal fun destroyDiceWebView(webView: WebView) {
    // WebView.destroy() must only be called once the view is out of the hierarchy
    (webView.parent as? ViewGroup)?.removeView(webView)
    webView.destroy()
}

private fun dispatchDiceMessage(message: String, onReady: () -> Unit, onValue: (Int) -> Unit) {
    val json = try {
        JSONObject(message)
    } catch (_: JSONException) {
        return
    }
    when (json.optString("type")) {
        "ready" -> onReady()
        "result" -> onValue(json.optInt("value", 0))
    }
}
