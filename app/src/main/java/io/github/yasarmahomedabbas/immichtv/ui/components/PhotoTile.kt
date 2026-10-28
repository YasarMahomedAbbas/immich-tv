package io.github.yasarmahomedabbas.immichtv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import coil3.compose.AsyncImage
import io.github.yasarmahomedabbas.immichtv.R
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.domain.albums.Preview

/** The corner a photo tile takes, by where it sits (the manual's corner tokens). */
enum class TileStyle(val corner: Dp) {
    /** Grid and strip tiles. */
    Tile(12.dp),

    /** An album card's cover. */
    AlbumCard(14.dp),

    /** The album screen's cover mosaic, sized like a panel. */
    Mosaic(18.dp),
}

/**
 * A photo's preview, cropped to fill. A `surface` block while it loads or when there is no
 * [photo]. A video gets a small play badge. A [focusable] tile shows the house focus and calls
 * [onClick] on OK.
 */
@Composable
fun PhotoTile(
    photo: PhotoId?,
    modifier: Modifier = Modifier,
    style: TileStyle = TileStyle.Tile,
    isVideo: Boolean = false,
    focusable: Boolean = false,
    onClick: () -> Unit = {},
) {
    val shape = RoundedCornerShape(style.corner)
    val surface = MaterialTheme.colorScheme.surface
    if (focusable) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = ClickableSurfaceDefaults.shape(shape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = surface,
                focusedContainerColor = surface,
                pressedContainerColor = surface,
            ),
            border = ClickableSurfaceDefaults.border(focusedBorder = houseFocusBorder(shape)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        ) { TileContent(photo, isVideo) }
    } else {
        Box(modifier.clip(shape).background(surface)) { TileContent(photo, isVideo) }
    }
}

@Composable
private fun BoxScope.TileContent(photo: PhotoId?, isVideo: Boolean) {
    if (photo != null) {
        AsyncImage(
            model = Preview(photo),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
    if (isVideo) {
        val colors = MaterialTheme.colorScheme
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
                .size(36.dp)
                .background(colors.background.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_play), contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(22.dp))
        }
    }
}
