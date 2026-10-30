package io.github.yasarmahomedabbas.immichtv.ui.slideshow

import android.content.Context
import android.view.TextureView
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ErrorResult
import coil3.request.SuccessResult
import io.github.yasarmahomedabbas.immichtv.R
import io.github.yasarmahomedabbas.immichtv.domain.Problem
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.domain.albums.Video
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.Place
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.Slide
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.SlideshowSettings
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.VideoMode
import io.github.yasarmahomedabbas.immichtv.ui.components.Clock
import io.github.yasarmahomedabbas.immichtv.ui.components.ClockStyle
import io.github.yasarmahomedabbas.immichtv.ui.components.EmptyState
import io.github.yasarmahomedabbas.immichtv.ui.components.FailedState
import io.github.yasarmahomedabbas.immichtv.ui.components.OverPhotoPanel
import io.github.yasarmahomedabbas.immichtv.ui.components.OverPhotoStyle
import io.github.yasarmahomedabbas.immichtv.ui.components.SkeletonBlock
import io.github.yasarmahomedabbas.immichtv.ui.components.fullScreenRequest
import io.github.yasarmahomedabbas.immichtv.ui.theme.ImmichTvTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate

/** What a show plays, for the words that name it. */
enum class SlideshowSource(@StringRes val back: Int, @StringRes val emptyHint: Int, @StringRes val emptyAction: Int) {
    Album(R.string.slideshow_back, R.string.slideshow_empty_hint, R.string.slideshow_empty_action),
    Library(R.string.slideshow_back_photos, R.string.slideshow_empty_hint_library, R.string.slideshow_empty_action_library),
}

private const val FADE_MS = 1_500
private const val PROGRESS_MS = 600
private const val BACKDROP_DARKENING = 0.42f
private val BackdropOverflow = 40.dp
private val BackdropBlur = 30.dp
private val ProgressHeight = 5.dp
private val OverlaySide = 96.dp
private val OverlayEdge = 54.dp
private val PlayButton = 76.dp
private val FocusGap = 4.dp
private val FocusWidth = 4.dp

/**
 * The full-screen slideshow: the album's photos crossfading, each moving slowly, and its videos
 * playing to their end, with the date and place, a thin progress bar and controls that show on a key
 * press. The remote works the show directly, so the controls are indicators and nothing in them takes
 * focus. [source] is what Back returns to, which its words name.
 */
@Composable
fun SlideshowScreen(
    viewModel: SlideshowViewModel,
    settings: SlideshowSettings,
    newPlayer: (Context) -> Player,
    onBack: () -> Unit,
    source: SlideshowSource = SlideshowSource.Album,
) {
    val state by viewModel.state.collectAsState()
    RunWhileStarted(viewModel)
    KeepScreenOn(state.load == SlideshowLoad.Playing && !state.paused)
    SlideshowContent(
        state = state,
        settings = settings,
        source = source,
        onBack = onBack,
        onRetry = viewModel::retry,
        onAction = { action ->
            when (action) {
                SlideshowAction.Back -> viewModel.skipBack()
                SlideshowAction.Forward -> viewModel.skipForward()
                SlideshowAction.TogglePause -> viewModel.togglePause()
                SlideshowAction.ShowControls -> viewModel.showControls()
            }
        },
        onImageReady = viewModel::imageReady,
        onImageFailed = viewModel::imageFailed,
        newPlayer = newPlayer,
        onVideoPlaying = viewModel::videoPlaying,
        onVideoWaiting = viewModel::videoWaiting,
        onVideoEnded = viewModel::videoEnded,
        onVideoFailed = viewModel::videoFailed,
    )
}

/** Runs the timer from start to stop, so it ends with the screen and when the app is left. */
@Composable
private fun RunWhileStarted(viewModel: SlideshowViewModel) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    DisposableEffect(lifecycle, viewModel) {
        var timer: Job? = null
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> timer = scope.launch { viewModel.play() }
                Lifecycle.Event.ON_STOP -> timer?.cancel()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            timer?.cancel()
        }
    }
}

