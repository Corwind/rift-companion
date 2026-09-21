package com.riftcompanion.app.ui.screens.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Captive WebView login for Piltover Archive.
 *
 * Loads the Clerk Account Portal sign-in page. After successful login,
 * Clerk redirects to piltoverarchive.com where we poll for the session
 * token via the Clerk JS SDK (window.Clerk.session.getToken()).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PiltoverArchiveLoginScreen(
    onBack: () -> Unit,
    onTokenCaptured: (token: String, expiresAt: Long) -> Unit,
) {
    var isLoading by remember { mutableStateOf(true) }
    var statusText by remember { mutableStateOf("Loading sign-in page…") }
    var tokenFound by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                text = "Piltover Archive Login",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }

        if (isLoading && !tokenFound) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text(statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        AndroidView(
            factory = { ctx ->
                val activity = ctx as? Activity

                val tokenReceiver = object {
                    @JavascriptInterface
                    fun onToken(token: String?) {
                        if (token != null && token.isNotEmpty() && !tokenFound) {
                            tokenFound = true
                            val expiresAt = extractJwtExpiry(token)
                            activity?.runOnUiThread { onTokenCaptured(token, expiresAt) }
                        }
                    }
                }

                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    addJavascriptInterface(tokenReceiver, "AndroidToken")

                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            isLoading = true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                            if (url == null || tokenFound) return

                            // If we're on the main site (redirected after login), try to get the token
                            if (isPostLoginUrl(url)) {
                                statusText = "Detecting session…"
                                pollForToken(view)
                            }
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            return false
                        }

                        private fun pollForToken(view: WebView?) {
                            // Poll up to 20 times (every 500ms = 10s total) for the Clerk SDK to be ready
                            var attempts = 0
                            val maxAttempts = 20
                            val handler = android.os.Handler(android.os.Looper.getMainLooper())
                            val pollRunnable = object : Runnable {
                                override fun run() {
                                    if (tokenFound || attempts >= maxAttempts) return
                                    attempts++
                                    statusText = "Detecting session… ($attempts)"

                                    view?.evaluateJavascript("""
                                        (function() {
                                            try {
                                                if (window.Clerk && window.Clerk.session) {
                                                    window.Clerk.session.getToken().then(function(t) {
                                                        AndroidToken.onToken(t);
                                                    }).catch(function() {
                                                        AndroidToken.onToken(null);
                                                    });
                                                } else if (window.Clerk && window.Clerk.loaded) {
                                                    // Clerk loaded but no session — not logged in
                                                    AndroidToken.onToken(null);
                                                }
                                            } catch(e) {
                                                AndroidToken.onToken(null);
                                            }
                                        })();
                                    """.trimIndent(), null)

                                    // Also try cookies as fallback
                                    val cookies = CookieManager.getInstance().getCookie(url)
                                    val cookieToken = extractClerkTokenFromCookieString(cookies)
                                    if (cookieToken != null && !tokenFound) {
                                        tokenFound = true
                                        val expiresAt = extractJwtExpiry(cookieToken)
                                        activity?.runOnUiThread { onTokenCaptured(cookieToken, expiresAt) }
                                        return
                                    }

                                    handler.postDelayed(this, 500)
                                }
                            }
                            handler.post(pollRunnable)
                        }
                    }
                    CookieManager.getInstance().setAcceptCookie(true)
                    loadUrl("https://accounts.piltoverarchive.com/sign-in")
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun isPostLoginUrl(url: String): Boolean {
    val lower = url.lowercase()
    return lower.contains("piltoverarchive.com") &&
           !lower.contains("accounts.piltoverarchive.com") &&
           !lower.contains("clerk.piltoverarchive.com") &&
           !lower.contains("/sign-in") &&
           !lower.contains("/sign-up")
}

private fun extractClerkTokenFromCookieString(cookies: String?): String? {
    if (cookies.isNullOrBlank()) return null
    for (part in cookies.split(";")) {
        val trimmed = part.trim()
        if (trimmed.startsWith("__session=")) {
            val value = trimmed.substringAfter("=")
            if (value.isNotEmpty()) return value
        }
        if (trimmed.startsWith("__clerk_db_jwt=")) {
            val value = trimmed.substringAfter("=")
            if (value.isNotEmpty()) return value
        }
    }
    return null
}

private fun extractJwtExpiry(token: String): Long {
    try {
        val parts = token.split(".")
        if (parts.size < 2) return System.currentTimeMillis() + 3600_000
        val payload = parts[1]
        val decoded = android.util.Base64.decode(
            payload.padEnd(payload.length + (4 - payload.length % 4) % 4, '='),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP,
        ).toString(Charsets.UTF_8)
        val expRegex = """"exp"\s*:\s*(\d+)""".toRegex()
        val match = expRegex.find(decoded)
        if (match != null) {
            val expSeconds = match.groupValues[1].toLong()
            return (expSeconds * 1000) - 300_000
        }
    } catch (_: Exception) {
    }
    return System.currentTimeMillis() + 3600_000
}
