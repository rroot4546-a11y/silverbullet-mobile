package com.silverbullet.mobile

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.silverbullet.mobile.databinding.ActivityMainBinding
import com.silverbullet.mobile.net.PingResult
import com.silverbullet.mobile.net.ServerClient
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val settingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                val changed = it.data?.getBooleanExtra("changed", false) ?: false
                if (changed) {
                    binding.setupPanel.visibility = View.GONE
                    load(Prefs.serverUrl)
                }
            }
        }

    private val webClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            if (!isInternalUrl(url) && request.isForMainFrame) {
                openExternal(url)
                return true
            }
            return false
        }

        /**
         * Integrates the embedder bearer-token seam documented in the upstream
         * client (client/client.ts:371): when a token is configured, the main
         * document is fetched with the Authorization header and a
         * `globalThis.silverbullet.bearerToken` bridge is injected before the
         * bundle executes — the same integration the Tauri app uses.
         */
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? {
            val token = Prefs.bearerToken
            if (token.isBlank()) return null
            if (!request.isForMainFrame) return null
            val url = request.url.toString()
            if (!isInternalUrl(url)) return null
            val accept = request.requestHeaders["Accept"] ?: ""
            if (!accept.contains("text/html", ignoreCase = true)) return null
            return fetchWithEmbeddedToken(url, request, token)
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            binding.progressBar.visibility = View.VISIBLE
            binding.loadingOverlay.visibility = View.VISIBLE
            binding.errorPanel.visibility = View.GONE
            binding.webView.visibility = View.VISIBLE
        }

        override fun onPageFinished(view: WebView, url: String) {
            binding.loadingOverlay.visibility = View.GONE
            binding.progressBar.visibility = View.GONE
            binding.toolbar.subtitle = if (Prefs.serverVersion.isNotBlank()) {
                "v${Prefs.serverVersion}"
            } else {
                ""
            }
            updateNavButtons()
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            if (request.isForMainFrame) {
                binding.webView.visibility = View.GONE
                binding.loadingOverlay.visibility = View.GONE
                binding.progressBar.visibility = View.GONE
                binding.errorPanel.visibility = View.VISIBLE
                binding.tvErrorText.text = error.description.toString()
            }
        }
    }

    private val chromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            binding.progressBar.progress = newProgress
            binding.progressBar.visibility =
                if (newProgress >= 100) View.GONE else View.VISIBLE
        }

        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
            android.util.Log.d("SilverBulletWeb", consoleMessage.message())
            return true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        setupWebView()
        setupToolbar()
        setupSetupPanel()

        if (Prefs.serverUrl.isBlank()) {
            showSetup()
        } else {
            load(Prefs.serverUrl)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val ws = binding.webView.settings
        ws.javaScriptEnabled = true
        ws.domStorageEnabled = true
        ws.databaseEnabled = true
        ws.javaScriptCanOpenWindowsAutomatically = true
        ws.allowContentAccess = true
        ws.allowFileAccess = true
        ws.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        ws.mediaPlaybackRequiresUserGesture = false
        ws.userAgentString = ws.userAgentString.replace("Version/", "SilverBullet/")

        CookieManager.getInstance().setAcceptCookie(true)
        binding.webView.webViewClient = webClient
        binding.webView.webChromeClient = chromeClient
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener { if (binding.webView.canGoBack()) binding.webView.goBack() }
        binding.btnForward.setOnClickListener { if (binding.webView.canGoForward()) binding.webView.goForward() }
        binding.btnReload.setOnClickListener { binding.webView.reload() }
        binding.btnSettings.setOnClickListener {
            settingsLauncher.launch(Intent(this, ui.SettingsActivity::class.java))
        }
        binding.btnRetry.setOnClickListener {
            if (!Prefs.serverUrl.isBlank()) load(Prefs.serverUrl) else showSetup()
        }
    }

    private fun setupSetupPanel() {
        binding.btnConnect.setOnClickListener { connect() }
    }

    private fun connect() {
        val url = binding.etServerUrl.text.toString().trim()
        if (url.isBlank()) {
            binding.tvSetupStatus.text = "Enter the server URL"
            return
        }
        binding.btnConnect.isEnabled = false
        binding.tvSetupStatus.text = "Connecting…"
        val token = binding.etToken.text.toString().trim()
        ServerClient.ping(url, token) { result ->
            runOnUiThread {
                binding.btnConnect.isEnabled = true
                if (result.ok) {
                    Prefs.serverUrl = url
                    Prefs.bearerToken = binding.etToken.text.toString().trim()
                    Prefs.serverVersion = result.serverVersion ?: ""
                    binding.tvSetupStatus.text = if (result.serverVersion != null) {
                        "Connected — server v${result.serverVersion}"
                    } else {
                        "Connected"
                    }
                    binding.setupPanel.visibility = View.GONE
                    load(url)
                } else {
                    binding.tvSetupStatus.text = "Failed: ${result.error}"
                }
            }
        }
    }

    private fun load(baseUrl: String) {
        val clean = baseUrl.trim().trimEnd('/')
        Prefs.serverUrl = clean
        binding.errorPanel.visibility = View.GONE
        binding.webView.visibility = View.VISIBLE
        binding.webView.loadUrl("$clean/")
    }

    private fun showSetup() {
        binding.webView.stopLoading()
        binding.webView.visibility = View.GONE
        binding.errorPanel.visibility = View.GONE
        binding.setupPanel.visibility = View.VISIBLE
        binding.etServerUrl.setText(Prefs.serverUrl)
        binding.etToken.setText(Prefs.bearerToken)
        binding.tvSetupStatus.text = ""
    }

    private fun isInternalUrl(url: String): Boolean = try {
        val host = URI(url).host ?: return false
        val serverHost = URI(Prefs.serverUrl.ifBlank { loadedServer() }).host ?: return false
        host == serverHost || host.endsWith(".$serverHost")
    } catch (e: Exception) {
        false
    }

    private fun loadedServer(): String = try {
        URI(binding.webView.url ?: "").let { "${it.scheme}://${it.host}" }
    } catch (e: Exception) {
        Prefs.serverUrl
    }

    private fun openExternal(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            android.widget.Toast.makeText(this, "No browser found", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateNavButtons() {
        binding.btnBack.setImageAlpha(if (binding.webView.canGoBack()) 255 else 90)
        binding.btnForward.setImageAlpha(if (binding.webView.canGoForward()) 255 else 90)
    }

    override fun onBackPressed() {
        if (binding.setupPanel.visibility == View.VISIBLE) {
            super.onBackPressed()
            return
        }
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    private fun fetchWithEmbeddedToken(
        url: String,
        request: WebResourceRequest,
        token: String,
    ): WebResourceResponse? {
        return try {
            val http = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            val headers = request.requestHeaders.toMutableMap().apply {
                put("Authorization", "Bearer $token")
                put("X-Sync-Mode", "true")
                remove("Host")
            }
            val cookieHeader = CookieManager.getInstance().getCookie(url)
            if (!cookieHeader.isNullOrBlank()) headers["Cookie"] = cookieHeader

            val b = Request.Builder().url(url).headers(headers)
            val method = request.method
            val contentType = request.requestHeaders["Content-Type"]
            val body: RequestBody? = request.requestBody?.let { rb ->
                rb.readBytes().toRequestBody(contentType?.toMediaType())
            }
            val call = when (method) {
                "POST" -> b.post(body ?: ByteArray(0).toRequestBody(null))
                "PUT" -> b.put(body ?: ByteArray(0).toRequestBody(null))
                "DELETE" -> b.delete(body)
                "PATCH" -> b.patch(body ?: ByteArray(0).toRequestBody(null))
                "HEAD" -> b.head()
                else -> b.get()
            }.build()

            http.newCall(call).execute().use { resp ->
                val setCookies = resp.headers("Set-Cookie")
                for (c in setCookies) {
                    CookieManager.getInstance().setCookie(url, c)
                }
                CookieManager.getInstance().flush()

                val mime = resp.header("Content-Type")?.substringBefore(";") ?: "text/html"
                val bytes = resp.body?.bytes() ?: ByteArray(0)

                val finalBytes = if (mime == "text/html" || mime == "application/xhtml+xml") {
                    injectTokenBridge(bytes, token)
                } else {
                    bytes
                }

                val responseHeaders = resp.headers.map { it.first to it.second }
                    .filter { (k, _) -> !k.equals("content-length", true) }
                    .toMap()

                WebResourceResponse(
                    mime,
                    "utf-8",
                    resp.code,
                    resp.message,
                    responseHeaders,
                    ByteArrayInputStream(finalBytes),
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Injects the same `globalThis.silverbullet.bearerToken` seam the Tauri embedder uses. */
    private fun injectTokenBridge(html: ByteArray, token: String): ByteArray {
        val escaped = token.replace("\\", "\\\\").replace("\"", "\\\"")
        val script =
            "<script>window.silverbullet=window.silverbullet||{};" +
                "window.silverbullet.bearerToken=\"$escaped\";</script>"
        val text = String(html, Charsets.UTF_8)
        val out = when {
            text.contains("<head", ignoreCase = true) ->
                text.replace(
                    Regex("(<head[^>]*>)", RegexOption.IGNORE_CASE),
                    "$1$script",
                    limit = 1,
                )
            else -> script + text
        }
        return out.toByteArray(Charsets.UTF_8)
    }
}