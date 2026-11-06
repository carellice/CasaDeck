package dev.casadeck.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.casadeck.CasaViewModel

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebScreen(vm: CasaViewModel, title: String, url: String) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var progress by remember { mutableFloatStateOf(0f) }
    var pageTitle by remember { mutableStateOf(title) }
    var canBack by remember { mutableStateOf(false) }
    val web = remember {
        WebView(ctx).apply {
            // MATCH_PARENT: with AndroidView's default WRAP_CONTENT, pages using height:100% collapse to 0
            layoutParams = android.view.ViewGroup.LayoutParams(-1, -1)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, u: String?, favicon: Bitmap?) { canBack = view.canGoBack() }
                override fun doUpdateVisitedHistory(view: WebView, u: String?, isReload: Boolean) { canBack = view.canGoBack() }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, p: Int) { progress = p / 100f }
                override fun onReceivedTitle(view: WebView, t: String?) { if (!t.isNullOrBlank() && !t.startsWith("http")) pageTitle = t }
            }
            setDownloadListener { u, ua, cd, mime, _ ->
                val name = URLUtil.guessFileName(u, cd, mime)
                vm.enqueueDownload(u, name)
            }
            loadUrl(url)
        }
    }
    DisposableEffect(Unit) { onDispose { web.destroy() } }
    BackHandler { if (web.canGoBack()) web.goBack() else vm.web.value = null }

    Column(Modifier.fillMaxSize().background(cs.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ vm.web.value = null }) { Icon(Icons.Rounded.Close, "Chiudi") }
            Column(Modifier.weight(1f)) {
                Text(pageTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(url.substringAfter("://"), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1)
            }
            AnimatedVisibility(canBack, enter = fadeIn(), exit = fadeOut()) {
                IconButton({ web.goBack() }) { Icon(Icons.Rounded.ArrowBack, "Indietro") }
            }
            IconButton({ web.reload() }) { Icon(Icons.Rounded.Refresh, "Ricarica") }
            IconButton({ runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(web.url ?: url))) } }) {
                Icon(Icons.Rounded.OpenInBrowser, "Apri nel browser")
            }
        }
        val p by animateFloatAsState(progress, label = "p")
        AnimatedVisibility(p < 1f, enter = fadeIn(), exit = fadeOut()) {
            LinearProgressIndicator({ p }, Modifier.fillMaxWidth().height(3.dp), drawStopIndicator = {})
        }
        AndroidView({ web }, Modifier.fillMaxSize())
    }
}
