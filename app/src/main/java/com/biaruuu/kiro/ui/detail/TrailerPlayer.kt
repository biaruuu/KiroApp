package com.biaruuu.kiro.ui.detail

import android.annotation.SuppressLint
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Looping, muted-by-default YouTube trailer with no controls, used as the details hero.
 *
 * It is the official YouTube IFrame player inside a WebView. The page tells us through a small
 * JavaScript bridge when the video is really playing ([onPlaying]) or cannot be embedded
 * ([onFailed]), so the caller can show a still image until then and fall back to it on errors.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TrailerPlayer(
    videoId: String,
    muted: Boolean,
    onPlaying: () -> Unit,
    onFailed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnPlaying by rememberUpdatedState(onPlaying)
    val currentOnFailed by rememberUpdatedState(onFailed)
    val lifecycleOwner = LocalLifecycleOwner.current
    var webView by remember { mutableStateOf<WebView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                isClickable = false
                isFocusable = false

                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // Lets the trailer start by itself, like the reference.
                settings.mediaPlaybackRequiresUserGesture = false

                // Never navigate away from the player page (for example a tap on the video).
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean = request.isForMainFrame
                }

                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onState(state: Int) {
                            // 1 = playing
                            if (state == 1) post { currentOnPlaying() }
                        }

                        @JavascriptInterface
                        fun onError(code: Int) {
                            post { currentOnFailed() }
                        }
                    },
                    "Kiro",
                )

                loadDataWithBaseURL(
                    "https://biaruuu.github.io/",
                    playerHtml(videoId),
                    "text/html",
                    "utf-8",
                    null,
                )
                webView = this
            }
        },
    )

    LaunchedEffect(webView, muted) {
        webView?.evaluateJavascript("setMuted($muted);", null)
    }

    // Stop the sound the moment the app goes to the background; resume when it comes back.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> webView?.evaluateJavascript("pausePlay();", null)
                Lifecycle.Event.ON_RESUME -> webView?.evaluateJavascript("resumePlay();", null)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webView?.let {
                it.stopLoading()
                it.removeJavascriptInterface("Kiro")
                it.loadUrl("about:blank")
                it.destroy()
            }
            webView = null
        }
    }
}

/**
 * The player page. The iframe is sized like CSS "object-fit: cover" so the 16:9 video fills any
 * hero shape, and scaled up a little so YouTube's edge UI is cropped away.
 */
private fun playerHtml(videoId: String): String = """
<!DOCTYPE html>
<html>
<head>
<meta name="referrer" content="strict-origin-when-cross-origin">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
<style>
  html, body { margin: 0; height: 100%; background: #000; overflow: hidden; }
  /* The WebView is sized 16:9 by the app, so the player simply fills it. Width-based units only:
     the WebView reported a wrong viewport height before. */
  #wrap { position: absolute; left: 0; top: 0; width: 100vw; height: 56.25vw; pointer-events: none; }
  #wrap iframe { position: absolute; left: 0; top: 0; width: 100%; height: 100%; border: 0; }
</style>
</head>
<body>
<div id="wrap"><div id="player"></div></div>
<script src="https://www.youtube.com/iframe_api"></script>
<script>
  var player;
  function onYouTubeIframeAPIReady() {
    player = new YT.Player('player', {
      width: '100%', height: '100%',
      videoId: 'VIDEO_ID',
      playerVars: {
        autoplay: 1, mute: 1, controls: 0, disablekb: 1, fs: 0, modestbranding: 1,
        rel: 0, playsinline: 1, iv_load_policy: 3, loop: 1, playlist: 'VIDEO_ID'
      },
      events: {
        onReady: function (e) { e.target.mute(); e.target.playVideo(); },
        onStateChange: function (e) { Kiro.onState(e.data); },
        onError: function (e) { Kiro.onError(e.data); }
      }
    });
  }
  function setMuted(m) {
    if (!player || !player.mute) return;
    if (m) { player.mute(); } else { player.unMute(); player.setVolume(100); }
  }
  function pausePlay() { if (player && player.pauseVideo) player.pauseVideo(); }
  function resumePlay() { if (player && player.playVideo) player.playVideo(); }
</script>
</body>
</html>
""".trimIndent().replace("VIDEO_ID", videoId)
