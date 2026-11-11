package io.github.baslawson.smartnotes.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.baslawson.smartnotes.R

/**
 * SmartNotes' own colours: warm paper neutrals, and one accent per part of the app (amber notes, teal tasks, coral
 * shopping; violet for the rest), the same on every phone, light and dark.
 */
enum class Accent(
    val light: Long, val lightContainer: Long, val onLightContainer: Long,
    val dark: Long, val darkContainer: Long, val onDarkContainer: Long,
) {
    BRAND(0xFF6A4FD0, 0xFFE7DEFF, 0xFF22005D, 0xFFCBBEFF, 0xFF4D33B0, 0xFFE7DEFF),
    NOTES(0xFF9A5B00, 0xFFFFDDB3, 0xFF311A00, 0xFFFFB95C, 0xFF6E4200, 0xFFFFDDB3),
    TASKS(0xFF00796B, 0xFFA6F2E3, 0xFF00201B, 0xFF5EDCC4, 0xFF00524A, 0xFFA6F2E3),
    SHOPPING(0xFFC2412F, 0xFFFFDAD4, 0xFF410100, 0xFFFFB4A6, 0xFF6E2C22, 0xFFFFDAD4),
}

/** Whether the app is dark (Settings → Appearance, or the phone's). */
val LocalDark = staticCompositionLocalOf { false }

fun smartColors(dark: Boolean, accent: Accent): ColorScheme = if (dark) darkColorScheme(
    primary = Color(accent.dark), onPrimary = Color(0xFF1B1300),
    primaryContainer = Color(accent.darkContainer), onPrimaryContainer = Color(accent.onDarkContainer),
    secondaryContainer = Color(accent.darkContainer), onSecondaryContainer = Color(accent.onDarkContainer),
    tertiary = Color(Accent.BRAND.dark),
    // Search highlights.
    tertiaryContainer = Color(0xFF6B5300), onTertiaryContainer = Color(0xFFFFE08A),
    background = Color(0xFF17130F), onBackground = Color(0xFFEDE1D8),
    surface = Color(0xFF17130F), onSurface = Color(0xFFEDE1D8),
    surfaceVariant = Color(0xFF50453A), onSurfaceVariant = Color(0xFFD5C4B4),
    // Cards stand out a little from the page.
    surfaceContainerLowest = Color(0xFF26211C), surfaceContainerLow = Color(0xFF1F1B16),
    surfaceContainer = Color(0xFF241F1A), surfaceContainerHigh = Color(0xFF2F2A24), surfaceContainerHighest = Color(0xFF3A342E),
    outline = Color(0xFF9E8E80), outlineVariant = Color(0xFF50453A),
) else lightColorScheme(
    primary = Color(accent.light), onPrimary = Color.White,
    primaryContainer = Color(accent.lightContainer), onPrimaryContainer = Color(accent.onLightContainer),
    secondaryContainer = Color(accent.lightContainer), onSecondaryContainer = Color(accent.onLightContainer),
    tertiary = Color(Accent.BRAND.light),
    tertiaryContainer = Color(0xFFFFE08A), onTertiaryContainer = Color(0xFF241A00),
    background = Color(0xFFFFF8F1), onBackground = Color(0xFF1F1B16),
    surface = Color(0xFFFFF8F1), onSurface = Color(0xFF1F1B16),
    surfaceVariant = Color(0xFFF1E0D0), onSurfaceVariant = Color(0xFF51453A),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFCF2E8),
    surfaceContainer = Color(0xFFF6ECE2), surfaceContainerHigh = Color(0xFFF1E6DC), surfaceContainerHighest = Color(0xFFEBE0D6),
    outline = Color(0xFF84756A), outlineVariant = Color(0xFFD6C4B4),
)

/** Nunito (SIL Open Font License, see licenses/): rounded and friendly. One variable font file, any weight. */
@OptIn(ExperimentalTextApi::class)
private val Nunito = FontFamily(
    listOf(400, 500, 600, 700, 800).map { w ->
        Font(R.font.nunito, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    }
)

private fun TextStyle.nunito(weight: Int) = copy(fontFamily = Nunito, fontWeight = FontWeight(weight))

val SmartTypography: Typography = Typography().run {
    copy(
        displayLarge = displayLarge.nunito(800), displayMedium = displayMedium.nunito(800), displaySmall = displaySmall.nunito(800),
        headlineLarge = headlineLarge.nunito(800), headlineMedium = headlineMedium.nunito(800), headlineSmall = headlineSmall.nunito(800),
        titleLarge = titleLarge.nunito(800), titleMedium = titleMedium.nunito(700), titleSmall = titleSmall.nunito(700),
        bodyLarge = bodyLarge.nunito(500), bodyMedium = bodyMedium.nunito(500), bodySmall = bodySmall.nunito(500),
        labelLarge = labelLarge.nunito(700), labelMedium = labelMedium.nunito(700), labelSmall = labelSmall.nunito(700),
    )
}

val SmartShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp),
)

