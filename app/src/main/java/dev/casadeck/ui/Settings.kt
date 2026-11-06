package dev.casadeck.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.casadeck.CasaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: CasaViewModel) {
    val ui by vm.ui.collectAsState()
    val cs = MaterialTheme.colorScheme
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmPower by remember { mutableStateOf<String?>(null) } // "restart" | "off"

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Impostazioni", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.staggerIn(0)) }
        item {
            CasaCard(Modifier.staggerIn(1)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).background(Brush.linearGradient(listOf(cs.primary, Teal))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(vm.prefs.username.take(1).uppercase().ifEmpty { "?" }, style = MaterialTheme.typography.titleLarge, color = Color.White)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(vm.prefs.username, style = MaterialTheme.typography.titleMedium)
                        Text(vm.prefs.serverUrl, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
            }
        }
        item { SectionTitle("Aspetto", Modifier.staggerIn(2)) }
        item {
            CasaCard(Modifier.staggerIn(3)) {
                Text("Tema", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                val opts = listOf("Sistema" to Icons.Rounded.BrightnessAuto, "Chiaro" to Icons.Rounded.LightMode, "Scuro" to Icons.Rounded.DarkMode)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    opts.forEachIndexed { i, (label, icon) ->
                        SegmentedButton(
                            ui.themeMode == i, { vm.updateUi { it.copy(themeMode = i) } },
                            SegmentedButtonDefaults.itemShape(i, opts.size),
                            icon = { Icon(icon, null, Modifier.size(18.dp)) },
                        ) { Text(label) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                ToggleRow("Nero AMOLED", "Sfondo nero puro nel tema scuro", Icons.Rounded.Contrast, ui.amoled) { v -> vm.updateUi { it.copy(amoled = v) } }
                if (Build.VERSION.SDK_INT >= 31) {
                    ToggleRow("Colori dinamici", "Usa i colori del tuo sfondo", Icons.Rounded.Palette, ui.dynamic) { v -> vm.updateUi { it.copy(dynamic = v) } }
                }
            }
        }
        item {
            CasaCard(Modifier.staggerIn(4)) {
                Text("Colore accento", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Accents.forEachIndexed { i, c ->
                        val sel = i == ui.accent && !ui.dynamic
                        val ring by animateDpAsState(if (sel) 3.dp else 0.dp, spring(0.5f), label = "ring")
                        Box(
                            Modifier.size(38.dp).border(ring, cs.onSurface, CircleShape).padding(5.dp).clip(CircleShape).background(c)
                                .bounceClick { vm.updateUi { it.copy(accent = i, dynamic = false) } },
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.animation.AnimatedVisibility(sel, enter = scaleIn(spring(0.4f)), exit = scaleOut()) {
                                Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
        item { SectionTitle("Sicurezza e avvisi", Modifier.staggerIn(5)) }
        item {
            val lockOn by vm.lockOn.collectAsState()
            val notifyOn by vm.notifyOn.collectAsState()
            val activity = LocalContext.current as FragmentActivity
            val askPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
                vm.setNotify(ok)
                if (!ok) vm.message.value = "Permesso notifiche negato"
            }
            CasaCard(Modifier.staggerIn(5)) {
                ToggleRow("Blocco biometrico", "Impronta, volto o PIN all'apertura", Icons.Rounded.Fingerprint, lockOn) { v ->
                    // confirm with a real prompt both ways, so a lost fingerprint can't lock you out silently
                    authenticate(activity, if (v) "Attiva blocco" else "Disattiva blocco") { ok -> if (ok) vm.setLock(v) }
                }
                ToggleRow("Notifiche", "App ferme, disco pieno, CPU calda, server offline (ogni 15 min)", Icons.Rounded.NotificationsActive, notifyOn) { v ->
                    if (v && Build.VERSION.SDK_INT >= 33) askPerm.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.setNotify(v)
                }
            }
        }
        item { SectionTitle("Layout", Modifier.staggerIn(5)) }
        item {
            CasaCard(Modifier.staggerIn(6)) {
                SliderRow("Colonne griglia app", "${ui.gridCols}", ui.gridCols.toFloat(), 2f..5f, 2) { v -> vm.updateUi { it.copy(gridCols = v) } }
                Spacer(Modifier.height(8.dp))
                SliderRow("Aggiornamento dashboard", "${ui.refreshSec}s", ui.refreshSec.toFloat(), 2f..10f, 7) { v -> vm.updateUi { it.copy(refreshSec = v) } }
            }
        }
        item { SectionTitle("Server", Modifier.staggerIn(6)) }
        item {
            Row(Modifier.staggerIn(6), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(Triple("restart", "Riavvia", Icons.Rounded.RestartAlt), Triple("off", "Spegni", Icons.Rounded.PowerSettingsNew)).forEach { (st, label, icon) ->
                    val c = if (st == "off") cs.error else Amber
                    Button(
                        { confirmPower = st }, Modifier.weight(1f).height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = c.copy(alpha = 0.14f), contentColor = c),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Icon(icon, null)
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        }
        item {
            Button(
                { confirmLogout = true }, Modifier.fillMaxWidth().height(54.dp).staggerIn(7),
                colors = ButtonDefaults.buttonColors(containerColor = cs.error.copy(alpha = 0.14f), contentColor = cs.error),
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(Icons.Rounded.Logout, null)
                Spacer(Modifier.width(8.dp))
                Text("Esci")
            }
        }
        item {
            Text(
                "CasaDeck 1.0", Modifier.fillMaxWidth().padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall, color = cs.outline,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }

    confirmPower?.let { st ->
        val off = st == "off"
        AlertDialog(
            onDismissRequest = { confirmPower = null },
            icon = { Icon(if (off) Icons.Rounded.PowerSettingsNew else Icons.Rounded.RestartAlt, null) },
            title = { Text(if (off) "Spegnere il server?" else "Riavviare il server?") },
            text = {
                Text(
                    if (off) "Tutte le app si fermeranno. Per riaccenderlo dovrai staccare e riattaccare l'alimentazione."
                    else "Tutte le app si fermeranno per un paio di minuti.",
                )
            },
            confirmButton = { TextButton({ confirmPower = null; vm.power(st) }) { Text(if (off) "Spegni" else "Riavvia") } },
            dismissButton = { TextButton({ confirmPower = null }) { Text("Annulla") } },
        )
    }

    if (confirmLogout) AlertDialog(
        onDismissRequest = { confirmLogout = false },
        icon = { Icon(Icons.Rounded.Logout, null) },
        title = { Text("Uscire?") },
        text = { Text("Dovrai inserire di nuovo la password per accedere.") },
        confirmButton = { TextButton({ confirmLogout = false; vm.logout() }) { Text("Esci") } },
        dismissButton = { TextButton({ confirmLogout = false }) { Text("Annulla") } },
    )
}

@Composable
private fun ToggleRow(title: String, sub: String, icon: ImageVector, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(MaterialTheme.colorScheme.primary, size = 38.dp) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange, thumbContent = if (checked) ({ Icon(Icons.Rounded.Check, null, Modifier.size(14.dp)) }) else null)
    }
}

@Composable
private fun SliderRow(title: String, value: String, v: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
    Slider(v, { onChange(Math.round(it)) }, valueRange = range, steps = steps)
}
