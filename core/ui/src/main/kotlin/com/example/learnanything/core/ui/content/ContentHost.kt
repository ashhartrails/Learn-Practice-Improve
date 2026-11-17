package com.example.learnanything.core.ui.content

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * The one WebView for outside content (YouTube embeds, PhET simulations).
 * - Only [allowedHosts] may load in the frame; any other link opens in a Custom Tab.
 * - No JavaScript bridge, no file or content access, no pop-up windows.
 * - Media needs a tap to start (no autoplay).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ContentHost(
    url: String,
    allowedHosts: Set<String>,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 16f / 9f,
    headers: Map<String, String> = emptyMap(),
) {
    AndroidView(
        modifier = modifier.fillMaxWidth().aspectRatio(aspectRatio),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setSupportMultipleWindows(false)
                settings.javaScriptCanOpenWindowsAutomatically = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        if (!request.isForMainFrame) return false
                        if (isAllowed(request.url, allowedHosts)) return false
                        openInBrowser(view.context, request.url.toString())
                        return true
                    }
                }
                loadUrl(url, headers)
            }
        },
        onRelease = { it.destroy() },
    )
}

/** True when [uri] is https and its host is on the list. */
fun isAllowed(uri: Uri, allowedHosts: Set<String>): Boolean =
    uri.scheme == "https" && uri.host?.lowercase() in allowedHosts

/** Opens an outside page in a Custom Tab (or any browser if Custom Tabs aren't available). */
fun openInBrowser(context: Context, url: String) {
    val uri = Uri.parse(url)
    if (uri.scheme != "https") return
    try {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)
    } catch (e: ActivityNotFoundException) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/** URLs the app builds itself from ids and catalog keys (principle 3). */
object ContentUrls {
    const val YOUTUBE_EMBED_HOST = "www.youtube-nocookie.com"

    private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")

    fun youTubeEmbed(videoId: String): String {
        require(VIDEO_ID.matches(videoId)) { "Not a YouTube video id" }
        return "https://$YOUTUBE_EMBED_HOST/embed/$videoId?rel=0&playsinline=1"
    }

    fun youTubeSearch(query: String): String =
        "https://www.youtube.com/results?search_query=" + Uri.encode(query)

    /** YouTube's embed player needs a Referer that identifies the app. */
    fun refererFor(context: Context): Map<String, String> = mapOf("Referer" to "https://${context.packageName}")
}