/** The app's look with [accent] as its colour (each tab, and the screens opened from it, has its own). */
@Composable
fun AccentTheme(accent: Accent, content: @Composable () -> Unit) {
    val dark = LocalDark.current
    val colors = androidx.compose.runtime.remember(dark, accent) { smartColors(dark, accent) }
    MaterialTheme(colorScheme = colors, typography = SmartTypography, shapes = SmartShapes) {
        CompositionLocalProvider(content = content)
    }
}

/** A rounded, filled search or add box (no underline). */
val PillShape = RoundedCornerShape(28.dp)

@Composable
fun pillColors(): TextFieldColors = MaterialTheme.colorScheme.let { c ->
    TextFieldDefaults.colors(
        focusedContainerColor = c.surfaceContainerHigh, unfocusedContainerColor = c.surfaceContainerHigh,
        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent, errorIndicatorColor = Color.Transparent,
    )
}

/** A row as a soft card: rounded, a little apart from the next, lifted while dragged; [faded] for done/ticked rows. */
@Composable
fun RowCard(
    modifier: Modifier = Modifier,
    dragging: Boolean = false,
    faded: Boolean = false,
    content: @Composable () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Surface(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (faded) c.surfaceContainerLow else c.surfaceContainerLowest,
        shadowElevation = when {
            dragging -> 8.dp
            faded || LocalDark.current -> 0.dp
            else -> 1.dp
        },
        content = content,
    )
}

/**
 * A list row you can swipe: right to tick (or untick) it, left to delete it ([onDelete] offers Undo). The row springs
 * back after a tick; a deleted row leaves the list. Off while selecting ([enabled]). TalkBack gets the same two as actions.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SwipeActions(
    enabled: Boolean,
    ticked: Boolean,
    onTick: () -> Unit,
    onDelete: () -> Unit,
    content: @Composable (actions: Modifier) -> Unit,
) {
    val state = androidx.compose.material3.rememberSwipeToDismissBoxState(positionalThreshold = { it * 0.35f })
    // The state is saved with the row's key: a row swiped away and brought back by Undo comes back still "swiped".
    // That first value is only put straight, never acted on (it would delete the row again); later swipes act as usual.
    var first by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    val tick by androidx.compose.runtime.rememberUpdatedState(onTick)
    val delete by androidx.compose.runtime.rememberUpdatedState(onDelete)
    // Act once the swipe has settled (not while dragging past the threshold and back).
    androidx.compose.runtime.LaunchedEffect(state.currentValue) {
        if (first) {
            first = false
            if (state.currentValue != androidx.compose.material3.SwipeToDismissBoxValue.Settled) {
                state.snapTo(androidx.compose.material3.SwipeToDismissBoxValue.Settled)
                return@LaunchedEffect
            }
        }
        when (state.currentValue) {
            androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd -> { tick(); state.snapTo(androidx.compose.material3.SwipeToDismissBoxValue.Settled) }
            androidx.compose.material3.SwipeToDismissBoxValue.EndToStart -> delete()
            androidx.compose.material3.SwipeToDismissBoxValue.Settled -> Unit
        }
    }
    val c = MaterialTheme.colorScheme
    androidx.compose.material3.SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = enabled,
        enableDismissFromEndToStart = enabled,
        gesturesEnabled = enabled,
        backgroundContent = {
            val dir = state.dismissDirection
            if (dir == androidx.compose.material3.SwipeToDismissBoxValue.Settled) return@SwipeToDismissBox
            val right = dir == androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd
            Box(
                Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp)
                    .background(if (right) c.primaryContainer else c.errorContainer, MaterialTheme.shapes.medium)
                    .padding(horizontal = 20.dp),
                contentAlignment = if (right) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                androidx.compose.material3.Icon(
                    if (right) androidx.compose.material.icons.Icons.Default.Check else androidx.compose.material.icons.Icons.Default.Delete,
                    contentDescription = null, tint = if (right) c.onPrimaryContainer else c.onErrorContainer,
                )
            }
        },
    ) {
        // For TalkBack, on the row it focuses (the caller puts [actions] on its clickable row).
        content(
            if (!enabled) Modifier else Modifier.semantics {
                customActions = listOf(
                    androidx.compose.ui.semantics.CustomAccessibilityAction(if (ticked) "Untick" else "Tick") { onTick(); true },
                    androidx.compose.ui.semantics.CustomAccessibilityAction("Delete") { onDelete(); true },
                )
            }
        )
    }
}
