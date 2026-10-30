package io.github.yasarmahomedabbas.immichtv.ui.slideshow

import androidx.compose.ui.input.key.Key
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.yasarmahomedabbas.immichtv.AppContainer
import io.github.yasarmahomedabbas.immichtv.domain.Problem
import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import io.github.yasarmahomedabbas.immichtv.domain.albums.Album
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.domain.map
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.Playlist
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.Slide
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.SlideshowOrder
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.SlideshowSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed interface SlideshowLoad {
    data object Loading : SlideshowLoad

    /** There are no photos to play. */
    data object Empty : SlideshowLoad
    data class Failed(val problem: Problem) : SlideshowLoad
    data object Playing : SlideshowLoad
}

/**
 * The slideshow. [next] and [previous] wrap, so one photo is its own neighbour.
 *
 * @property run goes up on every move, so a sole video, its own next, plays again.
 */
data class SlideshowState(
    val load: SlideshowLoad = SlideshowLoad.Loading,
    val slides: List<Slide> = emptyList(),
    val index: Int = 0,
    val paused: Boolean = false,
    val controlsVisible: Boolean = false,
    val run: Int = 0,
) {
    val current: Slide? get() = slides.getOrNull(index)
    val next: Slide? get() = if (slides.isEmpty()) null else slides[(index + 1) % slides.size]
    val previous: Slide? get() = if (slides.isEmpty()) null else slides[(index - 1 + slides.size) % slides.size]

    /** Which photo this is, counted from 1. */
    val position: Int get() = index + 1
}

/**
 * What the slideshow does over time. It knows nothing of Android: the screen runs [play] while it
 * is started, and reports each image the loader got ([imageReady]) or couldn't ([imageFailed]), and
 * what the current video's player does ([videoPlaying], [videoWaiting], [videoEnded], [videoFailed]).
 */
