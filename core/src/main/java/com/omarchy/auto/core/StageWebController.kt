package com.omarchy.auto.core

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.net.URLEncoder

/**
 * Offscreen interactive WebView renderer for the car/phone stage so YouTube,
 * ChatGPT, and Grok run directly inside the dashboard stage on the car display
 * rather than only opening an Activity on the phone screen.
 */
object StageWebController {
    private val main: Handler? by lazy {
        try {
            val looper = Looper.getMainLooper()
            if (looper != null) Handler(looper) else null
        } catch (_: Throwable) {
            null
        }
    }
    private val frameLock = Any()
    private var appContext: Context? = null
    private var webView: WebView? = null
    private var bufferA: Bitmap? = null
    private var bufferB: Bitmap? = null
    private var useBufferA: Boolean = true

    @Volatile
    var viewWidth: Int = 960
        private set

    @Volatile
    var viewHeight: Int = 540
        private set

    @Volatile
    var activeApp: StageWebApp = StageWebApp.YOUTUBE
        private set

    @Volatile
    var active: Boolean = false
        private set

    @Volatile
    var paused: Boolean = false
        private set

    @Volatile
    var bitmap: Bitmap? = null
        private set

    @Volatile
    var currentTitle: String = "YOUTUBE"
        private set

    @Volatile
    var currentUrl: String = StageWebApp.YOUTUBE.homeUrl
        private set

    @Volatile
    var loadingProgress: Int = 0
        private set

    @Volatile
    var errorMessage: String? = null
        private set

    private val frameLoop = object : Runnable {
        override fun run() {
            if (!active) return
            captureFrame()
            if (active) {
                main?.postDelayed(this, 140L)
            }
        }
    }

    private fun postMain(block: () -> Unit) {
        val h = main
        if (h != null) {
            h.post(block)
        } else {
            block()
        }
    }

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    fun open(app: StageWebApp) {
        selectApp(app)
        setActive(true, app)
    }

    fun ensureActive(app: StageWebApp, width: Int, height: Int) {
        updateViewport(width, height)
        setActive(true, app)
    }

    fun tapNormalized(nx: Float, ny: Float) {
        val (w, h) = synchronized(frameLock) { viewWidth to viewHeight }
        tap(nx * w, ny * h)
    }

    fun pause() {
        setActive(false)
    }

    fun resume() {
        setActive(true)
    }

    fun close() {
        active = false
        paused = true
        postMain {
            main?.removeCallbacks(frameLoop)
            try {
                webView?.stopLoading()
                webView?.loadUrl("about:blank")
                webView?.onPause()
                webView?.pauseTimers()
                webView?.destroy()
            } catch (_: Throwable) {
            }
            webView = null
            synchronized(frameLock) {
                bitmap = null
                bufferA = null
                bufferB = null
            }
        }
    }

    fun setActive(enabled: Boolean, app: StageWebApp? = null) {
        val changedApp = app != null && app != activeApp
        if (app != null) activeApp = app
        if (active == enabled && !changedApp) return
        active = enabled
        paused = !enabled
        postMain {
            main?.removeCallbacks(frameLoop)
            if (enabled) {
                ensureWebView()
                try {
                    webView?.onResume()
                    webView?.resumeTimers()
                } catch (_: Throwable) {
                }
                if (changedApp || webView?.url.isNullOrBlank()) {
                    loadUrlInternal(activeApp.homeUrl)
                }
                main?.post(frameLoop)
            } else {
                try {
                    webView?.stopLoading()
                    webView?.evaluateJavascript(
                        "document.querySelectorAll('video,audio').forEach(e=>e.pause());",
                        null,
                    )
                    webView?.onPause()
                    webView?.pauseTimers()
                } catch (_: Throwable) {
                }
            }
        }
    }

    fun updateViewport(width: Int, height: Int) {
        val w = width.coerceIn(320, 1920)
        val h = height.coerceIn(200, 1080)
        synchronized(frameLock) {
            if (w == viewWidth && h == viewHeight) return
            viewWidth = w
            viewHeight = h
        }
        if (active) {
            postMain {
                layoutWebView()
                captureFrame()
            }
        }
    }

    fun selectApp(app: StageWebApp) {
        activeApp = app
        currentTitle = app.label
        currentUrl = app.homeUrl
        errorMessage = null
        postMain {
            ensureWebView()
            loadUrlInternal(app.homeUrl)
            if (active) {
                main?.removeCallbacks(frameLoop)
                main?.post(frameLoop)
            }
        }
    }

    fun submitQuery(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return
        val url = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else if (trimmed.contains('.') && !trimmed.contains(' ')) {
            "https://$trimmed"
        } else {
            val encoded = try {
                URLEncoder.encode(trimmed, "UTF-8")
            } catch (_: Exception) {
                trimmed
            }
            activeApp.searchTemplate.format(encoded)
        }
        postMain {
            ensureWebView()
            loadUrlInternal(url)
        }
    }

