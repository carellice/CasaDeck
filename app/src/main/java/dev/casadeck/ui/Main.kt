package dev.casadeck.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import dev.casadeck.CasaViewModel

enum class Tab(val label: String, val icon: ImageVector, val selected: ImageVector) {
    Home("Home", Icons.Outlined.SpaceDashboard, Icons.Rounded.SpaceDashboard),
    Apps("App", Icons.Outlined.Apps, Icons.Rounded.Apps),
    Files("File", Icons.Outlined.Folder, Icons.Rounded.Folder),
    Terminal("Shell", Icons.Outlined.Terminal, Icons.Rounded.Terminal),
    Settings("Opzioni", Icons.Outlined.Tune, Icons.Rounded.Tune),
}

@Composable
fun MainScreen(vm: CasaViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    val snack = remember { SnackbarHostState() }
    val msg by vm.message.collectAsState()
    LaunchedEffect(msg) { msg?.let { vm.message.value = null; snack.showSnackbar(it) } }
    BackHandler(tab != Tab.Home) { tab = Tab.Home }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedContent(
            tab,
            transitionSpec = {
                val d = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (slideInHorizontally(spring(0.9f, Spring.StiffnessMediumLow)) { it / 4 * d } + fadeIn(tween(260))) togetherWith
                    (slideOutHorizontally(tween(220)) { -it / 4 * d } + fadeOut(tween(160)))
            },
            label = "tab",
        ) { t ->
            when (t) {
                Tab.Home -> DashboardScreen(vm) { tab = Tab.Apps }
                Tab.Apps -> AppsScreen(vm)
                Tab.Files -> FilesScreen(vm)
                Tab.Terminal -> TerminalScreen(vm)
                Tab.Settings -> SettingsScreen(vm)
            }
        }
        // soft fade so content scrolls under the floating bar
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(120.dp)
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background))),
        )
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 88.dp))
        NavPill(tab, { tab = it }, Modifier.align(Alignment.BottomCenter))

        val web by vm.web.collectAsState()
        AnimatedContent(
            web,
            transitionSpec = {
                (slideInVertically(spring(0.85f, Spring.StiffnessMediumLow)) { it } + fadeIn()) togetherWith
                    (slideOutVertically(tween(250)) { it } + fadeOut(tween(250)))
            },
            label = "web",
        ) { w -> if (w != null) WebScreen(vm, w.first, w.second) else Spacer(Modifier) }

        val locked by vm.locked.collectAsState()
        val quick by vm.quickPick.collectAsState()
        // the sheet is its own window and would sit above the lock screen, so wait for unlock
        if (quick && !locked) QuickAppsSheet(vm)
        val update by vm.update.collectAsState()
        update?.let { if (!locked) UpdateDialog(vm, it) }

        AnimatedVisibility(locked, enter = fadeIn(), exit = fadeOut(tween(400)) + scaleOut(targetScale = 1.1f)) {
            LockScreen { vm.locked.value = false }
        }
    }
}

@Composable
private fun NavPill(current: Tab, onSelect: (Tab) -> Unit, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    Surface(
        modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth()
            .shadow(24.dp, CircleShape, spotColor = cs.primary.copy(alpha = 0.35f)),
        shape = CircleShape, color = cs.surfaceContainerHigh,
    ) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Tab.entries.forEach { t ->
                val sel = t == current
                val bg by animateColorAsState(if (sel) cs.primary else Color.Transparent, spring(stiffness = Spring.StiffnessMediumLow), label = "bg")
                val fg by animateColorAsState(if (sel) cs.onPrimary else cs.onSurfaceVariant, label = "fg")
                Row(
                    Modifier.clip(CircleShape).background(bg)
                        .clickable(remember { MutableInteractionSource() }, ripple()) {
                            if (!sel) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(t)
                        }
                        .padding(horizontal = if (sel) 16.dp else 12.dp, vertical = 12.dp)
                        .animateContentSize(spring(0.7f, Spring.StiffnessMediumLow)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Crossfade(sel, label = "ic") { s -> Icon(if (s) t.selected else t.icon, t.label, tint = fg) }
                    if (sel) Text(t.label, Modifier.padding(start = 8.dp), color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun UpdateDialog(vm: CasaViewModel, r: dev.casadeck.Updater.Release) {
    val progress by vm.updateProgress.collectAsState()
    AlertDialog(
        onDismissRequest = { if (progress == null) vm.update.value = null },
        icon = { Icon(Icons.Rounded.SystemUpdate, null) },
        title = { Text("CasaDeck ${r.version} disponibile") },
        text = {
            Column {
                Text(r.notes.ifBlank { "È disponibile una nuova versione." }, maxLines = 8, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                progress?.let {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator({ it }, Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = { TextButton({ vm.installUpdate(r) }, enabled = progress == null) { Text(if (progress == null) "Aggiorna" else "Download…") } },
        dismissButton = { TextButton({ vm.update.value = null }, enabled = progress == null) { Text("Più tardi") } },
    )
}

/** "Most used apps" picker, opened from the quick-settings tile. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAppsSheet(vm: CasaViewModel) {
    val apps by vm.apps.collectAsState()
    LaunchedEffect(Unit) { if (apps == null) vm.loadApps() }
    ModalBottomSheet({ vm.quickPick.value = false }) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
            Text("App più usate", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            val list = apps?.let { vm.prefs.topApps(it).take(8) }
            if (list == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            else list.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    row.forEach { a ->
                        Column(
                            Modifier.weight(1f).clip(MaterialTheme.shapes.medium)
                                .clickable { vm.quickPick.value = false; vm.openWeb(a) }.padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AppIcon(a, 52.dp)
                            Spacer(Modifier.height(6.dp))
                            Text(a.title, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
