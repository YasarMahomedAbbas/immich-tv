package io.github.yasarmahomedabbas.immichtv.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.MaterialTheme

/**
 * A `raised` block standing in for something still loading, pulsing slowly. Shape it like what
 * it stands for: [corner] from the manual's tokens, or [round] for text lines and buttons.
 */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, corner: Dp = TileStyle.Tile.corner, round: Boolean = false) {
    val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 1f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1100, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    val shape = if (round) CircleShape else RoundedCornerShape(corner)
    Box(modifier.graphicsLayer { alpha = pulse }.background(MaterialTheme.colorScheme.surfaceVariant, shape))
}
