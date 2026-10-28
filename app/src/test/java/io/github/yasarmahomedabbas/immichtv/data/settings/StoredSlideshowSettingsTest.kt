package io.github.yasarmahomedabbas.immichtv.data.settings

import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumId
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.SlideshowOrder
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.SlideshowSettings
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.VideoMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class StoredSlideshowSettingsTest {
    private val storage = MemoryStorage()
    private val store = StoredSlideshowSettings(storage)
    private val summer = AlbumId("summer")
    private val wedding = AlbumId("wedding")
    private val changed = SlideshowSettings(20, SlideshowOrder.Shuffle, VideoMode.Skip, showDateAndPlace = false)

    @Test
    fun `saved settings load back`() = runTest {
        store.save(summer, changed)
        assertEquals(changed, store.load(summer))
        assertEquals("20|Shuffle|Skip|false", storage.values["slideshow.summer"])
    }

    @Test
    fun `two albums are kept apart`() = runTest {
        store.save(summer, changed)
        store.save(wedding, SlideshowSettings(secondsPerPhoto = 5))
        assertEquals(changed, store.load(summer))
        assertEquals(SlideshowSettings(secondsPerPhoto = 5), store.load(wedding))
    }

    @Test
    fun `an album with nothing saved gets the defaults`() = runTest {
        assertEquals(SlideshowSettings(), store.load(summer))
    }

    @Test
    fun `a corrupt value falls back to the defaults`() = runTest {
        for (bad in listOf("", "8|OldestFirst|PlayMuted", "x|OldestFirst|PlayMuted|true", "0|OldestFirst|PlayMuted|true",
            "8|Sideways|PlayMuted|true", "8|OldestFirst|Loud|true", "8|OldestFirst|PlayMuted|yes", "8|OldestFirst|PlayMuted|true|extra")) {
            storage.values["slideshow.summer"] = bad
            assertEquals(bad, SlideshowSettings(), store.load(summer))
        }
    }
}
