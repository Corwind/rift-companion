package com.riftcompanion.app.ui.screens.settings

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Captive WebView login for Piltover Archive.
 *
 * Loads the Piltover Archive sign-in page in a WebView.
 * After successful login, extracts the Clerk session token from cookies
 * and passes it to the caller.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PiltoverArchiveLoginScreen(
    onBack: () -> Unit,
    onTokenCaptured: (token: String, expiresAt: Long) -> Unit,
) {
    var isLoading by remember { mutableStateOf(true) }
    var currentUrl by remember { mutableStateOf("") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        // Top bar
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
                text = "Log in to Piltover Archive",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text("Loading…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            isLoading = true
                            currentUrl = url ?: ""
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                            currentUrl = url ?: ""

                            // Check if we're past the login page (redirected to dashboard/home)
                            // Clerk redirects to the app after successful login
                            if (url != null && isPostLoginUrl(url)) {
                                // Extract session token from cookies
                                val cookies = CookieManager.getInstance().getCookie(url)
                                val token = extractClerkToken(cookies)
                                if (token != null) {
                                    // Clerk session tokens are JWTs — extract expiry from the token
                                    val expiresAt = extractJwtExpiry(token)
                                    onTokenCaptured(token, expiresAt)
                                }
                            }
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            return false
                        }
                    }
                    loadUrl("https://piltoverarchive.com/sign-in")
                    webViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun isPostLoginUrl(url: String): Boolean {
    // After login, Clerk redirects to the app home or dashboard
    // Check if we're no longer on a sign-in URL
    val lower = url.lowercase()
    return !lower.contains("/sign-in") &&
           !lower.contains("/sign-up") &&
           !lower.contains("/login") &&
           (lower.contains("piltoverarchive.com") || lower.contains("clerk"))
}

private fun extractClerkToken(cookies: String?): String? {
    if (cookies.isNullOrBlank()) return null
    // Clerk stores the session token in __clerk_db_jwt cookie
    val parts = cookies.split(";")
    for (part in parts) {
        val trimmed = part.trim()
        if (trimmed.startsWith("__clerk_db_jwt=")) {
            return trimmed.substringAfter("=")
        }
        // Also check __session cookie
        if (trimmed.startsWith("__session=")) {
            return trimmed.substringAfter("=")
        }
    }
    return null
}

private fun extractJwtExpiry(token: String): Long {
    // JWTs have 3 parts separated by dots: header.payload.signature
    // The payload is base64url-encoded JSON with an "exp" field (Unix timestamp in seconds)
    try {
        val parts = token.split(".")
        if (parts.size < 2) return System.currentTimeMillis() + 3600_000 // Default 1 hour
        val payload = parts[1]
        // Base64url decode
        val decoded = android.util.Base64.decode(
            payload.padEnd(payload.length + (4 - payload.length % 4) % 4, '='),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP,
        ).toString(Charsets.UTF_8)
        // Parse JSON to find "exp"
        // Simple string search to avoid JSON parsing dependency
        val expRegex = """"exp"\s*:\s*(\d+)""".toRegex()
        val match = expRegex.find(decoded)
        if (match != null) {
            val expSeconds = match.groupValues[1].toLong()
            // Set TTL slightly below actual expiry (5 minute buffer)
            return (expSeconds * 1000) - 300_000
        }
    } catch (_: Exception) {
        // If parsing fails, default to 1 hour
    }
    return System.currentTimeMillis() + 3600_000
}
