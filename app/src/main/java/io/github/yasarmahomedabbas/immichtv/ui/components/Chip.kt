package io.github.yasarmahomedabbas.immichtv.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/** A filter chip: `raised` at rest, `fg` with `on-fg` bold text when [selected], fully round, amber focus. */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val fill = if (selected) colors.primary else colors.surfaceVariant
    val content = if (selected) colors.onPrimary else colors.onSurface
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = fill,
            contentColor = content,
            focusedContainerColor = fill,
            focusedContentColor = content,
            pressedContainerColor = fill,
            pressedContentColor = content,
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = houseFocusBorder(CircleShape)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            fontSize = 20.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
        )
    }
}
