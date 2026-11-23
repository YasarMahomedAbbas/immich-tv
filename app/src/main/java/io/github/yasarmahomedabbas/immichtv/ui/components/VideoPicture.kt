package io.github.yasarmahomedabbas.immichtv.ui.components

import android.view.TextureView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.VideoSize

/**
 * A player's picture, fitted and centred. It stays invisible until [visible] (the video's first
 * frame): until then what is under it shows through, and the view would otherwise keep the last
 * video's last frame.
 */
@Composable
internal fun VideoPicture(player: Player, size: VideoSize?, visible: Boolean, modifier: Modifier = Modifier) {
    val ratio = size?.takeIf { it.width > 0 && it.height > 0 }?.let { it.width * it.pixelWidthHeightRatio / it.height }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { viewContext ->
                TextureView(viewContext).apply {
                    isFocusable = false
                    isOpaque = false
                    alpha = 0f
                    player.setVideoTextureView(this)
                }
            },
            update = { it.alpha = if (visible) 1f else 0f },
            modifier = if (ratio == null) {
                Modifier.fillMaxSize()
            } else {
                // Fitted, never cropped: height first for portrait, so it can't run off the screen.
                Modifier.aspectRatio(ratio, matchHeightConstraintsFirst = ratio < 1f)
            },
        )
    }
}
