package dev.casadeck.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.casadeck.CasaViewModel
import dev.casadeck.data.CasaException
import kotlinx.coroutines.launch

private fun normalize(raw: String): String {
    var u = raw.trim().trimEnd('/')
    if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://$u"
    return u
}

@Composable
fun OnboardingScreen(vm: CasaViewModel) {
    var step by rememberSaveable { mutableIntStateOf(if (vm.prefs.serverUrl.isNotEmpty()) 1 else 0) }
    var url by rememberSaveable { mutableStateOf(vm.prefs.serverUrl) }
    var user by rememberSaveable { mutableStateOf(vm.prefs.username) }
    var pass by rememberSaveable { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme

    fun submit() {
        if (loading) return
        scope.launch {
            loading = true
            err = null
            try {
                if (step == 0) {
                    val base = normalize(url)
                    if (!vm.api.probe(base)) throw CasaException("Completa prima la configurazione di CasaOS dal browser")
                    url = base
                    step = 1
                } else {
                    vm.api.login(url, user.trim(), pass)
                    pass = ""
                    vm.onLoggedIn()
                }
            } catch (e: CasaException) {
                err = e.message
                for (x in listOf(18f, -14f, 10f, -6f, 0f)) shake.animateTo(x, tween(55))
            } finally {
                loading = false
            }
        }
    }

    Box(Modifier.fillMaxSize().background(cs.background)) {
        AnimatedBlobs(Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(24.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Logo()
            Spacer(Modifier.height(20.dp))
            Text("CasaDeck", style = MaterialTheme.typography.displaySmall)
            Text(
                if (step == 0) "Il tuo CasaOS, in tasca." else "Bentornato. Accedi al tuo server.",
                style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))

            Surface(
                Modifier.fillMaxWidth().graphicsLayer { translationX = shake.value.dp.toPx() },
                shape = MaterialTheme.shapes.extraLarge, color = cs.surfaceContainer.copy(alpha = 0.92f), tonalElevation = 2.dp,
            ) {
                Column(Modifier.padding(20.dp).animateContentSize(spring(0.8f, Spring.StiffnessMediumLow))) {
                    StepDots(step)
                    Spacer(Modifier.height(16.dp))
                    AnimatedContent(
                        step,
                        transitionSpec = {
                            val d = if (targetState > initialState) 1 else -1
                            (slideInHorizontally(spring(0.85f, Spring.StiffnessMediumLow)) { it * d } + fadeIn()) togetherWith
                                (slideOutHorizontally { -it * d / 2 } + fadeOut()) using SizeTransform(clip = false)
                        },
                        label = "step",
                    ) { s ->
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (s == 0) {
                                Text("Indirizzo del server", style = MaterialTheme.typography.titleLarge)
                                OutlinedTextField(
                                    url, { url = it; err = null }, Modifier.fillMaxWidth(),
                                    placeholder = { Text("192.168.1.50 o casaos.local") },
                                    leadingIcon = { Icon(Icons.Rounded.Dns, null) },
                                    singleLine = true, shape = MaterialTheme.shapes.medium,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                                    keyboardActions = KeyboardActions { submit() },
                                )
                                Text(
                                    "Lo stesso indirizzo che apri nel browser. Porta inclusa se non è la 80.",
                                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                                )
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconBadge(cs.primary, size = 36.dp) { Icon(Icons.Rounded.CheckCircle, null, tint = cs.primary) }
                                    Spacer(Modifier.width(10.dp))
                                    Text(url.substringAfter("://"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                    TextButton({ step = 0; err = null }) { Text("Cambia") }
                                }
                                OutlinedTextField(
                                    user, { user = it; err = null }, Modifier.fillMaxWidth(),
                                    label = { Text("Utente") }, leadingIcon = { Icon(Icons.Rounded.Person, null) },
                                    singleLine = true, shape = MaterialTheme.shapes.medium,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                )
                                OutlinedTextField(
                                    pass, { pass = it; err = null }, Modifier.fillMaxWidth(),
                                    label = { Text("Password") }, leadingIcon = { Icon(Icons.Rounded.Lock, null) },
                                    trailingIcon = {
                                        IconButton({ showPass = !showPass }) {
                                            Icon(if (showPass) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Mostra password")
                                        }
                                    },
                                    visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                                    singleLine = true, shape = MaterialTheme.shapes.medium,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions { submit() },
                                )
                            }
                        }
                    }
                    AnimatedVisibility(err != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Row(
                            Modifier.padding(top = 12.dp).fillMaxWidth().clip(MaterialTheme.shapes.small)
                                .background(cs.error.copy(alpha = 0.12f)).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.ErrorOutline, null, tint = cs.error)
                            Spacer(Modifier.width(8.dp))
                            Text(err.orEmpty(), color = cs.error, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        ::submit, Modifier.fillMaxWidth().height(56.dp),
                        enabled = if (step == 0) url.isNotBlank() else user.isNotBlank() && pass.isNotEmpty(),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        AnimatedContent(loading, transitionSpec = { scaleIn() + fadeIn() togetherWith scaleOut() + fadeOut() }, label = "btn") { l ->
                            if (l) CircularProgressIndicator(Modifier.size(24.dp), color = cs.onPrimary, strokeWidth = 2.5.dp)
                            else Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (step == 0) "Connetti" else "Accedi", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Rounded.ArrowForward, null)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Logo() {
    val cs = MaterialTheme.colorScheme
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, spring(0.45f, Spring.StiffnessLow)) }
    Box(
        Modifier.size(76.dp)
            .graphicsLayer { scaleX = p.value; scaleY = p.value; rotationZ = (1 - p.value) * -40f }
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(cs.primary, Teal))),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Rounded.Home, null, Modifier.size(40.dp), tint = cs.onPrimary) }
}

@Composable
private fun StepDots(step: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(2) { i ->
            val w by animateDpAsState(if (i == step) 28.dp else 8.dp, spring(0.6f), label = "dot")
            val c by animateColorAsState(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, label = "dc")
            Box(Modifier.height(8.dp).width(w).clip(CircleShape).background(c))
        }
    }
}