class SlideshowViewModel(
    private val settings: SlideshowSettings,
    private val loadPlaylist: suspend () -> ServerResult<Playlist>,
) : ViewModel() {
    private val _state = MutableStateFlow(SlideshowState())
    val state: StateFlow<SlideshowState> = _state.asStateFlow()

    private var elapsedMs = 0L
    private var failuresInARow = 0
    private val ready = LinkedHashSet<PhotoId>()
    private var hideControls: Job? = null
    private var videoEnded = false
    private var videoMoving = false
    private var videoWaitMs = 0L

    init {
        load()
    }

    /**
     * The timer: runs until cancelled, so the show stops with the screen and when the app is left.
     * A photo's time starts once it can be seen, and at the end of it the show waits for the next.
     * A video has no time of its own: it goes when it ends, or fails after [VIDEO_STALL_MS] standing still.
     */
    suspend fun play() {
        val photoMs = settings.secondsPerPhoto * MS_PER_SECOND
        while (true) {
            delay(TICK_MS)
            val state = _state.value
            if (state.load != SlideshowLoad.Playing || state.paused) continue
            if (state.current?.photo?.isVideo == true) {
                videoTick(state)
                continue
            }
            if (state.current?.photo?.id !in ready) continue
            if (elapsedMs < photoMs) elapsedMs += TICK_MS
            if (elapsedMs >= photoMs && state.next?.photo?.id in ready) {
                moveBy(1)
            }
        }
    }

    fun skipForward() = skip(1)

    fun skipBack() = skip(-1)

    fun togglePause() {
        if (_state.value.load != SlideshowLoad.Playing) return
        _state.update { it.copy(paused = !it.paused) }
        showControls()
    }

    /** Shows the controls and restarts the time until they fade, also while paused. */
    fun showControls() {
        if (_state.value.load != SlideshowLoad.Playing) return
        _state.update { it.copy(controlsVisible = true) }
        hideControls?.cancel()
        hideControls = viewModelScope.launch {
            delay(CONTROLS_MS)
            _state.update { it.copy(controlsVisible = false) }
        }
    }

    /** A video's preview says nothing of its stream, so only a photo's image shows the server is there. */
    fun imageReady(photo: PhotoId) {
        val isVideo = _state.value.slides.firstOrNull { it.photo.id == photo }?.photo?.isVideo == true
        if (!isVideo) failuresInARow = 0
        ready -= photo
        ready += photo
        if (ready.size > READY_KEPT) ready -= ready.first()
    }

    fun imageFailed(photo: PhotoId) = drop(photo, counts = true)

    /** The video's frames are moving, so the server is there. */
    fun videoPlaying(photo: PhotoId) {
        if (!isCurrent(photo)) return
        videoMoving = true
        videoWaitMs = 0
        failuresInARow = 0
    }

    fun videoWaiting(photo: PhotoId) {
        if (isCurrent(photo)) videoMoving = false
    }

    fun videoEnded(photo: PhotoId) {
        if (!isCurrent(photo)) return
        videoEnded = true
        videoMoving = false
    }

    /** Only a failure to reach the server counts towards [MAX_FAILURES]: this TV not decoding a video says nothing of it. */
    fun videoFailed(photo: PhotoId, serverFailure: Boolean) {
        if (isCurrent(photo)) drop(photo, counts = serverFailure)
    }

    fun retry() {
        if (_state.value.load == SlideshowLoad.Loading) return
        load()
    }

    private fun isCurrent(photo: PhotoId) = _state.value.current?.photo?.id == photo

    private fun videoTick(state: SlideshowState) {
        val id = state.current?.photo?.id ?: return
        when {
            // The preview isn't up, so the video hasn't started.
            id !in ready -> Unit
            videoEnded -> if (state.next?.photo?.id in ready) moveBy(1)
            !videoMoving -> {
                videoWaitMs += TICK_MS
                if (videoWaitMs >= VIDEO_STALL_MS) videoFailed(id, serverFailure = true)
            }
        }
    }

    /** Takes the item out of the show, keeping [SlideshowState.index] on the same slide. */
    private fun drop(photo: PhotoId, counts: Boolean) {
        val state = _state.value
        val at = state.slides.indexOfFirst { it.photo.id == photo }
        if (at < 0) return
        if (counts) failuresInARow++
        val slides = state.slides - state.slides[at]
        if (slides.isEmpty() && !counts) {
            _state.update { it.copy(load = SlideshowLoad.Empty, controlsVisible = false) }
            return
        }
        if (slides.isEmpty() || failuresInARow >= MAX_FAILURES) {
            _state.update { it.copy(load = SlideshowLoad.Failed(Problem.Unreachable), controlsVisible = false) }
            return
        }
        if (at == state.index) {
            elapsedMs = 0
            resetVideo()
        }
        val index = when {
            at < state.index -> state.index - 1
            state.index >= slides.size -> 0
            else -> state.index
        }
        _state.update { it.copy(slides = slides, index = index) }
    }

    private fun resetVideo() {
        videoEnded = false
        videoMoving = false
        videoWaitMs = 0
    }

    private fun skip(by: Int) {
        if (_state.value.load != SlideshowLoad.Playing) return
        moveBy(by)
        showControls()
    }

    private fun moveBy(by: Int) {
        elapsedMs = 0
        resetVideo()
        _state.update { it.copy(index = (it.index + by + it.slides.size) % it.slides.size, run = it.run + 1) }
    }

    private fun load() {
        hideControls?.cancel()
        elapsedMs = 0
        resetVideo()
        failuresInARow = 0
        ready.clear()
        _state.value = SlideshowState()
        viewModelScope.launch {
            _state.value = when (val result = loadPlaylist()) {
                is ServerResult.Ok -> SlideshowState(
                    load = if (result.value.slides.isEmpty()) SlideshowLoad.Empty else SlideshowLoad.Playing,
                    slides = result.value.slides,
                    index = result.value.start,
                )
                is ServerResult.Failed -> SlideshowState(load = SlideshowLoad.Failed(result.problem))
            }
        }
    }

    companion object {
        const val CONTROLS_MS = 4_000L
        const val MAX_FAILURES = 5

        /** How long a video may stand still, before its first frame or mid-way, before it counts as failed. */
        const val VIDEO_STALL_MS = 30_000L
        private const val TICK_MS = 100L
        private const val MS_PER_SECOND = 1_000L

        /** Previous, current and next, with room for skipping back and forth over them. */
        private const val READY_KEPT = 8

        fun factory(container: AppContainer, album: Album, settings: SlideshowSettings) = viewModelFactory {
            initializer {
                SlideshowViewModel(settings) { container.loadSlideshow(album, settings.order, settings.videos).map { Playlist(it) } }
            }
        }

        /** The whole library from the photo [from] onward, with [TimelineSettings]. */
        fun fromTimeline(container: AppContainer, from: PhotoId) = viewModelFactory {
            initializer {
                SlideshowViewModel(TimelineSettings) { container.loadLibrarySlideshow(from) }
            }
        }
    }
}

