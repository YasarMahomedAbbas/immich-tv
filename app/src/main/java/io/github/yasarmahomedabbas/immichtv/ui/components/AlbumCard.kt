package io.github.yasarmahomedabbas.immichtv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.yasarmahomedabbas.immichtv.R
import io.github.yasarmahomedabbas.immichtv.domain.albums.Album
import java.text.NumberFormat

/**
 * An album in a grid: its 16:10 cover (the focusable part, with the house focus), the name and
 * "1,032 photos · Shared" under it.
 */
@Composable
fun AlbumCard(album: Album, onClick: () -> Unit, modifier: Modifier = Modifier, coverModifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PhotoTile(
            photo = album.cover,
            style = TileStyle.AlbumCard,
            focusable = true,
            onClick = onClick,
            modifier = coverModifier.fillMaxWidth().aspectRatio(16f / 10f),
        )
        Text(
            album.name,
            color = colors.onSurface,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(albumMeta(album), color = colors.onSurfaceVariant, fontSize = 18.sp, maxLines = 1)
    }
}

@Composable
private fun albumMeta(album: Album): String {
    val count = pluralStringResource(R.plurals.photo_count, album.itemCount, NumberFormat.getIntegerInstance().format(album.itemCount))
    return if (album.shared) "$count · ${stringResource(R.string.album_shared)}" else count
}
