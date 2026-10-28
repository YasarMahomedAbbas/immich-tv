package io.github.yasarmahomedabbas.immichtv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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

@Composable
fun ImmichTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
