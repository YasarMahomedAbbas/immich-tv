package io.github.yasarmahomedabbas.immichtv.ui.screensaver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.MaterialTheme
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.Place
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.ScreensaverShow
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.Slide
import io.github.yasarmahomedabbas.immichtv.domain.Problem
import io.github.yasarmahomedabbas.immichtv.ui.components.Clock
import io.github.yasarmahomedabbas.immichtv.ui.components.ClockStyle
import io.github.yasarmahomedabbas.immichtv.ui.slideshow.Playing
import io.github.yasarmahomedabbas.immichtv.ui.slideshow.RunWhileStarted
import io.github.yasarmahomedabbas.immichtv.ui.slideshow.SlideshowLoad
import io.github.yasarmahomedabbas.immichtv.ui.slideshow.SlideshowSource
import io.github.yasarmahomedabbas.immichtv.ui.slideshow.SlideshowState
import io.github.yasarmahomedabbas.immichtv.ui.slideshow.SlideshowViewModel
import io.github.yasarmahomedabbas.immichtv.ui.slideshow.retryWhileStopped
import io.github.yasarmahomedabbas.immichtv.ui.theme.ImmichTvTheme
import kotlinx.coroutines.delay
import java.time.LocalDate

private const val MS_PER_MINUTE = 60_000L

/**
 * The slideshow on an idle TV. Nothing takes focus, since any key wakes the TV, and the ground alone
 * shows while loading: a skeleton has no one to reassure here. With no photos, for any reason, it
 * shows a clock that moves each minute, and tries again each minute until the photos are back.
 */
@Composable
fun ScreensaverScreen(viewModel: SlideshowViewModel) {
    val state by viewModel.state.collectAsState()
    RunWhileStarted(viewModel)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.retryWhileStopped() }
    }
    ScreensaverContent(
        state,
        onImageReady = viewModel::imageReady,
        onImageFailed = viewModel::imageFailed,
    )
}

@Composable
private fun ScreensaverContent(state: SlideshowState, onImageReady: (PhotoId) -> Unit, onImageFailed: (PhotoId) -> Unit) {
    var wasClock by remember { mutableStateOf(false) }
    val clock = showsClock(state.load, wasClock)
    SideEffect { wasClock = clock }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            clock -> MovingClock()
            state.load == SlideshowLoad.Playing -> Playing(
                state,
                ScreensaverShow,
                SlideshowSource.Library,
                onImageReady,
                onImageFailed,
                // VideoMode.Skip: nothing in this show asks for a player or reports on a video.
                { error("A screensaver plays no videos") },
                {},
                {},
                {},
                { _, _ -> },
            )
        }
    }
}

/** The clock alone on the ground, inside the safe area, in a new place each minute. */
@Composable
private fun MovingClock() {
    var minute by remember { mutableLongStateOf(System.currentTimeMillis() / MS_PER_MINUTE) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            minute = now / MS_PER_MINUTE
            delay(MS_PER_MINUTE - now % MS_PER_MINUTE)
        }
    }
    val (x, y) = clockSpot(minute)
    Box(Modifier.fillMaxSize().padding(horizontal = 96.dp, vertical = 48.dp)) {
        // A bias runs from -1 to 1 where a spot runs from 0 to 1.
        Box(Modifier.align(BiasAlignment(x * 2 - 1, y * 2 - 1))) { Clock(ClockStyle.Screensaver) }
    }
}

@Composable
private fun ScreensaverPreview(state: SlideshowState) {
    ImmichTvTheme { ScreensaverContent(state, {}, {}) }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun ScreensaverPlayingPreview() = ScreensaverPreview(
    SlideshowState(
        SlideshowLoad.Playing,
        List(3) { Slide(Photo(PhotoId("p$it"), false), false, LocalDate.of(2025, 8, 23), Place("Lisbon", "Portugal")) },
    ),
)

@Preview(device = Devices.TV_1080p)
@Composable
private fun ScreensaverClockPreview() = ScreensaverPreview(SlideshowState(SlideshowLoad.Failed(Problem.Unreachable)))
