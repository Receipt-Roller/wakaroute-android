package com.wakaroute.app.ui.design

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream

/**
 * A lesson widget, running its own JavaScript.
 *
 * The app renders lessons natively everywhere else, and that is not going to
 * change: native text follows the student's font size, TalkBack reads it, and
 * it works with no signal. This is an island for the one thing native
 * rendering cannot do — a balance scale that tilts when you press a button.
 *
 * **It executes code written by whoever authored the lesson**, so the sandbox
 * below is the feature, not decoration:
 *
 * - **Nothing may reach the network.** [shouldInterceptRequest] answers every
 *   request with an empty body, and the page is loaded with a null base URL so
 *   there is no origin to resolve a relative path against. A lesson cannot
 *   fetch a tracker, and cannot leak that a particular student opened it —
 *   which is the promise the privacy policy makes in so many words.
 * - **No bridge into the app.** `addJavascriptInterface` is never called, so
 *   there is no object for page script to reach the device or the account
 *   through. The one thing the page has to tell us — how tall it is — comes
 *   back through the document title, which is a read-only channel that hands
 *   out no reference to anything.
 * - **No file, content, or storage access**, so a page cannot read the
 *   device's files or keep anything between visits.
 * - **No navigation.** Links do not open; a lesson cannot walk a 中学生 out to
 *   a browser.
 *
 * Verified before this was written: the one lesson using it today references
 * nothing remote and is 1.2 KB of DOM manipulation.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InteractiveView(html: String, modifier: Modifier = Modifier) {
    val colours = MaterialTheme.colorScheme
    val fontScale = LocalConfiguration.current.fontScale

    // Grows to whatever the widget needs. Starts at a height that fits the
    // balance scale so the lesson does not visibly jump on load.
    var heightDp by remember(html) { mutableStateOf(280.dp) }

    val document = remember(html, colours, fontScale) {
        wrap(html, colours.onSurface, colours.primary, fontScale, isDark = colours.surface.luminance() < 0.5f)
    }

    AndroidView(
        modifier = modifier.fillMaxWidth().height(heightDp),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.domStorageEnabled = false
                settings.mediaPlaybackRequiresUserGesture = true
                settings.setSupportZoom(false)

                // So the lesson's own background shows through rather than a
                // white rectangle in the middle of a dark-theme page.
                setBackgroundColor(AndroidColor.TRANSPARENT)
                isVerticalScrollBarEnabled = false

                // The page reports its height by setting document.title. It
                // has to be able to report more than once: pressing a button
                // grows the explanation from one line to three, and a height
                // measured only at load time cuts the buttons in half — which
                // is exactly what happened the first time this shipped.
                webChromeClient = object : WebChromeClient() {
                    override fun onReceivedTitle(view: WebView?, title: String?) {
                        title?.removePrefix(HEIGHT_PREFIX)?.toFloatOrNull()
                            ?.takeIf { title.startsWith(HEIGHT_PREFIX) && it > 0 }
                            ?.let { heightDp = it.dp }
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse =
                        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean = true

                }
            }
        },
        update = { it.loadDataWithBaseURL(null, document, "text/html", "utf-8", null) },
    )
}

/**
 * Wraps the fragment so it inherits the app's appearance.
 *
 * The authored SVG draws with `currentColor` and a transparent background —
 * the author already expected to inherit — so setting `color` on the body is
 * enough to make the diagram follow the theme. Text size is scaled by the
 * student's own setting, which a WebView does not do on its own.
 */
private fun wrap(
    fragment: String,
    onSurface: Color,
    primary: Color,
    fontScale: Float,
    isDark: Boolean,
): String = """
    <!doctype html>
    <html><head>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <style>
      :root { color-scheme: ${if (isDark) "dark" else "light"}; }
      body {
        margin: 0; padding: 0; background: transparent;
        color: ${onSurface.css()};
        font-family: sans-serif;
        font-size: ${16 * fontScale}px;
        line-height: 1.7;
      }
      svg { max-width: 100%; height: auto; }
      button {
        font-size: 1em;
        /* 48dp, the same reachable target the native rows use. */
        min-height: 48px;
        color: ${primary.css()};
      }
    </style>
    </head><body>$fragment
    <script>
      // Reports the content height so the host can size itself, through the
      // title rather than a JavaScript bridge — nothing here can reach the
      // app. Re-reported on every change, because pressing a button in the
      // widget makes the explanation longer.
      (function () {
        var report = function () { document.title = '$HEIGHT_PREFIX' + document.body.scrollHeight; };
        if (window.ResizeObserver) { new ResizeObserver(report).observe(document.body); }
        window.addEventListener('load', report);
        // Older WebViews have no ResizeObserver; these catch the common cases.
        [0, 150, 500].forEach(function (d) { setTimeout(report, d); });
        document.addEventListener('click', function () { setTimeout(report, 50); });
      })();
    </script>
    </body></html>
""".trimIndent()

/** Marks a title that is a height report rather than a real document title. */
private const val HEIGHT_PREFIX = "wakaroute-height:"

private fun Color.css(): String = "#%06X".format(0xFFFFFF and toArgb())

/** Rough perceived brightness — enough to pick a `color-scheme`. */
private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
