package io.github.yasarmahomedabbas.immichtv.ui.screensaver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.ScreensaverShow
import io.github.yasarmahomedabbas.immichtv.ui.slideshow.Playing
import io.github.yasarmahomedabbas.immichtv.ui.slideshow.RunWhileStarted
import io.github.yasarmahomedabbas.immichtv.ui.slideshow.SlideshowLoad
import io.github.yasarmahomedabbas.immichtv.ui.slideshow.SlideshowSource
import io.github.yasarmahomedabbas.immichtv.ui.slideshow.SlideshowViewModel

/**
 * The slideshow on an idle TV. Nothing takes focus, since any key wakes the TV, and the ground alone
 * shows while loading: a skeleton has no one to reassure here.
 */
@Composable
fun ScreensaverScreen(viewModel: SlideshowViewModel) {
    val state by viewModel.state.collectAsState()
    RunWhileStarted(viewModel)
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (state.load == SlideshowLoad.Playing) {
            Playing(
                state,
                ScreensaverShow,
                SlideshowSource.Library,
                viewModel::imageReady,
                viewModel::imageFailed,
                // VideoMode.Skip: nothing in this show asks for a player.
                { error("A screensaver plays no videos") },
                viewModel::videoPlaying,
                viewModel::videoWaiting,
                viewModel::videoEnded,
                viewModel::videoFailed,
            )
        }
    }
}
