package io.github.yasarmahomedabbas.immichtv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/** Keeps the screen from sleeping while [awake], and lets it sleep again when this leaves. */
@Composable
internal fun KeepScreenOn(awake: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, awake) {
        view.keepScreenOn = awake
        onDispose { view.keepScreenOn = false }
    }
}
