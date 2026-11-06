package dev.casadeck.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.casadeck.CasaViewModel

@Composable
fun TerminalScreen(vm: CasaViewModel) {
    // session key: bump to reconnect
    var session by remember { mutableIntStateOf(if (vm.sshPass.isNotEmpty()) 1 else 0) }
    AnimatedContent(session, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "term") { s ->
        if (s == 0) SshLogin(vm) { session++ }
        else Column(Modifier.fillMaxSize().statusBarsPadding().padding(bottom = 96.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Terminale", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton({ session++ }) { Icon(Icons.Rounded.Refresh, "Riconnetti") }
                IconButton({ vm.sshPass = ""; session = 0 }) { Icon(Icons.Rounded.Logout, "Disconnetti") }
            }
            key(s) { XTerm(vm, Modifier.fillMaxSize().padding(12.dp)) }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun XTerm(vm: CasaViewModel, modifier: Modifier) {
    val ctx = LocalContext.current
    val accent = String.format("#%06X", MaterialTheme.colorScheme.primary.toArgb() and 0xFFFFFF)
    val web = remember {
        fun asset(p: String) = ctx.assets.open(p).bufferedReader().use { it.readText() }
        // placeholders first, libraries last, so library code is never rewritten
        val html = asset("terminal.html")
            .replace("\"ACCENT\"", "\"$accent\"")
            .replace("WS_URL", vm.api.sshUrl(vm.prefs.sshUser, vm.sshPass, vm.prefs.sshPort, 0, 0).replace("cols=0", "cols=COLS").replace("rows=0", "rows=ROWS"))
            .replace("/*XTERM_CSS*/", asset("xterm/xterm.css"))
            .replace("/*XTERM_JS*/", asset("xterm/xterm.js"))
            .replace("/*FIT_JS*/", asset("xterm/addon-fit.js"))
        WebView(ctx).apply {
            // AndroidView defaults to WRAP_CONTENT: then CSS height:100% is 0 and the terminal renders as a black box
            layoutParams = android.view.ViewGroup.LayoutParams(-1, -1)
            settings.javaScriptEnabled = true
            setBackgroundColor(0xFF0B0D12.toInt())
            // base url = server, so the websocket Origin matches CasaOS
            loadDataWithBaseURL(vm.prefs.serverUrl, html, "text/html", "utf-8", null)
        }
    }
    DisposableEffect(Unit) { onDispose { web.destroy() } }
    Surface(modifier, shape = MaterialTheme.shapes.large, color = androidx.compose.ui.graphics.Color(0xFF0B0D12)) {
        AndroidView({ web }, Modifier.fillMaxSize())
    }
}

@Composable
private fun SshLogin(vm: CasaViewModel, onConnect: () -> Unit) {
    var user by remember { mutableStateOf(vm.prefs.sshUser) }
    var port by remember { mutableStateOf(vm.prefs.sshPort) }
    var pass by remember { mutableStateOf("") }
    val cs = MaterialTheme.colorScheme
    fun go() {
        vm.prefs.sshUser = user.trim(); vm.prefs.sshPort = port.trim(); vm.sshPass = pass
        onConnect()
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 120.dp)) {
        Text("Terminale", style = MaterialTheme.typography.headlineMedium)
        Text("Accesso SSH al tuo server, come nella dashboard web di CasaOS.", color = cs.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        CasaCard(Modifier.staggerIn(0)) {
            IconBadge(cs.primary) { Icon(Icons.Rounded.Terminal, null, tint = cs.primary) }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(user, { user = it }, Modifier.weight(2f), label = { Text("Utente") }, singleLine = true, shape = MaterialTheme.shapes.medium)
                OutlinedTextField(
                    port, { port = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("Porta") }, singleLine = true,
                    shape = MaterialTheme.shapes.medium, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                pass, { pass = it }, Modifier.fillMaxWidth(), label = { Text("Password SSH") }, singleLine = true,
                shape = MaterialTheme.shapes.medium, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                keyboardActions = KeyboardActions { if (user.isNotBlank() && pass.isNotEmpty()) go() },
            )
            Spacer(Modifier.height(6.dp))
            Text("La password resta solo in memoria finché l'app è aperta.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Button(::go, Modifier.fillMaxWidth().height(54.dp), enabled = user.isNotBlank() && pass.isNotEmpty() && port.isNotEmpty(), shape = MaterialTheme.shapes.medium) {
                Icon(Icons.Rounded.Bolt, null); Spacer(Modifier.width(8.dp)); Text("Connetti")
            }
        }
    }
}
