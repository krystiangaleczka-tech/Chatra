package pl.chatra

import android.app.Activity
import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject
import java.io.ByteArrayInputStream

private const val REFERENCE_URL = "https://demo.chatra.invalid/reference.html"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ChatraContent() {
    val context = LocalContext.current
    val fixtures = remember {
        JSONObject(context.assets.open("screen-fixtures.json").bufferedReader().use { it.readText() })
    }
    val screens = remember {
        val array = fixtures.getJSONArray("screens")
        List(array.length()) { array.getJSONObject(it).getString("title") }
    }
    var selected by rememberSaveable {
        mutableIntStateOf(((context as? Activity)?.intent?.getIntExtra("screen", 0) ?: 0)
            .takeIf { it in 1..screens.size }?.minus(1) ?: -1)
    }
    BackHandler(selected >= 0) { selected = -1 }
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Text("Chatra Demo · podgląd wzorców", Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
        Text("10 ekranów offline. Przyciski we wzorcach są demonstracyjne.", Modifier.padding(horizontal = 16.dp))
        if (selected < 0) {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(screens) { index, title ->
                    OutlinedButton(onClick = { selected = index }, modifier = Modifier.fillMaxWidth()) {
                        Text("S%02d · %s".format(index + 1, title))
                    }
                }
            }
        } else {
            TextButton(onClick = { selected = -1 }) { Text("← Lista ekranów · S%02d".format(selected + 1)) }
            key(selected) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    factory = { ctx ->
                        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.domStorageEnabled = false
                            settings.setSupportMultipleWindows(false)
                            settings.textZoom = 100
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                    request.isForMainFrame || request.url.scheme !in setOf("about", "blob")
                                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                                    if (request.url.toString() == REFERENCE_URL) {
                                        return WebResourceResponse("text/html", "UTF-8", ctx.assets.open("reference.html"))
                                    }
                                    return WebResourceResponse("text/plain", "UTF-8", 403, "Offline demo", emptyMap(), ByteArrayInputStream(byteArrayOf()))
                                }
                                override fun onPageFinished(view: WebView, url: String) {
                                    view.evaluateJavascript(referencePresentation(selected), null)
                                }
                            }
                            loadUrl(REFERENCE_URL)
                        }
                    },
                    onRelease = { it.stopLoading(); it.destroy() },
                )
            }
        }
    }
}

// Only the outer presentation is adjusted. The bundled source and its iframe content stay intact.
private fun referencePresentation(index: Int): String = """
    (() => {
      let tries = 0;
      const show = () => {
        const frames = [...document.querySelectorAll('iframe')];
        if (frames.length !== 10 || !frames[$index].contentDocument?.body?.innerText.trim()) {
          if (++tries < 200) setTimeout(show, 100);
          return;
        }
        document.documentElement.lang = 'pl';
        document.body.style.cssText = 'margin:0;padding:0;display:block;overflow:auto';
        document.querySelectorAll('.board').forEach((board, i) => {
          board.style.display = i === $index ? 'block' : 'none';
          const heading = board.querySelector('h2');
          if (heading) heading.style.display = 'none';
        });
        const frame = frames[$index];
        frame.style.borderRadius = '0';
        frame.style.boxShadow = 'none';
      };
      show();
    })();
""".trimIndent()