/** A library show can't be set up: the defaults (8 s, date and place on), newest to oldest. */
val TimelineSettings = SlideshowSettings(order = SlideshowOrder.NewestFirst)

/** The date ("23 August 2025") and place ("Lisbon, Portugal") to show, each null when unknown. */
internal fun dateAndPlace(slide: Slide, locale: Locale = Locale.getDefault()): Pair<String?, String?> {
    val date = slide.takenOn?.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))
    val place = slide.place?.let { listOfNotNull(it.city, it.country).joinToString(", ") }
    return date to place
}

/** Where a slide's slow move ends: its scale, and its drift as a share of the screen. */
internal data class SlideMotion(val endScale: Float, val endX: Float, val endY: Float)

private const val MOTION_SCALE = 1.08f
private const val MOTION_DRIFT = 0.01f
private const val MOTION_DIRECTIONS = 4

/** The slow zoom and pan for the slide at [index]: one of four diagonals, so neighbours differ. */
internal fun slideMotion(index: Int): SlideMotion = when (index.mod(MOTION_DIRECTIONS)) {
    0 -> SlideMotion(MOTION_SCALE, MOTION_DRIFT, MOTION_DRIFT)
    1 -> SlideMotion(MOTION_SCALE, -MOTION_DRIFT, -MOTION_DRIFT)
    2 -> SlideMotion(MOTION_SCALE, -MOTION_DRIFT, MOTION_DRIFT)
    else -> SlideMotion(MOTION_SCALE, MOTION_DRIFT, -MOTION_DRIFT)
}

/**
 * Which slides the stage draws. [shown] has fully faded in; [lastLoaded] is the newest with an image,
 * which may still be fading in. Only [lastLoaded] and [shown] can ever be under the current slide.
 */
internal data class StageLayers(val shown: Slide? = null, val lastLoaded: Slide? = null) {
    /** What stays under [current] while it loads and fades in; null once it covers everything. */
    fun under(current: Slide): Slide? =
        (lastLoaded?.takeIf { !it.sameAs(current) } ?: shown)?.takeIf { !it.sameAs(current) }

    /** A move during a fade: the slide that was fading in, if it could be seen, is now the one under. */
    fun movedTo(current: Slide): StageLayers =
        lastLoaded?.takeIf { !it.sameAs(current) }?.let { copy(shown = it) } ?: this

    fun loaded(slide: Slide): StageLayers = copy(lastLoaded = slide)

    /**
     * Also the newest loaded: a slide skipped back to already has its image and never loads again, and
     * the one it replaced must not come back from under it at the next move.
     */
    fun fadedIn(slide: Slide): StageLayers = copy(shown = slide, lastLoaded = slide)

    private fun Slide.sameAs(other: Slide) = photo.id == other.photo.id
}

/** Whether a player's error means the server couldn't be reached: Media3's `ERROR_CODE_IO_*` range. */
internal fun isServerFailure(errorCode: Int): Boolean = errorCode in 2000..2999

/**
 * [whenHeld]: whether a held key's repeats count. A held OK would otherwise flip the pause on and off,
 * and holding OK on a tile is what starts a library show: the rest of that hold's repeats arrive here.
 */
internal enum class SlideshowAction(val whenHeld: Boolean = true) { Back, Forward, TogglePause(whenHeld = false), ShowControls }

/** What a key on the remote does while the slideshow plays; null leaves it alone (Back, for one). */
internal fun actionFor(key: Key): SlideshowAction? = when (key) {
    Key.DirectionLeft -> SlideshowAction.Back
    Key.DirectionRight -> SlideshowAction.Forward
    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause ->
        SlideshowAction.TogglePause
    Key.DirectionUp, Key.DirectionDown -> SlideshowAction.ShowControls
    else -> null
}
