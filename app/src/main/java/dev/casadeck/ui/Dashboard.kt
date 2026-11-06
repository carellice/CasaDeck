package dev.casadeck.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.casadeck.CasaViewModel
import kotlinx.coroutines.delay
import java.time.LocalTime

@Composable
fun DashboardScreen(vm: CasaViewModel, onOpenApps: () -> Unit) {
    val stats by vm.stats.collectAsState()
    val hist by vm.cpuHistory.collectAsState()
    val net by vm.netRate.collectAsState()
    val apps by vm.apps.collectAsState()
    val ui by vm.ui.collectAsState()
    val cs = MaterialTheme.colorScheme

    LaunchedEffect(ui.refreshSec) {
        while (true) { vm.pollStats(); delay(ui.refreshSec * 1000L) }
    }
    LaunchedEffect(Unit) { if (apps == null) vm.loadApps() }

    Box(Modifier.fillMaxSize()) {
        AnimatedBlobs(Modifier.fillMaxWidth().height(320.dp), strength = 0.22f)
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.statusBarsPadding(),
        ) {
            item { Header(vm.prefs.username, vm.prefs.serverUrl.substringAfter("://"), stats != null, Modifier.staggerIn(0)) }
            item {
                CasaCard(Modifier.staggerIn(1)) {
                    val s = stats
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Ring(
                            (s?.cpu ?: 0f) / 100f, "CPU", s?.let { "${it.cores} core" } ?: "—", cs.primary, Modifier.weight(1f),
                        )
                        Ring(
                            s?.let { if (it.memTotal > 0) it.memUsed.toFloat() / it.memTotal else 0f } ?: 0f, "RAM",
                            s?.let { "${formatBytes(it.memUsed)} / ${formatBytes(it.memTotal)}" } ?: "—", Teal, Modifier.weight(1f),
                        )
                        Ring(
                            s?.let { if (it.diskTotal > 0) it.diskUsed.toFloat() / it.diskTotal else 0f } ?: 0f, "Disco",
                            s?.let { "${formatBytes(it.diskTotal - it.diskUsed)} liberi" } ?: "—", Amber, Modifier.weight(1f),
                        )
                    }
                }
            }
            item {
                CasaCard(Modifier.staggerIn(2)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(cs.primary) { Icon(Icons.Rounded.Memory, null, tint = cs.primary) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Carico CPU", style = MaterialTheme.typography.titleMedium)
                            Text("ultimi ${hist.size * ui.refreshSec}s", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                        stats?.temp?.let { t ->
                            val hot = t >= 70
                            AssistChip(
                                {}, { Text("$t°C") },
                                leadingIcon = { Icon(Icons.Rounded.Thermostat, null, Modifier.size(18.dp)) },
                                colors = AssistChipDefaults.assistChipColors(
                                    leadingIconContentColor = if (hot) cs.error else Amber,
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Sparkline(hist, cs.primary, Modifier.fillMaxWidth().height(110.dp))
                }
            }
            item {
                Row(Modifier.staggerIn(3), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    NetCard("Download", net.first, Icons.Rounded.SouthWest, Teal, Modifier.weight(1f))
                    NetCard("Upload", net.second, Icons.Rounded.NorthEast, cs.primary, Modifier.weight(1f))
                }
            }
            item {
                val list = apps
                CasaCard(Modifier.staggerIn(4).bounceClick(onClick = onOpenApps)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("App", style = MaterialTheme.typography.titleMedium)
                            AnimatedContent(list?.let { l -> "${l.count { it.running }} attive su ${l.size}" } ?: "Caricamento…", label = "cnt") {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            }
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = cs.onSurfaceVariant)
                    }
                    if (!list.isNullOrEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
                            list.filter { it.running }.take(7).forEach { a ->
                                AsyncImage(
                                    a.icon, a.title,
                                    Modifier.size(40.dp).clip(CircleShape).border(3.dp, cs.surfaceContainer, CircleShape)
                                        .background(cs.surfaceContainerHigh).padding(6.dp),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(user: String, host: String, online: Boolean, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val h = remember { LocalTime.now().hour }
    val greet = when { h < 5 -> "Buonanotte"; h < 12 -> "Buongiorno"; h < 18 -> "Buon pomeriggio"; else -> "Buonasera" }
    Column(modifier.padding(top = 8.dp, bottom = 4.dp)) {
        Text("$greet,", style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant)
        Text(user.ifEmpty { "ciao" }, style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.clip(CircleShape).background(cs.surfaceContainerHigh).padding(start = 4.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val c by animateColorAsState(if (online) Color(0xFF2BD47D) else cs.outline, label = "on")
            PulseDot(c, online, 8.dp)
            Text(if (online) host else "Connessione…", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun NetCard(label: String, bps: Long, icon: ImageVector, color: Color, modifier: Modifier) {
    CasaCard(modifier) {
        IconBadge(color, size = 38.dp) { Icon(icon, null, tint = color, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.height(12.dp))
        AnimatedContent(
            formatBytes(bps) + "/s",
            transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) },
            label = "net",
        ) { Text(it, style = MaterialTheme.typography.titleLarge, maxLines = 1) }
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
