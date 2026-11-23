package io.github.yasarmahomedabbas.immichtv.ui.photos

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.imageLoader
import io.github.yasarmahomedabbas.immichtv.R
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.domain.albums.Video
import io.github.yasarmahomedabbas.immichtv.ui.components.KeepScreenOn
import io.github.yasarmahomedabbas.immichtv.ui.components.VideoBadge
import io.github.yasarmahomedabbas.immichtv.ui.components.VideoPicture
import io.github.yasarmahomedabbas.immichtv.ui.components.fullScreenRequest
import io.github.yasarmahomedabbas.immichtv.ui.theme.ImmichTvTheme

// The slideshow's overlay margins: this screen has no safe area either.
private val BadgeSide = 96.dp
private val BadgeEdge = 54.dp

/**
 * One photo full-screen, whole, on the page ground, with nothing over it. Left and right move through
 * the timeline, held keys too. A video plays over its still, with sound; OK pauses, resumes and
 * replays it. Back isn't handled here: it stays with the activity's handler. With no photo open
 * there is nothing to show, so it goes straight back.
 */
@Composable
fun PhotoViewerScreen(viewModel: PhotoGridViewModel, newPlayer: (Context) -> Player, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    DisposableEffect(viewModel) { onDispose(viewModel::viewerClosed) }
    val photo = state.viewing
    if (photo == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    PreloadImage(state.neighbour(1))
    PreloadImage(state.neighbour(-1))
    PhotoViewerContent(photo, newPlayer, viewModel::viewerMove)
}

/** Keeps a neighbour's image ready: with the one on screen, at most three bitmaps are asked for. */
@Composable
private fun PreloadImage(photo: Photo?) {
    val id = photo?.id ?: return
    val context = LocalContext.current
    LaunchedEffect(id) { context.imageLoader.execute(fullScreenRequest(context, id)) }
}

@Composable
internal fun PhotoViewerContent(photo: Photo, newPlayer: (Context) -> Player, onMove: (Int) -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { rootFocus.requestFocus() } }
    var failed by remember(photo.id) { mutableStateOf(false) }
    var video by remember(photo.id) { mutableStateOf(ViewerVideo(photo.id)) }
    KeepScreenOn(photo.isVideo && video.keepScreenOn)
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .focusRequester(rootFocus)
            .onPreviewKeyEvent { event ->
                when (event.key) {
                    Key.DirectionLeft, Key.DirectionRight -> {
                        if (event.type == KeyEventType.KeyDown) onMove(if (event.key == Key.DirectionLeft) -1 else 1)
                        true
                    }
                    // The tile's OK press opened this screen and its repeats and release arrive here:
                    // only a fresh press acts.
                    Key.DirectionCenter, Key.Enter -> {
                        if (photo.isVideo && event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                            video = video.toggle()
                        }
                        photo.isVideo
                    }
                    else -> false
                }
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
        // A tool's preview has no player to ask for: it shows the video's still.
        if (photo.isVideo && !LocalInspectionMode.current) {
            ViewerVideoLayer(video, newPlayer) { change -> video = change(video) }
        }
        val message = when {
            photo.isVideo && video.phase == VideoPhase.Failed -> R.string.video_failed
            failed -> R.string.photo_failed
            else -> null
        }
        if (message != null) {
            Text(
                stringResource(message),
                color = colors.onSurfaceVariant,
                fontSize = 22.sp,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (photo.isVideo && video.showBadge) {
            VideoBadge(Modifier.align(Alignment.BottomStart).padding(start = BadgeSide, bottom = BadgeEdge))
        }
    }
}

/**
 * The open video, over its still. The one player lasts while left and right pass through videos and
 * goes when a photo takes over or the viewer leaves, which stops the sound. Each report names the
 * video the player has, so one that arrives after a move changes nothing. Leaving the app pauses it.
 */
@Composable
private fun ViewerVideoLayer(
    video: ViewerVideo,
    newPlayer: (Context) -> Player,
    onReport: ((ViewerVideo) -> ViewerVideo) -> Unit,
) {
    val context = LocalContext.current
    val player = remember { newPlayer(context) }
    var size by remember { mutableStateOf<VideoSize?>(null) }
    val report by rememberUpdatedState(onReport)

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            private fun about(change: ViewerVideo.(PhotoId) -> ViewerVideo) {
                val id = player.currentMediaItem?.mediaId?.let(::PhotoId) ?: return
                report { it.change(id) }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) about(ViewerVideo::playing)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> about(ViewerVideo::buffering)
                    Player.STATE_ENDED -> about(ViewerVideo::ended)
                }
            }

            override fun onPlayerError(error: PlaybackException) = about(ViewerVideo::failed)

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                size = videoSize
            }

            override fun onRenderedFirstFrame() {
                size = player.videoSize
                about(ViewerVideo::firstFrame)
            }
        }
        player.addListener(listener)
        val movie = AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build()
        player.setAudioAttributes(movie, /* handleAudioFocus = */ true)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    LaunchedEffect(player, video.playWhenReady, lifecycle) {
        player.playWhenReady = video.playWhenReady && lifecycle.isAtLeast(Lifecycle.State.STARTED)
    }
    LaunchedEffect(player, video.id, video.run) {
        player.setMediaItem(MediaItem.Builder().setMediaId(Video(video.id).photo.value).build())
        if (video.run > 0) player.seekTo(0)
        player.prepare()
    }
    VideoPicture(player, size, visible = video.firstFrame)
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun PhotoViewerVideoPreview() {
    ImmichTvTheme { PhotoViewerContent(Photo(PhotoId("v1"), isVideo = true), newPlayer = { error("no player in a preview") }) {} }
}
