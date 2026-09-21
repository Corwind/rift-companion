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
 * Clerk redirects to piltoverarchive.com where we extract ALL session
 * cookies (which are long-lived, unlike the 60-second JWT tokens).
 * The cookies are stored and sent with every PA API request.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PiltoverArchiveLoginScreen(
    onBack: () -> Unit,
    onCookiesCaptured: (cookies: String) -> Unit,
) {
    var isLoading by remember { mutableStateOf(true) }
    var statusText by remember { mutableStateOf("Loading sign-in page…") }
    var done by remember { mutableStateOf(false) }

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

        if (isLoading && !done) {
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

                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true

                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            isLoading = true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                            if (url == null || done) return

                            if (isPostLoginUrl(url)) {
                                statusText = "Capturing session…"
                                // Wait a moment for cookies to settle, then grab them
                                val handler = android.os.Handler(android.os.Looper.getMainLooper())
                                var attempts = 0
                                val maxAttempts = 10
                                val checkRunnable = object : Runnable {
                                    override fun run() {
                                        if (done || attempts >= maxAttempts) return
                                        attempts++
                                        val cm = CookieManager.getInstance()
                                        // Gather cookies from all relevant domains
                                        val mainCookies = cm.getCookie("https://piltoverarchive.com") ?: ""
                                        val clerkCookies = cm.getCookie("https://clerk.piltoverarchive.com") ?: ""
                                        val accountsCookies = cm.getCookie("https://accounts.piltoverarchive.com") ?: ""
                                        val allCookies = listOf(mainCookies, clerkCookies, accountsCookies)
                                            .filter { it.isNotBlank() }
                                            .joinToString("; ")
                                        // Check if we have any auth-related cookie
                                        if (allCookies.contains("__session") || allCookies.contains("__clerk_db_jwt") || allCookies.contains("__client_uat")) {
                                            done = true
                                            activity?.runOnUiThread { onCookiesCaptured(allCookies) }
                                        } else if (attempts >= maxAttempts) {
                                        } else {
                                            handler.postDelayed(this, 500)
                                        }
                                    }
                                }
                                handler.postDelayed(checkRunnable, 500)
                            }
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            return false
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
