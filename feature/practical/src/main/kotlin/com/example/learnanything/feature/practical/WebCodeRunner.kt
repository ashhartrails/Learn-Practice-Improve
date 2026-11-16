package com.example.learnanything.feature.practical

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import com.example.learnanything.core.model.ModelJson
import com.example.learnanything.core.model.practical.CodeRunner
import com.example.learnanything.core.model.practical.PracticalKind
import com.example.learnanything.core.model.practical.PracticalResponse
import com.example.learnanything.core.model.practical.RunReport
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Runs learner code offline in a hidden WebView: Python with Pyodide, SQL with sql.js and
 * JavaScript in a Web Worker (assets/runner). The page is served from the app's own assets
 * at appassets.androidplatform.net; every other request is refused, there is no JavaScript
 * bridge, and the page only answers when the app polls it.
 */
@Singleton
class WebCodeRunner @Inject constructor(@ApplicationContext private val context: Context) : CodeRunner {
    private val mutex = Mutex()
    private var webView: WebView? = null
    private var pageReady: CompletableDeferred<Unit>? = null
    private val warm = mutableSetOf<PracticalKind>()

    override suspend fun run(practical: PracticalResponse, code: String): RunReport = mutex.withLock {
        withContext(Dispatchers.Main) {
            val view = ensurePage() ?: return@withContext RunReport(error = "The code runner couldn't start. Try again.")
            // The first Python run loads the interpreter, which takes a while on a phone.
            val timeoutMs = when {
                practical.kind !in warm && practical.kind == PracticalKind.PYTHON -> 90_000L
                practical.kind !in warm -> 30_000L
                else -> 15_000L
            }
            val id = UUID.randomUUID().toString()
            val payload = buildJsonObject {
                put("kind", practical.kind.wire)
                put("code", code)
                putJsonArray("tests") {
                    practical.tests.forEach { t -> addJsonObject { put("name", t.name); put("code", t.code) } }
                }
                put("setup", practical.sqlSetup)
                put("reference", practical.referenceSolution)
                put("ordered", practical.ordered)
                put("timeoutMs", timeoutMs)
            }.toString()
            view.evaluate("startRun(${js(id)}, ${js(payload)})")
            val result = withTimeoutOrNull(timeoutMs + 5_000) {
                var r: String? = null
                while (r == null) {
                    delay(POLL_MS)
                    r = view.evaluate("takeResult(${js(id)})")?.let { decodeString(it) }
                }
                r
            }
            if (result == null) {
                reset()
                return@withContext RunReport(error = "The code runner stopped responding. Try again.", timedOut = true)
            }
            runCatching { ModelJson.decodeFromString(RunReport.serializer(), result) }
                .onSuccess { if (!it.timedOut) warm += practical.kind else warm -= practical.kind }
                .getOrElse { RunReport(error = "The code runner sent an unreadable result.") }
        }
    }

    private suspend fun WebView.evaluate(script: String): String? = suspendCancellableCoroutine { cont ->
        evaluateJavascript(script) { cont.resume(it) }
    }

    /** evaluateJavascript returns the value JSON-encoded: "null" or a quoted string. */
    private fun decodeString(raw: String): String? =
        runCatching { ModelJson.decodeFromString(String.serializer(), raw) }.getOrNull()

    private fun js(value: String) = JsonPrimitive(value).toString()

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun ensurePage(): WebView? {
        webView?.let { view -> pageReady?.let { if (withTimeoutOrNull(20_000) { it.await() } != null) return view } }
        reset()
        val ready = CompletableDeferred<Unit>()
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/runner/", RunnerAssets(context))
            .build()
        val view = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.domStorageEnabled = false
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
                    loader.shouldInterceptRequest(request.url) ?: blocked()

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true

                override fun onPageFinished(view: WebView, url: String) {
                    ready.complete(Unit)
                }
            }
        }
        webView = view
        pageReady = ready
        view.loadUrl("https://${WebViewAssetLoader.DEFAULT_DOMAIN}/assets/runner/runner.html")
        return if (withTimeoutOrNull(20_000) { ready.await() } != null) view else null.also { reset() }
    }

    private fun reset() {
        webView?.destroy()
        webView = null
        pageReady = null
        warm.clear()
    }

    private fun blocked() = WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))

    private companion object {
        const val POLL_MS = 150L
    }
}

/** Serves assets/runner with the right MIME types (module scripts and wasm need them) and a strict CSP. */
private class RunnerAssets(private val context: Context) : WebViewAssetLoader.PathHandler {
    override fun handle(path: String): WebResourceResponse? {
        if (path.contains("..")) return null
        val stream = try {
            context.assets.open("runner/$path")
        } catch (e: IOException) {
            return null
        }
        val mime = when (path.substringAfterLast('.')) {
            "html" -> "text/html"
            "js", "mjs" -> "text/javascript"
            "wasm" -> "application/wasm"
            "json" -> "application/json"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
        return WebResourceResponse(mime, if (mime.startsWith("text/")) "utf-8" else null, 200, "OK", mapOf("Content-Security-Policy" to CSP), stream)
    }

    companion object {
        const val CSP = "default-src 'self'; script-src 'self' 'unsafe-eval' 'wasm-unsafe-eval'; worker-src 'self'; connect-src 'self'; img-src 'none'; style-src 'none'"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PracticalModule {
    @Binds abstract fun codeRunner(impl: WebCodeRunner): CodeRunner
}
