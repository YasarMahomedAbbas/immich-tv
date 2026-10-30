package io.github.yasarmahomedabbas.immichtv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme

/** The solid dark panels that sit over a photo (manual rule 5), by what they hold. */
enum class OverPhotoStyle(internal val alpha: Float, internal val shape: Shape) {
    /** Text about the photo: `scrim` at 55%, the panel corner. */
    Info(0.55f, RoundedCornerShape(18.dp)),

    /** A short label: `scrim` at 55%, fully round. */
    Pill(0.55f, CircleShape),

    /** The remote's controls: `scrim` at 80%, fully round. */
    Controls(0.8f, CircleShape),
}

/** A panel over a photo; [modifier] places it and pads what it holds. */
@Composable
fun OverPhotoPanel(style: OverPhotoStyle, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val scrim = MaterialTheme.colorScheme.background.copy(alpha = style.alpha)
    Box(modifier.background(scrim, style.shape), content = content)
}
