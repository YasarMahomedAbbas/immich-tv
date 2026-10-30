package io.github.yasarmahomedabbas.immichtv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.LifecycleStartEffect

/**
 * Calls [onRefresh] whenever the app comes back to the screen, and also on entering the screen: a
 * lifecycle observer catches up to the current state when it is added.
 */
@Composable
fun RefreshOnStart(onRefresh: () -> Unit) {
    val refresh by rememberUpdatedState(onRefresh)
    LifecycleStartEffect(Unit) {
        refresh()
        onStopOrDispose {}
    }
}

/** Whether the item the remote was last on, if any, is no longer among those [present]. */
internal fun <T> lostFocus(lastFocused: T?, present: Collection<T>): Boolean =
    lastFocused != null && lastFocused !in present