@Composable
private fun KeepScreenOn(awake: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, awake) {
        view.keepScreenOn = awake
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun SlideshowContent(
    state: SlideshowState,
    settings: SlideshowSettings,
    source: SlideshowSource,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAction: (SlideshowAction) -> Unit,
    onImageReady: (PhotoId) -> Unit,
    onImageFailed: (PhotoId) -> Unit,
    newPlayer: (Context) -> Player,
    onVideoPlaying: (PhotoId) -> Unit,
    onVideoWaiting: (PhotoId) -> Unit,
    onVideoEnded: (PhotoId) -> Unit,
    onVideoFailed: (PhotoId, Boolean) -> Unit,
) {
    val rootFocus = remember { FocusRequester() }
    val load = state.load
    // Takes focus back when a state card's button, which held it, has gone.
    LaunchedEffect(load) {
        if (load == SlideshowLoad.Loading || load == SlideshowLoad.Playing) runCatching { rootFocus.requestFocus() }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .focusRequester(rootFocus)
            .onPreviewKeyEvent { event ->
                // Only while playing: a state card's button needs its OK.
                val action = if (load == SlideshowLoad.Playing && event.type == KeyEventType.KeyDown) actionFor(event.key) else null
                if (action != null && (action.whenHeld || event.nativeKeyEvent.repeatCount == 0)) onAction(action)
                action != null
            }
            .focusable(),
    ) {
        when (load) {
            SlideshowLoad.Loading -> SkeletonBlock(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = OverlaySide, bottom = OverlayEdge)
                    .size(420.dp, 108.dp),
                corner = 18.dp,
            )
            SlideshowLoad.Empty -> EmptyState(
                title = stringResource(R.string.slideshow_empty_title),
                hint = stringResource(source.emptyHint),
                actionLabel = stringResource(source.emptyAction),
                onAction = onBack,
            )
            is SlideshowLoad.Failed -> FailedState(load.problem, onRetry)
            SlideshowLoad.Playing -> Playing(
                state,
                settings,
                source,
                onImageReady,
                onImageFailed,
                newPlayer,
                onVideoPlaying,
                onVideoWaiting,
                onVideoEnded,
                onVideoFailed,
            )
        }
    }
}

@Composable
private fun Playing(
    state: SlideshowState,
    settings: SlideshowSettings,
    source: SlideshowSource,
    onImageReady: (PhotoId) -> Unit,
    onImageFailed: (PhotoId) -> Unit,
    newPlayer: (Context) -> Player,
    onVideoPlaying: (PhotoId) -> Unit,
    onVideoWaiting: (PhotoId) -> Unit,
    onVideoEnded: (PhotoId) -> Unit,
    onVideoFailed: (PhotoId, Boolean) -> Unit,
) {
    Preload(state.current, onImageReady, onImageFailed)
    Preload(state.next, onImageReady, onImageFailed)
    Preload(state.previous, onImageReady, onImageFailed)
    Stage(state, settings, newPlayer, onVideoPlaying, onVideoWaiting, onVideoEnded, onVideoFailed)
    Progress(state)
    Box(Modifier.fillMaxSize()) {
        OverPhotoPanel(
            OverPhotoStyle.Pill,
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = OverlayEdge, end = OverlaySide),
        ) { Box(Modifier.padding(horizontal = 24.dp, vertical = 10.dp)) { Clock(ClockStyle.OverPhoto) } }
        if (settings.showDateAndPlace) state.current?.let { DateAndPlace(it, Modifier.align(Alignment.BottomStart)) }
        AnimatedVisibility(
            visible = state.controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = OverlayEdge),
        ) { Controls(state, source) }
    }
}

/** Keeps [slide]'s image in Coil's cache and tells the show when it is there, or never will be. */
@Composable
private fun Preload(slide: Slide?, onReady: (PhotoId) -> Unit, onFailed: (PhotoId) -> Unit) {
    val id = slide?.photo?.id ?: return
    val context = LocalContext.current
    val ready by rememberUpdatedState(onReady)
    val failed by rememberUpdatedState(onFailed)
    LaunchedEffect(id) {
        when (context.imageLoader.execute(fullScreenRequest(context, id))) {
            is SuccessResult -> ready(id)
            is ErrorResult -> failed(id)
        }
    }
}

