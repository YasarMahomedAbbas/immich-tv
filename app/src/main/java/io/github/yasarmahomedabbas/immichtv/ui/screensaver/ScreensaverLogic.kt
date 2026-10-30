package io.github.yasarmahomedabbas.immichtv.ui.screensaver

import io.github.yasarmahomedabbas.immichtv.ui.slideshow.SlideshowLoad

/**
 * Whether the screensaver shows the clock: whenever there are no photos, for any reason. While
 * loading it stays as it [wasClock], so a retry doesn't blink the clock away.
 */
internal fun showsClock(load: SlideshowLoad, wasClock: Boolean): Boolean = when (load) {
    is SlideshowLoad.Failed, SlideshowLoad.Empty -> true
    SlideshowLoad.Playing -> false
    SlideshowLoad.Loading -> wasClock
}

/** Spread over the safe area, in an order that makes each move a long one. */
private val ClockSpots = listOf(
    0.1f to 0.15f,
    0.9f to 0.85f,
    0.5f to 0.1f,
    0.15f to 0.8f,
    0.85f to 0.2f,
    0.4f to 0.9f,
    0.95f to 0.5f,
    0.05f to 0.5f,
)

/** Where the clock sits in [minute], as fractions of the safe area: a new place each minute, against burn-in. */
internal fun clockSpot(minute: Long): Pair<Float, Float> = ClockSpots[minute.mod(ClockSpots.size)]
