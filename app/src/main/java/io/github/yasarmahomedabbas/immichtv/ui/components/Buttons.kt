package io.github.yasarmahomedabbas.immichtv.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.yasarmahomedabbas.immichtv.R

/** The screen's one main action: `fg` fill, `on-fg` text, an optional leading icon, fully round, amber focus outline. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: Painter? = null,
) {
    val colors = MaterialTheme.colorScheme
    HouseButton(
        text, onClick, modifier, enabled, icon = icon, fill = colors.primary, content = colors.onPrimary,
        fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, padding = PaddingValues(horizontal = 40.dp, vertical = 20.dp),
    )
}

/**
 * Every other action: `raised` fill, `fg` text, an optional leading icon tinted to match.
 * [compact] is for a button sitting among chips or beside a field.
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    compact: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    HouseButton(
        text, onClick, modifier, enabled = true, icon = icon, fill = colors.surfaceVariant, content = colors.onSurface,
        fontSize = if (compact) 20.sp else 24.sp,
        fontWeight = FontWeight.SemiBold,
        padding = if (compact) {
            PaddingValues(horizontal = 22.dp, vertical = 10.dp)
        } else {
            PaddingValues(horizontal = 34.dp, vertical = 20.dp)
        },
    )
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
    fontSize: TextUnit,
    fontWeight: FontWeight,
    padding: PaddingValues,
) {
    // Focus changes only the outline: the fill stays put so amber is the one focus signal.
    val focusBorder = houseFocusBorder(CircleShape)
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = ButtonDefaults.shape(shape = CircleShape),
        contentPadding = padding,
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
        Text(text, fontSize = fontSize, fontWeight = fontWeight)
    }
}

/** A round `raised` button with only an icon, for the top menu's Settings gear. */
@Composable
fun IconButton(icon: Painter, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val fill = colors.surfaceVariant
    Button(
        onClick = onClick,
        modifier = modifier.size(56.dp),
        shape = ButtonDefaults.shape(shape = CircleShape),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.colors(
            containerColor = fill,
            contentColor = colors.onSurface,
            focusedContainerColor = fill,
            focusedContentColor = colors.onSurface,
            pressedContainerColor = fill,
            pressedContentColor = colors.onSurface,
        ),
        border = ButtonDefaults.border(focusedBorder = houseFocusBorder(CircleShape)),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(28.dp), tint = colors.onSurface)
        }
    }
}

/** "‹ Albums": a text-only way back, top-left of a screen. `mfg` at rest, `fg` with the house focus. */
@Composable
fun BackLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val clear = colors.background
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = ButtonDefaults.shape(shape = CircleShape),
        contentPadding = PaddingValues(start = 12.dp, end = 20.dp),
        colors = ButtonDefaults.colors(
            containerColor = clear,
            contentColor = colors.onSurfaceVariant,
            focusedContainerColor = clear,
            focusedContentColor = colors.onSurface,
            pressedContainerColor = clear,
            pressedContentColor = colors.onSurface,
        ),
        border = ButtonDefaults.border(focusedBorder = houseFocusBorder(CircleShape)),
    ) {
        Icon(painterResource(R.drawable.ic_back), contentDescription = null, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    }
}
