package io.github.yasarmahomedabbas.immichtv.domain.slideshow

import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlideshowSettingsTest {
    @Test
    fun `the defaults`() {
        assertEquals(SlideshowSettings(8, SlideshowOrder.OldestFirst, VideoMode.PlayMuted, true), SlideshowSettings())
    }

    @Test
    fun `time per photo cycles 8, 12, 20, 5 and wraps`() {
        val seen = generateSequence(SlideshowSettings()) { it.nextSeconds() }.take(5).map { it.secondsPerPhoto }.toList()
        assertEquals(listOf(8, 12, 20, 5, 8), seen)
    }

    @Test
    fun `an unknown time per photo goes to 8`() {
        assertEquals(8, SlideshowSettings(secondsPerPhoto = 7).nextSeconds().secondsPerPhoto)
    }

    @Test
    fun `order cycles oldest, shuffle, newest and wraps`() {
        val seen = generateSequence(SlideshowSettings()) { it.nextOrder() }.take(4).map { it.order }.toList()
        assertEquals(listOf(SlideshowOrder.OldestFirst, SlideshowOrder.Shuffle, SlideshowOrder.NewestFirst, SlideshowOrder.OldestFirst), seen)
    }

    @Test
    fun `videos cycle muted, with sound, skip and wrap`() {
        val seen = generateSequence(SlideshowSettings()) { it.nextVideos() }.take(4).map { it.videos }.toList()
        assertEquals(listOf(VideoMode.PlayMuted, VideoMode.PlayWithSound, VideoMode.Skip, VideoMode.PlayMuted), seen)
    }

    @Test
    fun `date and place toggles`() {
        assertFalse(SlideshowSettings().toggleDateAndPlace().showDateAndPlace)
        assertTrue(SlideshowSettings().toggleDateAndPlace().toggleDateAndPlace().showDateAndPlace)
    }

    @Test
    fun `the use cases go through the store`() = runTest {
        val store = FakeSlideshowSettingsStore()
        val album = AlbumId("a")
        SaveSlideshowSettings(store)(album, SlideshowSettings(secondsPerPhoto = 20))
        assertEquals(SlideshowSettings(secondsPerPhoto = 20), GetSlideshowSettings(store)(album))
        assertEquals(SlideshowSettings(), GetSlideshowSettings(store)(AlbumId("b")))
    }
}