    fun tap(localX: Float, localY: Float) {
        postMain {
            val wv = webView ?: return@postMain
            val (w, h) = synchronized(frameLock) { viewWidth to viewHeight }
            val x = localX.coerceIn(0f, w.toFloat())
            val y = localY.coerceIn(0f, h.toFloat())
            val downTime = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
            val up = MotionEvent.obtain(downTime, downTime + 45L, MotionEvent.ACTION_UP, x, y, 0)
            try {
                wv.dispatchTouchEvent(down)
                wv.dispatchTouchEvent(up)
            } finally {
                down.recycle()
                up.recycle()
            }
            main?.postDelayed({ captureFrame() }, 80L)
        }
    }

    fun scrollBy(dyPixels: Int) {
        postMain {
            val wv = webView ?: return@postMain
            val h = synchronized(frameLock) { viewHeight }
            val maxScroll = (wv.contentHeight * wv.scale - h).toInt().coerceAtLeast(0)
            val nextY = (wv.scrollY + dyPixels).coerceIn(0, maxScroll.coerceAtLeast(h * 4))
            wv.scrollTo(wv.scrollX, nextY)
            captureFrame()
        }
    }

    fun goBack() {
        postMain {
            val wv = webView ?: return@postMain
            if (wv.canGoBack()) {
                wv.goBack()
                main?.postDelayed({ captureFrame() }, 120L)
            }
        }
    }

    fun reload() {
        errorMessage = null
        postMain {
            webView?.reload()
            main?.postDelayed({ captureFrame() }, 120L)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWebView() {
        if (webView != null) {
            layoutWebView()
            return
        }
        val ctx = appContext ?: return
        try {
            val wv = WebView(ctx).apply {
                setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                isVerticalScrollBarEnabled = true
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    loadsImagesAutomatically = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    userAgentString =
                        "Mozilla/5.0 (Linux; Android 15; Pixel 8) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                }
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean = false

                    override fun onPageFinished(view: WebView?, url: String?) {
                        currentUrl = url ?: activeApp.homeUrl
                        currentTitle = view?.title?.takeIf { it.isNotBlank() } ?: activeApp.label
                        loadingProgress = 100
                        captureFrame()
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?,
                    ) {
                        if (request?.isForMainFrame == true) {
                            errorMessage = error?.description?.toString()?.takeIf { it.isNotBlank() }
                                ?: "Page load failed"
                            LiveDataStore.notifyDisplayChanged()
                        }
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        loadingProgress = newProgress
                        if (newProgress % 25 == 0 || newProgress == 100) {
                            captureFrame()
                        }
                    }

                    override fun onReceivedTitle(view: WebView?, title: String?) {
                        if (!title.isNullOrBlank()) {
                            currentTitle = title
                        }
                    }
                }
            }
            webView = wv
            layoutWebView()
            loadUrlInternal(activeApp.homeUrl)
        } catch (error: Throwable) {
            errorMessage = error.message ?: "WebView unavailable"
        }
    }

    private fun layoutWebView() {
        val wv = webView ?: return
        val (w, h) = synchronized(frameLock) { viewWidth to viewHeight }
        val wSpec = View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY)
        val hSpec = View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        wv.measure(wSpec, hSpec)
        wv.layout(0, 0, w, h)
    }

    private fun loadUrlInternal(url: String) {
        val wv = webView ?: return
        currentUrl = url
        loadingProgress = 10
        errorMessage = null
        wv.loadUrl(url)
        captureFrame()
    }

    private fun captureFrame() {
        val wv = webView ?: return
        try {
            layoutWebView()
            val (w, h) = synchronized(frameLock) { viewWidth to viewHeight }
            val target = synchronized(frameLock) {
                val candidate = if (useBufferA) bufferA else bufferB
                val allocated = if (candidate == null || candidate.width != w || candidate.height != h) {
                    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                        if (useBufferA) bufferA = it else bufferB = it
                    }
                } else {
                    candidate
                }
                useBufferA = !useBufferA
                allocated
            }
            val c = Canvas(target)
            c.save()
            c.drawColor(0xFF10131C.toInt())
            c.translate(-wv.scrollX.toFloat(), -wv.scrollY.toFloat())
            wv.draw(c)
            c.restore()
            synchronized(frameLock) {
                bitmap = target
            }
            LiveDataStore.notifyDisplayChanged()
        } catch (error: Throwable) {
            errorMessage = error.message
        }
    }

    fun setErrorForTest(message: String?) {
        errorMessage = message
    }

    fun resetForTest() {
        active = false
        paused = false
        errorMessage = null
        bitmap = null
        bufferA = null
        bufferB = null
        viewWidth = 960
        viewHeight = 540
    }
}