/**
 * At most two slides are composed: the one on screen and the one fading in over it. A new slide
 * waits for its image while the old one stays; once it has faded in it is the one shown, and only then
 * does a video start over its preview, so a held right key starts no loads.
 */
@Composable
private fun Stage(
    state: SlideshowState,
    settings: SlideshowSettings,
    newPlayer: (Context) -> Player,
    onVideoPlaying: (PhotoId) -> Unit,
    onVideoWaiting: (PhotoId) -> Unit,
    onVideoEnded: (PhotoId) -> Unit,
    onVideoFailed: (PhotoId, Boolean) -> Unit,
) {
    val current = state.current ?: return
    var layers by remember { mutableStateOf(StageLayers()) }
    LaunchedEffect(current.photo.id) { layers = layers.movedTo(current) }
    val photoMs = settings.secondsPerPhoto * 1_000 + FADE_MS
    for (slide in listOfNotNull(layers.under(current), current)) {
        key(slide.photo.id) {
            SlideLayer(
                slide = slide,
                index = state.index,
                incoming = slide.photo.id == current.photo.id && layers.shown?.photo?.id != current.photo.id,
                paused = state.paused,
                motionMs = photoMs,
                onLoaded = { layers = layers.loaded(slide) },
                onFadedIn = { layers = layers.fadedIn(slide) },
            )
        }
    }
    // A tool's preview has no player to ask for: it shows the video's still.
    if (current.photo.isVideo && !LocalInspectionMode.current) {
        VideoLayer(
            slide = current,
            run = state.run,
            showing = layers.shown?.photo?.id == current.photo.id,
            paused = state.paused,
            newPlayer = newPlayer,
            sound = settings.videos == VideoMode.PlayWithSound,
            onPlaying = onVideoPlaying,
            onWaiting = onVideoWaiting,
            onEnded = onVideoEnded,
            onFailed = onVideoFailed,
        )
    }
}

/**
 * The current video, over its preview. The one player lasts while videos follow each other and goes
 * with the last of them, so a photo holds none. It only streams while [showing], and each report names
 * the video the player has, so one that arrives after a move is ignored by the show. The picture stays
 * invisible until the video's first frame: until then the preview shows through, and the view would
 * otherwise keep the last video's last frame.
 */
@Composable
private fun VideoLayer(
    slide: Slide,
    run: Int,
    showing: Boolean,
    paused: Boolean,
    newPlayer: (Context) -> Player,
    sound: Boolean,
    onPlaying: (PhotoId) -> Unit,
    onWaiting: (PhotoId) -> Unit,
    onEnded: (PhotoId) -> Unit,
    onFailed: (PhotoId, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val player = remember { newPlayer(context) }
    var size by remember { mutableStateOf<VideoSize?>(null) }
    var firstFrame by remember { mutableStateOf(false) }
    val playing by rememberUpdatedState(onPlaying)
    val waiting by rememberUpdatedState(onWaiting)
    val ended by rememberUpdatedState(onEnded)
    val failed by rememberUpdatedState(onFailed)

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            private val id get() = player.currentMediaItem?.mediaId?.let(::PhotoId)

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) id?.let(playing)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> id?.let(waiting)
                    Player.STATE_ENDED -> id?.let(ended)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                id?.let { failed(it, isServerFailure(error.errorCode)) }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                size = videoSize
            }

            override fun onRenderedFirstFrame() {
                size = player.videoSize
                firstFrame = true
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(player, sound) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, !sound)
            .build()
        if (sound) {
            val movie = AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build()
            player.setAudioAttributes(movie, /* handleAudioFocus = */ true)
        }
    }
    // Leaving the app pauses it, as a pause does.
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    LaunchedEffect(player, paused, lifecycle) {
        player.playWhenReady = !paused && lifecycle.isAtLeast(Lifecycle.State.STARTED)
    }
    LaunchedEffect(player, slide.photo.id, run, showing) {
        firstFrame = false
        if (showing) {
            player.setMediaItem(MediaItem.Builder().setMediaId(Video(slide.photo.id).photo.value).build())
            player.prepare()
        } else {
            player.stop()
            player.clearMediaItems()
        }
    }

    val ratio = size?.takeIf { it.width > 0 && it.height > 0 }?.let { it.width * it.pixelWidthHeightRatio / it.height }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { viewContext ->
                TextureView(viewContext).apply {
                    isFocusable = false
                    isOpaque = false
                    alpha = 0f
                    player.setVideoTextureView(this)
                }
            },
            update = { it.alpha = if (firstFrame) 1f else 0f },
            modifier = if (ratio == null) {
                Modifier.fillMaxSize()
            } else {
                // Fitted, never cropped: height first for portrait, so it can't run off the screen.
                Modifier.aspectRatio(ratio, matchHeightConstraintsFirst = ratio < 1f)
            },
        )
    }
}

