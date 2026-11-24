package dev.opencarl.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import dev.opencarl.BuildConfig
import org.json.JSONObject

/**
 * xterm.js running in a WebView, wired to a PTY. The WebView gives us a
 * battle-tested VT parser (so vim/htop/opencode render correctly) without
 * shipping a native terminal emulator.
 */
class TerminalWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    var onInput: ((String) -> Unit)? = null
    var onBinary: ((ByteArray) -> Unit)? = null
    var onResize: ((cols: Int, rows: Int) -> Unit)? = null
    var onReady: ((cols: Int, rows: Int) -> Unit)? = null
    var onExternalUrl: ((String) -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val pending = StringBuilder()
    private var flushScheduled = false
    private var ready = false

    var fontSize: Int = 14
        set(value) {
            field = value
            evaluate("window.lbFontSize && window.lbFontSize($value)")
        }

    private val assetLoader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .build()

    private val webView = WebView(context)

    init {
        setBackgroundColor(BACKGROUND)
        webView.setBackgroundColor(BACKGROUND)
        webView.overScrollMode = View.OVER_SCROLL_NEVER
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = true
            javaScriptCanOpenWindowsAutomatically = false
            setGeolocationEnabled(false)
            textZoom = 100
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = "$userAgentString openCarl/${BuildConfig.VERSION_NAME}"
        }
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)

        webView.addJavascriptInterface(Bridge(), "openCarl")
        webView.webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean {
                val url = request.url
                if (url.host == WebViewAssetLoader.DEFAULT_DOMAIN) return false
                onExternalUrl?.invoke(url.toString())
                return true
            }
        }

        addView(webView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        load()
    }

    private fun load() {
        val url = "https://${WebViewAssetLoader.DEFAULT_DOMAIN}/assets/terminal/index.html?fontSize=$fontSize"
        webView.loadUrl(url)
    }

    fun reload() {
        ready = false
        pending.setLength(0)
        load()
    }

    /** Queues terminal output; chunks are coalesced to one JS call per frame. */
    fun write(text: String) {
        if (text.isEmpty()) return
        mainHandler.post {
            pending.append(text)
            if (!flushScheduled) {
                flushScheduled = true
                mainHandler.postDelayed({ flush() }, 16)
            }
        }
    }

    private fun flush() {
        flushScheduled = false
        if (pending.isEmpty()) return
        val chunk = pending.toString()
        pending.setLength(0)
        evaluate("window.lbWrite && window.lbWrite(${JSONObject.quote(chunk)})")
    }

    fun clear() = evaluate("window.lbClear && window.lbClear()")

    fun reset() = evaluate("window.lbReset && window.lbReset()")

    fun focusTerminal() {
        evaluate("window.lbFocus && window.lbFocus()")
        webView.requestFocus()
    }

    fun fit() = evaluate("window.lbFit && window.lbFit()")

    fun copySelection(onCopied: (String) -> Unit) {
        webView.evaluateJavascript("window.lbGetSelection ? window.lbGetSelection() : ''") { raw ->
            val text = runCatching {
                JSONObject("{\"v\":${raw ?: "null"}}").optString("v")
            }.getOrDefault("")
            if (text.isNotEmpty()) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("terminal", text))
                onCopied(text)
            }
        }
    }

    fun paste(text: String) {
        if (text.isEmpty()) return
        onInput?.invoke(text.replace("\n", "\r"))
    }

    /** Applies the 32-color palette and font choice to the live xterm instance. */
    fun applyTheme(
        fontFamily: String,
        fontSize: Int,
        background: String,
        foreground: String,
        cursor: String,
        selection: String,
    ) {
        this.fontSize = fontSize
        val payload = JSONObject().apply {
            put("fontFamily", fontFamily)
            put("fontSize", fontSize)
            put("background", background)
            put("foreground", foreground)
            put("cursor", cursor)
            put("cursorAccent", background)
            put("selection", selection)
        }
        runCatching {
            setBackgroundColor(Color.parseColor(background))
            webView.setBackgroundColor(Color.parseColor(background))
        }
        evaluate("window.lbSetTheme && window.lbSetTheme(" + payload.toString() + ")")
    }

    fun destroy() {
        mainHandler.removeCallbacksAndMessages(null)
        runCatching {
            webView.loadUrl("about:blank")
            removeView(webView)
            webView.destroy()
        }
    }

    private fun evaluate(script: String) {
        if (!ready) return
        mainHandler.post {
            if (ready) runCatching { webView.evaluateJavascript(script, null) }
        }
    }

    private inner class Bridge {
        @JavascriptInterface
        fun onData(data: String) {
            onInput?.invoke(data)
        }

        @JavascriptInterface
        fun onBinary(data: String) {
            val bytes = ByteArray(data.length) { (data[it].code and 0xFF).toByte() }
            onBinary?.invoke(bytes)
        }

        @JavascriptInterface
        fun onResize(cols: Int, rows: Int) {
            onResize?.invoke(cols, rows)
        }

        @JavascriptInterface
        fun onReady(cols: Int, rows: Int) {
            mainHandler.post {
                ready = true
                fit()
                onReady?.invoke(cols, rows)
            }
        }

        @JavascriptInterface
        fun openUrl(url: String) {
            mainHandler.post { onExternalUrl?.invoke(url) }
        }
    }

    private companion object {
        val BACKGROUND: Int = Color.parseColor("#0b0f14")
    }
}
