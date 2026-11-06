package dev.casadeck.ui

import android.app.Activity
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import dev.casadeck.data.UiSettings

val Accents = listOf(
    Color(0xFF5B6CFF), Color(0xFF00BFA6), Color(0xFFFF6B6B), Color(0xFFFFB020),
    Color(0xFFB36BFF), Color(0xFF2EA8FF), Color(0xFFFF5FA2),
)
val Teal = Color(0xFF00C2A8)
val Amber = Color(0xFFFFA63D)

private fun schemeFrom(a: Color, dark: Boolean): ColorScheme {
    fun tint(base: Long, f: Float) = lerp(Color(base), a, f)
    return if (dark) darkColorScheme(
        primary = a, onPrimary = Color.White,
        primaryContainer = tint(0xFF1A1D26, 0.35f), onPrimaryContainer = lerp(a, Color.White, 0.7f),
        secondaryContainer = tint(0xFF1C1F27, 0.2f), onSecondaryContainer = Color.White,
        tertiary = lerp(a, Amber, 0.5f),
        background = tint(0xFF0C0E13, 0.04f), onBackground = Color(0xFFE8EAF0),
        surface = tint(0xFF0C0E13, 0.04f), onSurface = Color(0xFFE8EAF0),
        surfaceVariant = tint(0xFF242833, 0.1f), onSurfaceVariant = Color(0xFF9AA0AE),
        surfaceContainerLow = tint(0xFF13161D, 0.05f), surfaceContainer = tint(0xFF181B23, 0.07f),
        surfaceContainerHigh = tint(0xFF1F232C, 0.1f), surfaceContainerHighest = tint(0xFF272B35, 0.1f),
        outline = Color(0xFF4A505E), outlineVariant = Color(0xFF2A2E38),
    ) else lightColorScheme(
        primary = a, onPrimary = Color.White,
        primaryContainer = tint(0xFFFFFFFF, 0.18f), onPrimaryContainer = lerp(a, Color.Black, 0.5f),
        secondaryContainer = tint(0xFFF0F1F6, 0.12f), onSecondaryContainer = Color(0xFF151821),
        tertiary = lerp(a, Amber, 0.5f),
        background = tint(0xFFF5F6FA, 0.03f), onBackground = Color(0xFF151821),
        surface = tint(0xFFF5F6FA, 0.03f), onSurface = Color(0xFF151821),
        surfaceVariant = tint(0xFFE9EBF1, 0.06f), onSurfaceVariant = Color(0xFF5F6575),
        surfaceContainerLow = Color.White, surfaceContainer = tint(0xFFFFFFFF, 0.03f),
        surfaceContainerHigh = tint(0xFFF0F1F6, 0.06f), surfaceContainerHighest = tint(0xFFE8EAF0, 0.08f),
        outline = Color(0xFFB4B8C4), outlineVariant = Color(0xFFE2E4EC),
    )
}

/** Animates every color we actually use, so theme/accent changes cross-fade. */
@Composable
private fun ColorScheme.animated(): ColorScheme {
    @Composable fun a(c: Color) = animateColorAsState(c, tween(450), label = "c").value
    return copy(
        primary = a(primary), onPrimary = a(onPrimary), primaryContainer = a(primaryContainer),
        onPrimaryContainer = a(onPrimaryContainer), secondaryContainer = a(secondaryContainer),
        onSecondaryContainer = a(onSecondaryContainer), tertiary = a(tertiary),
        background = a(background), onBackground = a(onBackground), surface = a(surface), onSurface = a(onSurface),
        surfaceVariant = a(surfaceVariant), onSurfaceVariant = a(onSurfaceVariant),
        surfaceContainerLow = a(surfaceContainerLow), surfaceContainer = a(surfaceContainer),
        surfaceContainerHigh = a(surfaceContainerHigh), surfaceContainerHighest = a(surfaceContainerHighest),
        outline = a(outline), outlineVariant = a(outlineVariant),
    )
}

private val Typo = Typography().let {
    it.copy(
        displaySmall = it.displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
        headlineMedium = it.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineSmall = it.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = it.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = it.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = it.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun CasaTheme(s: UiSettings, content: @Composable () -> Unit) {
    val dark = when (s.themeMode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    var scheme = if (s.dynamic && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else schemeFrom(Accents[s.accent.coerceIn(Accents.indices)], dark)
    if (dark && s.amoled) scheme = scheme.copy(background = Color.Black, surface = Color.Black, surfaceContainerLow = Color(0xFF0A0A0A))

    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        val w = (view.context as Activity).window
        WindowCompat.getInsetsController(w, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(
        colorScheme = scheme.animated(),
        typography = Typo,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(36.dp),
        ),
        content = content,
    )
}
