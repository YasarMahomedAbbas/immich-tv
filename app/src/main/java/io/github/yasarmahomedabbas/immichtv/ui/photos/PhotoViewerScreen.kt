package io.github.yasarmahomedabbas.immichtv.ui.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.imageLoader
import io.github.yasarmahomedabbas.immichtv.R
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.ui.components.VideoBadge
import io.github.yasarmahomedabbas.immichtv.ui.components.fullScreenRequest
import io.github.yasarmahomedabbas.immichtv.ui.theme.ImmichTvTheme

// The slideshow's overlay margins: this screen has no safe area either.
private val BadgeSide = 96.dp
private val BadgeEdge = 54.dp

/**
 * One photo full-screen, whole, on the page ground, with nothing over it. Left and right move through
 * the timeline, held keys too. Back isn't handled here: it stays with the activity's handler. With
 * no photo open there is nothing to show, so it goes straight back.
 */
@Composable
fun PhotoViewerScreen(viewModel: PhotoGridViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val photo = state.viewing
    if (photo == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    PreloadImage(state.neighbour(1))
    PreloadImage(state.neighbour(-1))
    PhotoViewerContent(photo, viewModel::viewerMove)
}

/** Keeps a neighbour's image ready: with the one on screen, at most three bitmaps are asked for. */
@Composable
private fun PreloadImage(photo: Photo?) {
    val id = photo?.id ?: return
    val context = LocalContext.current
    LaunchedEffect(id) { context.imageLoader.execute(fullScreenRequest(context, id)) }
}

@Composable
private fun PhotoViewerContent(photo: Photo, onMove: (Int) -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { rootFocus.requestFocus() } }
    var failed by remember(photo.id) { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .focusRequester(rootFocus)
            .onPreviewKeyEvent { event ->
                val by = when (event.key) {
                    Key.DirectionLeft -> -1
                    Key.DirectionRight -> 1
                    else -> null
                }
                if (by != null && event.type == KeyEventType.KeyDown) onMove(by)
                by != null
            }
            .focusable(),
    ) {
        AsyncImage(
            model = remember(photo.id) { fullScreenRequest(context, photo.id) },
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
            onError = { failed = true },
        )
        if (failed) {
            Text(
                stringResource(R.string.photo_failed),
                color = colors.onSurfaceVariant,
                fontSize = 22.sp,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (photo.isVideo) {
            VideoBadge(Modifier.align(Alignment.BottomStart).padding(start = BadgeSide, bottom = BadgeEdge))
        }
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun PhotoViewerVideoPreview() {
    ImmichTvTheme { PhotoViewerContent(Photo(PhotoId("v1"), isVideo = true)) {} }
}