@Composable
private fun SlideLayer(
    slide: Slide,
    index: Int,
    incoming: Boolean,
    paused: Boolean,
    motionMs: Int,
    onLoaded: () -> Unit,
    onFadedIn: () -> Unit,
) {
    val context = LocalContext.current
    val id = slide.photo.id
    val request = remember(id) { fullScreenRequest(context, id) }
    val motion = remember { slideMotion(index) }
    val alpha = remember { Animatable(if (incoming) 0f else 1f) }
    val progress = remember { Animatable(0f) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(incoming, loaded) {
        when {
            !incoming -> alpha.snapTo(1f)
            loaded -> {
                alpha.animateTo(1f, tween(FADE_MS, easing = LinearEasing))
                onFadedIn()
            }
        }
    }
    // Stopped by a pause where it is, then given what was left; at its end it just stays.
    LaunchedEffect(loaded, paused) {
        if (loaded && !paused && !slide.photo.isVideo) {
            val left = ((1f - progress.value) * motionMs).toInt()
            progress.animateTo(1f, tween(left, easing = LinearEasing))
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value }) {
        val moving = Modifier.fillMaxSize().graphicsLayer {
            val scale = 1f + (motion.endScale - 1f) * progress.value
            scaleX = scale
            scaleY = scale
            translationX = motion.endX * size.width * progress.value
            translationY = motion.endY * size.height * progress.value
        }
        if (slide.portrait) {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.Center)
                    .requiredSize(maxWidth + BackdropOverflow * 2, maxHeight + BackdropOverflow * 2)
                    .blur(BackdropBlur),
            )
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = BACKDROP_DARKENING)))
        }
        AsyncImage(
            model = request,
            contentDescription = null,
            // A video's still lies under the fitted video, so it is fitted too.
            contentScale = if (slide.portrait || slide.photo.isVideo) ContentScale.Fit else ContentScale.Crop,
            modifier = moving,
            onSuccess = {
                loaded = true
                onLoaded()
            },
        )
    }
}

@Composable
private fun Progress(state: SlideshowState) {
    val colors = MaterialTheme.colorScheme
    val share = if (state.slides.isEmpty()) 0f else state.position.toFloat() / state.slides.size
    val width by animateFloatAsState(share, tween(PROGRESS_MS), label = "progress")
    Box(
        Modifier
            .fillMaxWidth()
            .height(ProgressHeight)
            .background(colors.onSurface.copy(alpha = 0.18f)),
    ) {
        // Off-white, not the canvas's amber: amber is only for focus (rule 3).
        Box(Modifier.fillMaxHeight().fillMaxWidth(width).background(colors.onSurface))
    }
}

