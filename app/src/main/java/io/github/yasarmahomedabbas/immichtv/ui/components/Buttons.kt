package io.github.yasarmahomedabbas.immichtv.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/** The screen's one main action: `fg` fill, `on-fg` text, fully round, amber focus outline. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    HouseButton(text, onClick, modifier, enabled, icon = null, fill = colors.primary, content = colors.onPrimary)
}

/** Every other action: `raised` fill, `fg` text, an optional leading icon tinted to match. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: Painter? = null) {
    val colors = MaterialTheme.colorScheme
    HouseButton(text, onClick, modifier, enabled = true, icon = icon, fill = colors.surfaceVariant, content = colors.onSurface)
}

@Composable
private fun HouseButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    icon: Painter?,
    fill: Color,
    content: Color,
) {
    // Focus changes only the outline: the fill stays put so amber is the one focus signal.
    val focusBorder = houseFocusBorder(CircleShape)
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = ButtonDefaults.shape(shape = CircleShape),
        colors = ButtonDefaults.colors(
            containerColor = fill,
            contentColor = content,
            focusedContainerColor = fill,
            focusedContentColor = content,
            pressedContainerColor = fill,
            pressedContentColor = content,
            disabledContainerColor = fill.copy(alpha = 0.6f),
            disabledContentColor = content.copy(alpha = 0.6f),
        ),
        border = ButtonDefaults.border(focusedBorder = focusBorder, focusedDisabledBorder = focusBorder),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = content)
            Spacer(Modifier.width(10.dp))
        }
        Text(text, fontSize = 22.sp)
    }
}
