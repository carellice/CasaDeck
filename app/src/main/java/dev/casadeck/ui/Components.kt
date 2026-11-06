package dev.casadeck.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

/** Springy press-scale + ripple. */
fun Modifier.bounceClick(onLongClick: (() -> Unit)? = null, onClick: () -> Unit) = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(0.45f, 700f), label = "press")
    graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(interactionSource = src, indication = ripple(), onLongClick = onLongClick, onClick = onClick)
}

/** Staggered rise + fade entrance. */
fun Modifier.staggerIn(index: Int) = composed {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index.coerceAtMost(10) * 55L)
        p.animateTo(1f, spring(0.72f, Spring.StiffnessLow))
    }
    graphicsLayer {
        alpha = p.value.coerceIn(0f, 1f)
        translationY = (1 - p.value) * 48.dp.toPx()
        val s = 0.94f + 0.06f * p.value
        scaleX = s; scaleY = s
    }
}

@Composable
fun CasaCard(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.surfaceContainer, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape = MaterialTheme.shapes.large, color = color) {
        Column(Modifier.padding(20.dp), content = content)
    }
}

/** Slowly drifting colour blobs, drawn only in the draw phase (no recomposition per frame). */
@Composable
fun AnimatedBlobs(modifier: Modifier = Modifier, strength: Float = 0.45f) {
    val t = rememberInfiniteTransition(label = "blobs")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(0f, 1f, infiniteRepeatable(tween(13000, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    val p = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        fun blob(c: Color, x: Float, y: Float, r: Float) =
            drawCircle(Brush.radialGradient(listOf(c.copy(alpha = strength), Color.Transparent), Offset(x, y), r), r, Offset(x, y))
        val w = size.width
        val h = size.height
        blob(p, w * (0.15f + 0.6f * a), h * (0.12f + 0.2f * b), w * 0.85f)
        blob(Teal, w * (0.95f - 0.5f * b), h * (0.35f + 0.15f * a), w * 0.7f)
        blob(Color(0xFFFF7A59), w * (0.3f + 0.4f * b), h * (0.02f + 0.1f * a), w * 0.5f)
    }
}

@Composable
fun Ring(value: Float, label: String, sub: String, color: Color, modifier: Modifier = Modifier) {
    val p by animateFloatAsState(value.coerceIn(0f, 1f), spring(0.8f, Spring.StiffnessVeryLow), label = "ring")
    val track = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                val sw = size.minDimension * 0.1f
                val tl = Offset(sw, sw)
                val sz = Size(size.width - 2 * sw, size.height - 2 * sw)
                drawArc(track, 135f, 270f, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
                drawArc(color.copy(alpha = 0.22f), 135f, 270f * p, false, tl, sz, style = Stroke(sw * 2.1f, cap = StrokeCap.Round))
                drawArc(color, 135f, 270f * p, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
            }
            Text("${(p * 100).roundToInt()}%", style = MaterialTheme.typography.titleLarge)
        }
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** Smoothed line chart; slides left on each new sample. Values in 0..100. */
@Composable
fun Sparkline(values: List<Float>, color: Color, modifier: Modifier = Modifier) {
    val shift = remember { Animatable(0f) }
    LaunchedEffect(values) { shift.snapTo(1f); shift.animateTo(0f, tween(700, easing = FastOutSlowInEasing)) }
    Canvas(modifier.clipToBounds()) {
        if (values.size < 2) return@Canvas
        val step = size.width / (values.size - 2)
        val dx = shift.value * step
        val pts = values.mapIndexed { i, v ->
            Offset((i - 1) * step + dx, size.height - (v / 100f).coerceIn(0f, 1f) * size.height * 0.85f - 6.dp.toPx())
        }
        val line = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) {
                val a = pts[i - 1]; val b = pts[i]; val cx = (a.x + b.x) / 2
                cubicTo(cx, a.y, cx, b.y, b.x, b.y)
            }
        }
        val fill = Path().apply { addPath(line); lineTo(pts.last().x, size.height); lineTo(pts[0].x, size.height); close() }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(line, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color.copy(alpha = 0.25f), 9.dp.toPx(), pts.last())
        drawCircle(color, 4.5.dp.toPx(), pts.last())
    }
}

@Composable
fun PulseDot(color: Color, pulsing: Boolean, size: Dp = 10.dp) {
    val t = rememberInfiniteTransition(label = "pulse")
    val k by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearOutSlowInEasing)), label = "k")
    Canvas(Modifier.size(size * 2)) {
        val r = size.toPx() / 2
        if (pulsing) drawCircle(color.copy(alpha = 0.5f * (1 - k)), r * (1 + 1.2f * k))
        drawCircle(color, r)
    }
}

@Composable
fun Shimmer(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "x")
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val hi = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(
        modifier.clip(MaterialTheme.shapes.medium).background(base).drawWithContent {
            val w = size.width
            drawRect(Brush.linearGradient(listOf(Color.Transparent, hi, Color.Transparent), Offset(w * x, 0f), Offset(w * x + w * 0.6f, size.height)))
        },
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) = Text(
    text.uppercase(Locale.ROOT), modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp),
    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
    color = MaterialTheme.colorScheme.primary, letterSpacing = MaterialTheme.typography.labelMedium.letterSpacing * 1.5f,
)

fun formatBytes(b: Long): String {
    if (b < 1024) return "$b B"
    val units = listOf("KB", "MB", "GB", "TB")
    var v = b / 1024.0
    var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return String.format(Locale.ITALY, if (v < 10) "%.1f %s" else "%.0f %s", v, units[i])
}

@Composable
fun IconBadge(color: Color, modifier: Modifier = Modifier, size: Dp = 44.dp, content: @Composable BoxScope.() -> Unit) =
    Box(modifier.size(size).clip(RoundedCornerShape(size / 3)).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center, content = content)
