package io.github.yasarmahomedabbas.immichtv.ui.screensaver

import io.github.yasarmahomedabbas.immichtv.domain.Problem
import io.github.yasarmahomedabbas.immichtv.ui.slideshow.SlideshowLoad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreensaverLogicTest {
    @Test
    fun `no photos for any reason shows the clock`() {
        for (problem in Problem.entries) assertTrue(showsClock(SlideshowLoad.Failed(problem), wasClock = false))
        assertTrue(showsClock(SlideshowLoad.Empty, wasClock = false))
    }

    @Test
    fun `playing shows no clock`() {
        assertFalse(showsClock(SlideshowLoad.Playing, wasClock = true))
    }

    @Test
    fun `loading keeps what was there, so a retry doesn't blink the clock`() {
        assertTrue(showsClock(SlideshowLoad.Loading, wasClock = true))
        assertFalse(showsClock(SlideshowLoad.Loading, wasClock = false))
    }

    @Test
    fun `the clock moves every minute, among at least eight spots`() {
        for (minute in 0L..100L) assertNotEquals(clockSpot(minute), clockSpot(minute + 1))
        assertTrue((0L..100L).map(::clockSpot).toSet().size >= 8)
    }

    @Test
    fun `every spot is inside the safe area, also for a very large or negative minute`() {
        for (minute in listOf(-1L, 0L, 7L, Long.MAX_VALUE, 29_000_000L)) {
            val (x, y) = clockSpot(minute)
            assertTrue(x in 0f..1f)
            assertTrue(y in 0f..1f)
        }
        assertEquals(clockSpot(0), clockSpot(8))
    }
}
