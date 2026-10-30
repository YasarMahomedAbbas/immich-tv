package io.github.yasarmahomedabbas.immichtv.ui.photos

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MovePaceTest {
    private val pace = MovePace(minGapMillis = 100)

    @Test
    fun `the first move is accepted`() {
        assertTrue(pace.accept(5_000))
    }

    @Test
    fun `a move too soon is refused, even with a frame since the last`() {
        pace.accept(5_000)
        pace.frameDrawn()
        assertFalse(pace.accept(5_099))
    }

    @Test
    fun `a move after the gap but with no frame is refused`() {
        pace.accept(5_000)
        assertFalse(pace.accept(5_500))
    }

    @Test
    fun `a move after both the gap and a frame is accepted`() {
        pace.accept(5_000)
        pace.frameDrawn()
        assertTrue(pace.accept(5_100))
    }

    @Test
    fun `a refused move doesn't restart the gap`() {
        pace.accept(5_000)
        pace.frameDrawn()
        assertFalse(pace.accept(5_090))
        assertTrue(pace.accept(5_100))
    }

    @Test
    fun `an accepted move needs a new frame before the next`() {
        pace.accept(5_000)
        pace.frameDrawn()
        pace.accept(5_100)
        assertFalse(pace.accept(5_200))
    }
}
