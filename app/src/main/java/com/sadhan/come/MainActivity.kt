package com.sadhan.come

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * Single-screen WebView wrapper.
 *
 * - Splash dikhata hai jab tak page load na ho
 * - Sirf BuildConfig.HOST_NAME wale links WebView me khulte hain, baaki Custom Tab / external app me
 * - Site ka "download app" banner hide karne wali JS inject karta hai
 * - Network error par backoff ke saath retry, phir browser fallback
 */
class MainActivity : ComponentActivity() {

    private val tag = "ComeWebView"
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var webViewHost: FrameLayout
    private lateinit var loadingContainer: View
    private lateinit var splashLogo: View
    private lateinit var splashLoader: View

    private var webView: WebView? = null
    private var pageRevealed = false
    private var awaitingRetry = false
    private var retryAttempt = 0
    private var bannerScript: String? = null
    private val splashAnimators = mutableListOf<Animator>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        webViewHost = findViewById(R.id.webview_host)
        loadingContainer = findViewById(R.id.loading_container)
        splashLogo = findViewById(R.id.splash_logo)
        splashLoader = findViewById(R.id.splash_loader)

        applySystemBarInsets()
        startSplashMotion()
        bannerScript = loadBannerScript()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val wv = webView
                if (wv != null && wv.canGoBack()) {
                    wv.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        createWebView(savedInstanceState)
    }

    // ---------------------------------------------------------------- UI

    private fun applySystemBarInsets() {
        val root = findViewById<View>(R.id.root_container)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun startSplashMotion() {
        fun pulse(target: View, prop: android.util.Property<View, Float>, from: Float, to: Float, ms: Long) =
            ObjectAnimator.ofFloat(target, prop, from, to).apply {
                duration = ms
                repeatCount = android.animation.ValueAnimator.INFINITE
                repeatMode = android.animation.ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
                start()
                splashAnimators += this
            }
        pulse(splashLogo, View.SCALE_X, 1f, 1.07f, 900)
        pulse(splashLogo, View.SCALE_Y, 1f, 1.07f, 900)
        splashLoader.scaleX = 0.18f
        pulse(splashLoader, View.SCALE_X, 0.18f, 1f, 1400)
    }

    private fun stopSplashMotion() {
        splashAnimators.forEach { it.cancel() }
        splashAnimators.clear()
    }

    private fun revealPage() {
        if (pageRevealed) return
        pageRevealed = true
        val wv = webView ?: return
        wv.animate().alpha(1f).setDuration(280).start()
        loadingContainer.animate().alpha(0f).setDuration(420)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    loadingContainer.visibility = View.GONE
                    stopSplashMotion()
                }
            }).start()
    }

    // ---------------------------------------------------------------- WebView

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(savedInstanceState: Bundle?) {
        val wv = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(getColor(R.color.background_dark))
            alpha = 0f
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                // "; wv" aur "Version/4.0 " hata do taaki UA normal Chrome jaisa dikhe.
                userAgentString = userAgentString.replace("; wv", "").replace("Version/4.0 ", "")
            }
            webChromeClient = WebChromeClient()
            webViewClient = ComeWebViewClient()
        }
        webView = wv
        webViewHost.addView(wv)

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(wv, true)
        }

        installDocumentStartScript(wv)

        if (savedInstanceState != null && wv.restoreState(savedInstanceState) != null) {
            // state restore ho gaya
        } else {
            wv.loadUrl(BuildConfig.START_URL)
        }

        // Agar page kisi wajah se load-finished event nahi deta to bhi splash hata do.
        handler.postDelayed({ revealPage() }, 8000)
    }

    private fun loadBannerScript(): String? = try {
        assets.open("hide-download-banner.js").bufferedReader().use { it.readText() }
            .replace("__TARGET_HOST__", BuildConfig.HOST_NAME.lowercase())
    } catch (e: Exception) {
        Log.w(tag, "Unable to load download banner hide script", e)
        null
    }

    private fun installDocumentStartScript(wv: WebView) {
        val script = bannerScript ?: return
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(
                wv, script, setOf("https://${BuildConfig.HOST_NAME}")
            )
        } else {
            Log.i(tag, "Document-start scripts unavailable; using on-load filter only")
        }
    }

    private fun injectBannerFilter(wv: WebView, url: String?) {
        val script = bannerScript ?: return
        wv.evaluateJavascript(script) { Log.d(tag, "Banner hide result=$it, url=$url") }
    }

    private inner class ComeWebViewClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            handleNavigation(request.url)

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            injectBannerFilter(view, url)
        }

        override fun onPageCommitVisible(view: WebView, url: String?) {
            injectBannerFilter(view, url)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            injectBannerFilter(view, url)
            if (!awaitingRetry) {
                retryAttempt = 0
                // Thoda ruk ke reveal karte hain taaki React ka pehla paint ho jaye.
                handler.postDelayed({ revealPage() }, 350)
            }
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError
        ) {
            if (!request.isForMainFrame) return
            Log.w(tag, "Main frame error ${error.errorCode} ${error.description}")
            if (retryAttempt < MAX_RETRIES) {
                scheduleReload()
            } else {
                openBrowserFallback(Uri.parse(BuildConfig.START_URL))
                revealPage()
            }
        }
    }

    /** true = hum handle kar liya (WebView na khole), false = WebView khud khole. */
    private fun handleNavigation(uri: Uri): Boolean {
        val scheme = uri.scheme.orEmpty()
        val isWeb = scheme.equals("http", true) || scheme.equals("https", true)
        if (isWeb && BuildConfig.HOST_NAME.equals(uri.host, ignoreCase = true)) return false
        openExternal(uri)
        return true
    }

    private fun scheduleReload() {
        if (awaitingRetry) return
        awaitingRetry = true
        val delay = minOf(400L shl retryAttempt, 2500L)
        retryAttempt++
        Log.i(tag, "Retrying start URL in ${delay}ms (attempt $retryAttempt)")
        handler.postDelayed({
            awaitingRetry = false
            webView?.loadUrl(BuildConfig.START_URL)
        }, delay)
    }

    // ---------------------------------------------------------------- external links

    private fun openExternal(uri: Uri) {
        val scheme = uri.scheme.orEmpty().lowercase()
        if (scheme == "http" || scheme == "https") {
            openBrowserFallback(uri)
            return
        }
        // tel:, mailto:, upi:, whatsapp:, intent: ... jaise schemes
        try {
            val intent = if (scheme == "intent") {
                Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    component = null
                    selector = null
                }
            } else {
                Intent(Intent.ACTION_VIEW, uri)
            }
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.unsupported_link, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.w(tag, "Bad external uri scheme=$scheme", e)
        }
    }

    private fun openBrowserFallback(uri: Uri) {
        try {
            val provider = CustomTabsClient.getPackageName(
                this, listOf("com.android.chrome", "org.chromium.chrome")
            )
            val tab = CustomTabsIntent.Builder().setShowTitle(true).build()
            if (provider != null) tab.intent.setPackage(provider)
            tab.launchUrl(this, uri)
        } catch (e: Exception) {
            Log.w(tag, "Custom Tab unavailable; trying browser intent", e)
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (e2: ActivityNotFoundException) {
                Toast.makeText(this, R.string.unsupported_link, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---------------------------------------------------------------- lifecycle

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView?.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
    }

    override fun onPause() {
        webView?.onPause()
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopSplashMotion()
        webView?.let {
            webViewHost.removeView(it)
            it.stopLoading()
            it.destroy()
        }
        webView = null
        super.onDestroy()
    }

    private companion object {
        const val MAX_RETRIES = 3
    }
}
