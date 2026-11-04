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
    private val main = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var webView: WebView? = null
    private var buffer: Bitmap? = null
    private var bufferCanvas: Canvas? = null
    private var viewWidth: Int = 960
    private var viewHeight: Int = 540

    @Volatile
    var activeApp: StageWebApp = StageWebApp.YOUTUBE
        private set

    @Volatile
    var active: Boolean = false
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
                main.postDelayed(this, 140L)
            }
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
        tap(nx * viewWidth, ny * viewHeight)
    }

    fun setActive(enabled: Boolean, app: StageWebApp? = null) {
        val changedApp = app != null && app != activeApp
        if (app != null) activeApp = app
        if (active == enabled && !changedApp) return
        active = enabled
        main.post {
            main.removeCallbacks(frameLoop)
            if (enabled) {
                ensureWebView()
                if (changedApp || webView?.url.isNullOrBlank()) {
                    loadUrlInternal(activeApp.homeUrl)
                }
                main.post(frameLoop)
            }
        }
    }

    fun updateViewport(width: Int, height: Int) {
        val w = width.coerceIn(320, 1920)
        val h = height.coerceIn(200, 1080)
        if (w == viewWidth && h == viewHeight) return
        viewWidth = w
        viewHeight = h
        if (active) {
            main.post {
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
        main.post {
            ensureWebView()
            loadUrlInternal(app.homeUrl)
            if (active) {
                main.removeCallbacks(frameLoop)
                main.post(frameLoop)
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
        main.post {
            ensureWebView()
            loadUrlInternal(url)
        }
    }

    fun tap(localX: Float, localY: Float) {
        main.post {
            val wv = webView ?: return@post
            val x = localX.coerceIn(0f, viewWidth.toFloat())
            val y = localY.coerceIn(0f, viewHeight.toFloat())
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
            main.postDelayed({ captureFrame() }, 80L)
        }
    }

    fun scrollBy(dyPixels: Int) {
        main.post {
            val wv = webView ?: return@post
            val maxScroll = (wv.contentHeight * wv.scale - viewHeight).toInt().coerceAtLeast(0)
            val nextY = (wv.scrollY + dyPixels).coerceIn(0, maxScroll.coerceAtLeast(viewHeight * 4))
            wv.scrollTo(wv.scrollX, nextY)
            captureFrame()
        }
    }

    fun goBack() {
        main.post {
            val wv = webView ?: return@post
            if (wv.canGoBack()) {
                wv.goBack()
                main.postDelayed({ captureFrame() }, 120L)
            }
        }
    }

    fun reload() {
        main.post {
            webView?.reload()
            main.postDelayed({ captureFrame() }, 120L)
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
        val wSpec = View.MeasureSpec.makeMeasureSpec(viewWidth, View.MeasureSpec.EXACTLY)
        val hSpec = View.MeasureSpec.makeMeasureSpec(viewHeight, View.MeasureSpec.EXACTLY)
        wv.measure(wSpec, hSpec)
        wv.layout(0, 0, viewWidth, viewHeight)
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
            var bmp = buffer
            if (bmp == null || bmp.width != viewWidth || bmp.height != viewHeight) {
                bmp = Bitmap.createBitmap(viewWidth, viewHeight, Bitmap.Config.ARGB_8888)
                buffer = bmp
                bufferCanvas = Canvas(bmp)
            }
            val c = bufferCanvas ?: return
            c.save()
            c.drawColor(0xFF10131C.toInt())
            c.translate(-wv.scrollX.toFloat(), -wv.scrollY.toFloat())
            wv.draw(c)
            c.restore()
            bitmap = bmp
            LiveDataStore.notifyDisplayChanged()
        } catch (error: Throwable) {
            errorMessage = error.message
        }
    }
}
