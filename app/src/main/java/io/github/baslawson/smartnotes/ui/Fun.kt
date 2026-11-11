package io.github.baslawson.smartnotes.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/** A checkbox that springs when ticked and gives a small buzz (the phone's own setting for touch feedback applies). */
@Composable
fun FunCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val view = LocalView.current
    var bounce by remember { mutableIntStateOf(0) }
    val scale by animateFloatAsState(
        targetValue = if (bounce % 2 == 1) 1.25f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        finishedListener = { if (bounce % 2 == 1) bounce++ },
        label = "tick",
    )
    Checkbox(
        checked = checked,
        onCheckedChange = { now ->
            if (now) {
                bounce = if (bounce % 2 == 0) bounce + 1 else bounce
                view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.KEYBOARD_TAP)
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
            onCheckedChange(now)
        },
        modifier = modifier.scale(scale),
    )
}

/** A short burst of confetti and a cheerful line, fired by [fire] (say, when the last thing is ticked off). */
class Celebration {
    var message by mutableStateOf<String?>(null)
        private set
    var round by mutableIntStateOf(0)
        private set

    fun fire(text: String) {
        message = text
        round++
    }

    fun done(at: Int) {
        if (round == at) message = null
    }

    companion object {
        val SHOPPING = listOf("All bought! 🎉", "Basket full! 🛒", "Shopping done! 🥳", "Nothing left to buy! ✨")
        val TASKS = listOf("All done! 🎉", "Nothing left to do! 🥳", "You did it! ✨", "Clean sweep! 🌟")
    }
}

@Composable
fun rememberCelebration() = remember { Celebration() }

private class Piece(val x: Float, val vx: Float, val vy: Float, val spin: Float, val size: Float, val color: Color, val delay: Float)

/** Draws the celebration over the screen; touches go through to the screen below. */
@Composable
fun CelebrationOverlay(celebration: Celebration) {
    val text = celebration.message ?: return
    val round = celebration.round
    val time = remember(round) { Animatable(0f) }
    LaunchedEffect(round) {
        time.animateTo(1f, tween(2200, easing = LinearEasing))
        celebration.done(round)
    }
    val colors = listOf(
        Color(Accent.NOTES.dark), Color(Accent.TASKS.dark), Color(Accent.SHOPPING.dark), Color(Accent.BRAND.dark),
        Color(0xFFFFD54F), Color(0xFF81C784),
    )
    val pieces = remember(round) {
        val r = Random(round)
        List(90) {
            Piece(
                x = r.nextFloat(), vx = (r.nextFloat() - 0.5f) * 0.5f, vy = 0.35f + r.nextFloat() * 0.5f,
                spin = (r.nextFloat() - 0.5f) * 1080f, size = 8f + r.nextFloat() * 10f, color = colors[r.nextInt(colors.size)],
                delay = r.nextFloat() * 0.25f,
            )
        }
    }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val t = time.value
            pieces.forEach { p ->
                val life = ((t - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
                if (life <= 0f) return@forEach
                val x = (p.x + p.vx * life) * size.width
                val y = -40f + (p.vy * life + 0.9f * life * life) * size.height
                val fade = (1f - life * life * life).coerceIn(0f, 1f)
                rotate(p.spin * life, Offset(x, y)) {
                    drawRect(p.color, topLeft = Offset(x - p.size / 2, y - p.size / 4), size = Size(p.size, p.size / 2), alpha = fade)
                }
            }
        }
        // Pops in, stays, then fades.
        val t = time.value
        val alpha = when {
            t < 0.1f -> t / 0.1f
            t > 0.75f -> ((1f - t) / 0.25f).coerceIn(0f, 1f)
            else -> 1f
        }
        val pop = if (t < 0.15f) 0.6f + 0.4f * (t / 0.15f) * 1.1f else 1f
        Text(
            text,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            textAlign = TextAlign.Center,
            // Read out by screen readers too.
            modifier = Modifier.align(Alignment.Center).semantics { liveRegion = LiveRegionMode.Polite }.alpha(alpha).scale(pop)
                .background(MaterialTheme.colorScheme.primaryContainer, PillShape)
                .padding(horizontal = 28.dp, vertical = 16.dp),
        )
    }
}

/** "3 of 8 bought" with a ring that fills up. */
@Composable
fun ProgressLine(done: Int, total: Int, label: String, modifier: Modifier = Modifier) {
    if (total == 0) return
    val progress by animateFloatAsState(done.toFloat() / total, spring(stiffness = Spring.StiffnessLow), label = "progress")
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress }, modifier = Modifier.size(22.dp), strokeWidth = 3.dp,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest, strokeCap = StrokeCap.Round,
            )
        }
        Text(
            if (done == total) "All $label! ✨" else "$done of $total $label",
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** An empty screen: a picture in a soft circle, a short title and what to do. */
@Composable
fun EmptyState(icon: ImageVector, title: String, hint: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(96.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                hint, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A one-time tip for [tip] ("notes", "tasks", "shopping"): shown until "Got it", then never again (remembered on this
 * phone only, like the rest of where you left off).
 */
@Composable
fun OneTimeTip(tip: String, text: String, horizontal: Dp = 12.dp) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state = remember { io.github.baslawson.smartnotes.AppState(context.getSharedPreferences("state", android.content.Context.MODE_PRIVATE)) }
    var shown by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(!state.tipSeen(tip)) }
    androidx.compose.animation.AnimatedVisibility(shown) {
        Row(
            Modifier.padding(horizontal = horizontal, vertical = 4.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.medium)
                .padding(start = 16.dp, top = 12.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text("💡", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 10.dp, top = 2.dp).clearAndSetSemantics {})
            Column(Modifier.weight(1f)) {
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                androidx.compose.material3.TextButton(
                    onClick = { state.setTipSeen(tip); shown = false },
                    modifier = Modifier.align(Alignment.End),
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                ) { Text("Got it") }
            }
        }
    }
}
