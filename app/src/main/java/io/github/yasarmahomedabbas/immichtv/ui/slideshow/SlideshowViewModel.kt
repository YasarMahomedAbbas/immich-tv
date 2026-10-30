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
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.LoadSlideshow
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.Slide
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

    /** The album has no photos to play. */
    data object Empty : SlideshowLoad
    data class Failed(val problem: Problem) : SlideshowLoad
    data object Playing : SlideshowLoad
}

/** The slideshow. [next] and [previous] wrap, so one photo is its own neighbour. */
data class SlideshowState(
    val load: SlideshowLoad = SlideshowLoad.Loading,
    val slides: List<Slide> = emptyList(),
    val index: Int = 0,
    val paused: Boolean = false,
    val controlsVisible: Boolean = false,
) {
    val current: Slide? get() = slides.getOrNull(index)
    val next: Slide? get() = if (slides.isEmpty()) null else slides[(index + 1) % slides.size]
    val previous: Slide? get() = if (slides.isEmpty()) null else slides[(index - 1 + slides.size) % slides.size]

    /** Which photo this is, counted from 1. */
    val position: Int get() = index + 1
}

/**
 * What the slideshow does over time. It knows nothing of Android: the screen runs [play] while it
 * is started, and reports each image the loader got ([imageReady]) or couldn't ([imageFailed]).
 */
class SlideshowViewModel(
    private val album: Album,
    private val settings: SlideshowSettings,
    private val loadSlideshow: LoadSlideshow,
) : ViewModel() {
    private val _state = MutableStateFlow(SlideshowState())
    val state: StateFlow<SlideshowState> = _state.asStateFlow()

    private var elapsedMs = 0L
    private var failuresInARow = 0
    private val ready = LinkedHashSet<PhotoId>()
    private var hideControls: Job? = null

    init {
        load()
    }

    /**
     * The timer: runs until cancelled, so the show stops with the screen and when the app is left.
     * A photo's time starts once it can be seen, and at the end of it the show waits for the next.
     */
    suspend fun play() {
        val photoMs = settings.secondsPerPhoto * MS_PER_SECOND
        while (true) {
            delay(TICK_MS)
            val state = _state.value
            if (state.load != SlideshowLoad.Playing || state.paused) continue
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

    fun imageReady(photo: PhotoId) {
        failuresInARow = 0
        ready -= photo
        ready += photo
        if (ready.size > READY_KEPT) ready -= ready.first()
    }

    /** Takes the photo out of the show, keeping [SlideshowState.index] on the same slide. */
    fun imageFailed(photo: PhotoId) {
        val state = _state.value
        val at = state.slides.indexOfFirst { it.photo.id == photo }
        if (at < 0) return
        failuresInARow++
        val slides = state.slides - state.slides[at]
        if (slides.isEmpty() || failuresInARow >= MAX_FAILURES) {
            _state.update { it.copy(load = SlideshowLoad.Failed(Problem.Unreachable), controlsVisible = false) }
            return
        }
        if (at == state.index) elapsedMs = 0
        val index = when {
            at < state.index -> state.index - 1
            state.index >= slides.size -> 0
            else -> state.index
        }
        _state.update { it.copy(slides = slides, index = index) }
    }

    fun retry() {
        if (_state.value.load == SlideshowLoad.Loading) return
        load()
    }

    private fun skip(by: Int) {
        if (_state.value.load != SlideshowLoad.Playing) return
        moveBy(by)
        showControls()
    }

    private fun moveBy(by: Int) {
        elapsedMs = 0
        _state.update { it.copy(index = (it.index + by + it.slides.size) % it.slides.size) }
    }

    private fun load() {
        hideControls?.cancel()
        elapsedMs = 0
        failuresInARow = 0
        ready.clear()
        _state.value = SlideshowState()
        viewModelScope.launch {
            _state.value = when (val result = loadSlideshow(album, settings.order)) {
                is ServerResult.Ok -> SlideshowState(
                    load = if (result.value.isEmpty()) SlideshowLoad.Empty else SlideshowLoad.Playing,
                    slides = result.value,
                )
                is ServerResult.Failed -> SlideshowState(load = SlideshowLoad.Failed(result.problem))
            }
        }
    }

    companion object {
        const val CONTROLS_MS = 4_000L
        const val MAX_FAILURES = 5
        private const val TICK_MS = 100L
        private const val MS_PER_SECOND = 1_000L

        /** Previous, current and next, with room for skipping back and forth over them. */
        private const val READY_KEPT = 8

        fun factory(container: AppContainer, album: Album, settings: SlideshowSettings) = viewModelFactory {
            initializer { SlideshowViewModel(album, settings, container.loadSlideshow) }
        }
    }
}

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

internal enum class SlideshowAction { Back, Forward, TogglePause, ShowControls }

/** What a key on the remote does while the slideshow plays; null leaves it alone (Back, for one). */
internal fun actionFor(key: Key): SlideshowAction? = when (key) {
    Key.DirectionLeft -> SlideshowAction.Back
    Key.DirectionRight -> SlideshowAction.Forward
    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause ->
        SlideshowAction.TogglePause
    Key.DirectionUp, Key.DirectionDown -> SlideshowAction.ShowControls
    else -> null
}
