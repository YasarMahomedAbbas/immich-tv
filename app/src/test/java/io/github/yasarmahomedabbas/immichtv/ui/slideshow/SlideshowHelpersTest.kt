package io.github.yasarmahomedabbas.immichtv.ui.slideshow

import androidx.compose.ui.input.key.Key
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.Place
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.Slide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class SlideshowHelpersTest {
    private val day = LocalDate.of(2025, 8, 3)

    private fun slide(takenOn: LocalDate?, place: Place?) = Slide(Photo(PhotoId("p"), false), false, takenOn, place)

    @Test
    fun `date and place both show`() {
        assertEquals(
            "3 August 2025" to "Lisbon, Portugal",
            dateAndPlace(slide(day, Place("Lisbon", "Portugal")), Locale.ENGLISH),
        )
    }

    @Test
    fun `a date alone`() {
        assertEquals("3 August 2025" to null, dateAndPlace(slide(day, null), Locale.ENGLISH))
    }

    @Test
    fun `a place with only a city or only a country`() {
        assertEquals(null to "Lisbon", dateAndPlace(slide(null, Place("Lisbon", null)), Locale.ENGLISH))
        assertEquals(null to "Portugal", dateAndPlace(slide(null, Place(null, "Portugal")), Locale.ENGLISH))
    }

    @Test
    fun `neither`() {
        assertEquals(null to null, dateAndPlace(slide(null, null), Locale.ENGLISH))
    }

    @Test
    fun `the date is in the given locale`() {
        assertEquals("3 août 2025", dateAndPlace(slide(day, null), Locale.FRENCH).first)
    }

    @Test
    fun `each of four slides in a row drifts its own way, then they repeat`() {
        val motions = (0..3).map(::slideMotion)
        assertEquals(4, motions.distinct().size)
        assertEquals(motions[0], slideMotion(4))
        for (motion in motions) {
            assertEquals(1.08f, motion.endScale)
            assertEquals(0.01f, kotlin.math.abs(motion.endX))
            assertEquals(0.01f, kotlin.math.abs(motion.endY))
        }
    }

    @Test
    fun `the remote's keys`() {
        assertEquals(SlideshowAction.Back, actionFor(Key.DirectionLeft))
        assertEquals(SlideshowAction.Forward, actionFor(Key.DirectionRight))
        for (key in listOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause)) {
            assertEquals(SlideshowAction.TogglePause, actionFor(key))
        }
        for (key in listOf(Key.DirectionUp, Key.DirectionDown)) {
            assertEquals(SlideshowAction.ShowControls, actionFor(key))
        }
    }

    @Test
    fun `Back and other keys are left alone`() {
        assertNull(actionFor(Key.Back))
        assertNull(actionFor(Key.A))
    }

    @Test
    fun `only Media3's IO errors are the server's`() {
        for (code in listOf(2000, 2001, 2004, 2008, 2999)) assertEquals(true, isServerFailure(code))
        // Unspecified, a runtime error, a malformed container, a decoder failure, and either side of the range.
        for (code in listOf(1000, 1004, 3001, 4001, 1999, 3000)) assertEquals(false, isServerFailure(code))
    }

    private fun photo(id: String) = Slide(Photo(PhotoId(id), false), false, null, null)

    @Test
    fun `a slide stays under the next until the next has faded in`() {
        val (a, b) = photo("a") to photo("b")
        val showingA = StageLayers().loaded(a).fadedIn(a)
        val movedToB = showingA.movedTo(b)
        assertEquals(a, movedToB.under(b))
        assertEquals(a, movedToB.loaded(b).under(b))
        assertNull(movedToB.loaded(b).fadedIn(b).under(b))
    }

    @Test
    fun `a skip during a fade puts the slide that was fading in underneath`() {
        val (a, b) = photo("a") to photo("b")
        val c = photo("c")
        val fadingInB = StageLayers().loaded(a).fadedIn(a).movedTo(b).loaded(b)
        assertEquals(b, fadingInB.movedTo(c).under(c))
    }

    @Test
    fun `after skipping back during a fade the skipped photo doesn't return at the next move`() {
        val (a, b) = photo("a") to photo("b")
        val c = photo("c")
        val fadingInB = StageLayers().loaded(a).fadedIn(a).movedTo(b).loaded(b)
        // Back to a, whose layer is still composed with its image: it fades in without loading again.
        val backOnA = fadingInB.movedTo(a).fadedIn(a)
        assertNull(backOnA.under(a))
        assertEquals(a, backOnA.movedTo(c).under(c))
    }

    @Test
    fun `holding OK pauses once, while the other keys repeat`() {
        assertEquals(false, SlideshowAction.TogglePause.whenHeld)
        assertEquals(true, SlideshowAction.Forward.whenHeld)
        assertEquals(true, SlideshowAction.Back.whenHeld)
    }
}
