package io.github.yasarmahomedabbas.immichtv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/**
 * A setting that OK steps through: a `surface` card with 16dp corners, the [label] in `mfg`
 * over the current [value], and the house focus.
 */
@Composable
fun SettingButton(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.widthIn(min = 230.dp),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = colors.surface,
            contentColor = colors.onSurface,
            focusedContainerColor = colors.surface,
            focusedContentColor = colors.onSurface,
            pressedContainerColor = colors.surface,
            pressedContentColor = colors.onSurface,
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = houseFocusBorder(shape)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // 18sp, not the canvas's 17: the manual's minimum.
            Text(label, color = colors.onSurfaceVariant, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(value, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        }
    }
}
