package com.ortaksistem.seyirbox.catalog

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.app.Dialog
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.lagradost.cloudstream3.CommonActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

internal object WebPlayback {
    private val opening = Mutex()
    private var currentPlayer: PlaybackWindow? = null

    suspend fun open(title: String, page: String, waitSeconds: Int = 0): Boolean = withContext(Dispatchers.Main) {
        if (!isHttpsUrl(page) || !opening.tryLock()) return@withContext false
        try {
            val activity = CommonActivity.activity ?: return@withContext false
            if (activity.isFinishing || activity.isDestroyed || currentPlayer != null) return@withContext false
            if (waitSeconds > 0 && !waitToOpen(activity, title, waitSeconds)) return@withContext false
            if (activity.isFinishing || activity.isDestroyed) return@withContext false

            val player = try {
                PlaybackWindow(activity, title) { currentPlayer = null }
            } catch (_: RuntimeException) {
                Toast.makeText(activity, "Web oynatıcı başlatılamadı. Android System WebView uygulamasını kontrol edin.", Toast.LENGTH_LONG).show()
                return@withContext false
            }
            currentPlayer = player
            try {
                player.show(page)
                true
            } catch (_: WindowManager.BadTokenException) {
                player.close()
                false
            }
        } finally {
            opening.unlock()
        }
    }

    private suspend fun waitToOpen(activity: Activity, title: String, seconds: Int): Boolean {
        val dialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage("Oynatıcı açılıyor · $seconds sn")
            .setNegativeButton("İptal", null)
            .show()
        try {
            for (remaining in seconds downTo 1) {
                if (!dialog.isShowing || activity.isDestroyed) return false
                dialog.setMessage("Oynatıcı açılıyor · $remaining sn")
                delay(1_000)
            }
            return dialog.isShowing && dialog.window?.decorView?.hasWindowFocus() == true
        } finally {
            dialog.dismiss()
        }
    }
}

private class PlaybackWindow(
    private val activity: Activity,
    title: String,
    private val onClosed: () -> Unit,
) : Application.ActivityLifecycleCallbacks {
    private val web = playbackView(activity)
    private val layout = playerLayout(activity, title, web, ::close)
    private val root = FrameLayout(activity).apply {
        setBackgroundColor(Color.BLACK)
        addView(layout, FrameLayout.LayoutParams(-1, -1))
    }
    private val dialog = object : Dialog(activity) {
        @Suppress("OVERRIDE_DEPRECATION")
        override fun onBackPressed() {
            when {
                fullscreenView != null -> exitFullscreen()
                web.canGoBack() -> web.goBack()
                else -> close()
            }
        }
    }
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var popup: Dialog? = null
    private var popupWeb: WebView? = null
    private var closed = false

    init {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(root)
        dialog.setOnDismissListener { close() }
        web.settings.setSupportMultipleWindows(true)
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (fullscreenView != null || closed) {
                    callback.onCustomViewHidden()
                    return
                }
                fullscreenView = view
                fullscreenCallback = callback
                layout.visibility = View.GONE
                root.addView(view, FrameLayout.LayoutParams(-1, -1))
                view.requestFocus()
            }

            override fun onHideCustomView() = exitFullscreen()

            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                if (!isUserGesture || popup != null || closed) return false
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                val window = Dialog(activity)
                val child = playbackView(activity)
                popup = window
                popupWeb = child
                child.webChromeClient = object : WebChromeClient() {
                    override fun onCloseWindow(window: WebView) {
                        if (window === child) popup?.dismiss()
                    }
                }
                window.requestWindowFeature(Window.FEATURE_NO_TITLE)
                window.setContentView(playerLayout(activity, "Ek pencere", child, window::dismiss))
                window.setOnDismissListener {
                    popup = null
                    popupWeb = null
                    child.release()
                    if (!closed) (fullscreenView ?: web).requestFocus()
                }
                window.show()
                val display = root.resources.displayMetrics
                val width = minOf(display.widthPixels * 9 / 10, (460 * display.density).toInt())
                val height = minOf(display.heightPixels * 4 / 5, (340 * display.density).toInt())
                window.window?.setLayout(width, height)
                window.window?.setGravity(Gravity.CENTER)
                transport.webView = child
                resultMsg.sendToTarget()
                return true
            }
        }
    }

    fun show(page: String) {
        dialog.show()
        dialog.window?.let { window ->
            window.setLayout(-1, -1)
            if (Build.VERSION.SDK_INT >= 30) {
                window.insetsController?.hide(WindowInsets.Type.statusBars())
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            }
        }
        activity.application.registerActivityLifecycleCallbacks(this)
        web.loadUrl(page)
        web.requestFocus()
    }

    fun close() {
        if (closed) return
        closed = true
        activity.application.unregisterActivityLifecycleCallbacks(this)
        popup?.dismiss()
        exitFullscreen()
        dialog.dismiss()
        web.release()
        onClosed()
    }

    private fun exitFullscreen() {
        val view = fullscreenView ?: return
        val callback = fullscreenCallback
        fullscreenView = null
        fullscreenCallback = null
        root.removeView(view)
        layout.visibility = View.VISIBLE
        callback?.onCustomViewHidden()
        if (!closed) web.requestFocus()
    }

    override fun onActivityStarted(activity: Activity) {
        if (activity !== this.activity || closed) return
        web.onResume()
        popupWeb?.onResume()
    }

    override fun onActivityStopped(activity: Activity) {
        if (activity !== this.activity || closed) return
        web.onPause()
        popupWeb?.onPause()
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (activity === this.activity) close()
    }

    override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
}

private fun playbackView(activity: Activity) = WebView(activity).apply {
    setBackgroundColor(Color.BLACK)
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        allowFileAccess = false
        allowContentAccess = false
        javaScriptCanOpenWindowsAutomatically = false
    }
    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            request.isForMainFrame && !isAllowedWebNavigation(request.url.toString())

        @Suppress("OVERRIDE_DEPRECATION")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = !isAllowedWebNavigation(url)

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) showPageError()
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
            if (request.isForMainFrame) showPageError()
        }

        private fun showPageError() {
            Toast.makeText(activity, "Kaynak sayfası açılamadı.", Toast.LENGTH_SHORT).show()
        }
    }
}

internal fun isAllowedWebNavigation(url: String): Boolean = url == "about:blank" || isHttpsUrl(url)

private fun playerLayout(activity: Activity, title: String, web: WebView, close: () -> Unit): LinearLayout {
    val bar = LinearLayout(activity).apply {
        setBackgroundColor(Color.rgb(15, 23, 42))
        gravity = Gravity.CENTER_VERTICAL
        addView(TextView(activity).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 16f
            val inset = (16 * resources.displayMetrics.density).toInt()
            setPadding(inset, 0, inset, 0)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(Button(activity).apply {
            text = "Kapat"
            setOnClickListener { close() }
        }, LinearLayout.LayoutParams(-2, -2))
    }
    return LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        addView(bar, LinearLayout.LayoutParams(-1, -2))
        addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
    }
}

private fun WebView.release() {
    stopLoading()
    (parent as? ViewGroup)?.removeView(this)
    destroy()
}
