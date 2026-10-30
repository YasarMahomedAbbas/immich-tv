package io.github.yasarmahomedabbas.immichtv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

// The house tokens from docs/design/README.md. One theme: the app always looks the same.
val Ground = Color(0xFF0E0F12)
val Surface = Color(0xFF191B20)
val Raised = Color(0xFF22252C)
val Fg = Color(0xFFF3F1EC)
val Mfg = Color(0xFFA3A6AE)
val Focus = Color(0xFFF5C451)

private val colors = darkColorScheme(
    primary = Fg,
    onPrimary = Ground,
    background = Ground,
    onBackground = Fg,
    surface = Surface,
    onSurface = Fg,
    surfaceVariant = Raised,
    onSurfaceVariant = Mfg,
    border = Focus,
)

/** The width of the design canvas's artboard. The manual, the canvas and the code all measure in it. */
const val CANVAS_WIDTH = 1920

/**
 * The density where 1dp is 1/[CANVAS_WIDTH] of the screen's width, so a canvas value written as dp
 * takes the same share of the screen on any TV. A Chromecast renders 1920 px at density 2.0, which
 * left the app only 960 dp and drew everything at twice its size. The system's font scale is kept.
 * A width of 0 means the system doesn't know it yet, so its own density stands.
 */
internal fun canvasDensity(system: Density, screenWidthDp: Int): Density {
    if (screenWidthDp <= 0) return system
    return Density(density = screenWidthDp * system.density / CANVAS_WIDTH, fontScale = system.fontScale)
}

@Composable
fun ImmichTvTheme(content: @Composable () -> Unit) {
    val system = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val density = remember(system, screenWidthDp) { canvasDensity(system, screenWidthDp) }
    CompositionLocalProvider(LocalDensity provides density) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}