@Composable
private fun DateAndPlace(slide: Slide, modifier: Modifier) {
    val (date, place) = dateAndPlace(slide)
    if (date == null && place == null) return
    val colors = MaterialTheme.colorScheme
    OverPhotoPanel(OverPhotoStyle.Info, modifier.padding(start = OverlaySide, bottom = OverlayEdge)) {
        Column(Modifier.padding(horizontal = 28.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (date != null) Text(date, color = colors.onSurface, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            if (place != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(painterResource(R.drawable.ic_place), contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(24.dp))
                    Text(place, color = colors.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/** Shows what the remote does; the keys work whether this is showing or not. */
@Composable
private fun Controls(state: SlideshowState, source: SlideshowSource) {
    val colors = MaterialTheme.colorScheme
    OverPhotoPanel(OverPhotoStyle.Controls) {
        Row(
            Modifier.padding(horizontal = 36.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Text(stringResource(source.back), color = colors.onSurfaceVariant, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Icon(painterResource(R.drawable.ic_previous), stringResource(R.string.slideshow_previous), tint = colors.onSurface, modifier = Modifier.size(44.dp))
            Box(
                Modifier
                    .border(FocusWidth, colors.border, CircleShape)
                    .padding(FocusWidth + FocusGap),
            ) {
                Box(Modifier.size(PlayButton).background(colors.onSurface, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(if (state.paused) R.drawable.ic_play else R.drawable.ic_pause),
                        stringResource(if (state.paused) R.string.slideshow_play else R.string.slideshow_pause),
                        tint = colors.onPrimary,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
            Icon(painterResource(R.drawable.ic_next), stringResource(R.string.slideshow_next), tint = colors.onSurface, modifier = Modifier.size(44.dp))
            Text(
                stringResource(R.string.slideshow_position, state.position, state.slides.size),
                color = colors.onSurface,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun previewSlide(id: Int, portrait: Boolean = false, place: Place? = Place("Lisbon", "Portugal"), isVideo: Boolean = false) =
    Slide(Photo(PhotoId("p$id"), isVideo), portrait, LocalDate.of(2025, 8, 23), place)

private val previewSlides = List(214) { previewSlide(it) }

@Composable
private fun SlideshowPreview(state: SlideshowState, settings: SlideshowSettings = SlideshowSettings()) {
    // Stage composes no video layer in a preview, so nothing asks for this player.
    ImmichTvTheme {
        SlideshowContent(
            state,
            settings,
            SlideshowSource.Album,
            {},
            {},
            {},
            {},
            {},
            { error("No player in a preview") },
            {},
            {},
            {},
            { _, _ -> },
        )
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun SlideshowLandscapePreview() =
    SlideshowPreview(SlideshowState(SlideshowLoad.Playing, previewSlides, index = 36))

@Preview(device = Devices.TV_1080p)
@Composable
private fun SlideshowPortraitPreview() =
    SlideshowPreview(SlideshowState(SlideshowLoad.Playing, List(3) { previewSlide(it, portrait = true) }))

@Preview(device = Devices.TV_1080p)
@Composable
private fun SlideshowVideoPreview() =
    SlideshowPreview(SlideshowState(SlideshowLoad.Playing, List(3) { previewSlide(it, isVideo = true) }))

@Preview(device = Devices.TV_1080p)
@Composable
private fun SlideshowPausedPreview() =
    SlideshowPreview(SlideshowState(SlideshowLoad.Playing, previewSlides, index = 36, paused = true, controlsVisible = true))

@Preview(device = Devices.TV_1080p)
@Composable
private fun SlideshowNoPlacePreview() =
    SlideshowPreview(SlideshowState(SlideshowLoad.Playing, List(3) { previewSlide(it, place = null) }))

@Preview(device = Devices.TV_1080p)
@Composable
private fun SlideshowLoadingPreview() = SlideshowPreview(SlideshowState())

@Preview(device = Devices.TV_1080p)
@Composable
private fun SlideshowEmptyPreview() = SlideshowPreview(SlideshowState(SlideshowLoad.Empty))

@Preview(device = Devices.TV_1080p)
@Composable
private fun SlideshowFailedPreview() = SlideshowPreview(SlideshowState(SlideshowLoad.Failed(Problem.Unreachable)))
