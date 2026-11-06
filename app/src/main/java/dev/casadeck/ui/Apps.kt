package dev.casadeck.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.casadeck.CasaViewModel
import dev.casadeck.data.CasaApp
import kotlinx.coroutines.launch

private val Green = Color(0xFF2BD47D)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(vm: CasaViewModel) {
    val apps by vm.apps.collectAsState()
    val ui by vm.ui.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var refreshing by remember { mutableStateOf(false) }
    var sheetApp by remember { mutableStateOf<CasaApp?>(null) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme

    LaunchedEffect(Unit) { vm.loadApps() }

    fun open(a: CasaApp) = vm.openWeb(a)

    val shown = apps?.filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) }

    PullToRefreshBox(
        refreshing,
        onRefresh = { scope.launch { refreshing = true; vm.loadApps().join(); refreshing = false } },
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
    ) {
        LazyVerticalGrid(
            GridCells.Fixed(ui.gridCols),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 130.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(bottom = 4.dp)) {
                    Text("Le tue app", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp))
                    apps?.let { l ->
                        Text("${l.count { it.running }} in esecuzione · tieni premuto per gestire", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(14.dp))
                    TextField(
                        query, { query = it }, Modifier.fillMaxWidth(),
                        placeholder = { Text("Cerca") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Rounded.Close, "Cancella") } },
                        singleLine = true, shape = CircleShape,
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = cs.surfaceContainerHigh, unfocusedContainerColor = cs.surfaceContainer,
                        ),
                    )
                }
            }
            when {
                shown == null -> items(9) { Shimmer(Modifier.aspectRatio(0.85f)) }
                shown.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        if (query.isBlank()) "Nessuna app installata" else "Nessun risultato per \"$query\"",
                        Modifier.fillMaxWidth().padding(40.dp), textAlign = TextAlign.Center, color = cs.onSurfaceVariant,
                    )
                }
                else -> itemsIndexed(shown, key = { _, a -> a.id }) { i, a ->
                    AppTile(a, ui.gridCols, Modifier.animateItem().staggerIn(i), { open(a) }, { sheetApp = a })
                }
            }
        }
    }

    sheetApp?.let { a ->
        val live = apps?.find { it.id == a.id } ?: a
        ModalBottomSheet({ sheetApp = null }) {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(live, 56.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(live.title, style = MaterialTheme.typography.titleLarge)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PulseDot(if (live.running) Green else cs.outline, live.running, 6.dp)
                            Text(if (live.running) "In esecuzione" else "Fermata", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SheetAction("Apri", Icons.Rounded.OpenInNew, cs.primary, Modifier.weight(1f), live.url != null) { open(live); sheetApp = null }
                    if (live.running) SheetAction("Ferma", Icons.Rounded.Stop, cs.error, Modifier.weight(1f)) { vm.appAction(live, "stop"); sheetApp = null }
                    else SheetAction("Avvia", Icons.Rounded.PlayArrow, Green, Modifier.weight(1f)) { vm.appAction(live, "start"); sheetApp = null }
                    SheetAction("Riavvia", Icons.Rounded.RestartAlt, Amber, Modifier.weight(1f)) { vm.appAction(live, "restart"); sheetApp = null }
                }
            }
        }
    }
}

@Composable
fun AppIcon(a: CasaApp, size: androidx.compose.ui.unit.Dp) {
    val sat by animateFloatAsState(if (a.running) 1f else 0f, label = "sat")
    var failed by remember(a.icon) { mutableStateOf(a.icon.isBlank()) }
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(size).clip(MaterialTheme.shapes.medium).background(cs.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        // fallback: initial letter when the icon is missing or cannot be decoded
        if (failed) Text(a.title.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = cs.primary)
        else AsyncImage(
            a.icon, a.title, Modifier.fillMaxSize().padding(size / 7),
            contentScale = ContentScale.Fit, onError = { failed = true },
            colorFilter = if (sat < 1f) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(sat) }) else null,
        )
    }
}

@Composable
private fun AppTile(a: CasaApp, cols: Int, modifier: Modifier, onClick: () -> Unit, onLong: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val alpha by animateFloatAsState(if (a.running) 1f else 0.55f, label = "a")
    Surface(modifier.bounceClick(onLong, onClick), shape = MaterialTheme.shapes.large, color = cs.surfaceContainer) {
        Box {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 18.dp, horizontal = 8.dp).graphicsLayer { this.alpha = alpha },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppIcon(a, if (cols >= 4) 44.dp else 56.dp)
                Spacer(Modifier.height(10.dp))
                Text(
                    a.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
            Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                PulseDot(if (a.running) Green else cs.outline, a.running, 5.dp)
            }
        }
    }
}

@Composable
private fun SheetAction(label: String, icon: ImageVector, color: Color, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val bg by animateColorAsState(color.copy(alpha = if (enabled) 0.14f else 0.05f), label = "sa")
    Column(
        modifier.clip(MaterialTheme.shapes.medium).background(bg)
            .then(if (enabled) Modifier.bounceClick(onClick = onClick) else Modifier).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = color.copy(alpha = if (enabled) 1f else 0.4f))
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = color.copy(alpha = if (enabled) 1f else 0.4f))
    }
}
