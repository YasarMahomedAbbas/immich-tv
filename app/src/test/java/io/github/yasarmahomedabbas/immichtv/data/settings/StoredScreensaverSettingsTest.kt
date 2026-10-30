package io.github.yasarmahomedabbas.immichtv.data.settings

import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumId
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.ScreensaverSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class StoredScreensaverSettingsTest {
    private val storage = MemoryStorage()
    private val store = StoredScreensaverSettings(storage)

    @Test
    fun `an album loads back`() = runTest {
        store.save(ScreensaverSource.Album(AlbumId("summer")))
        assertEquals(ScreensaverSource.Album(AlbumId("summer")), store.load())
        assertEquals("album:summer", storage.values["screensaver.source"])
    }

    @Test
    fun `the library loads back`() = runTest {
        store.save(ScreensaverSource.Album(AlbumId("summer")))
        store.save(ScreensaverSource.Library)
        assertEquals(ScreensaverSource.Library, store.load())
        assertEquals("library", storage.values["screensaver.source"])
    }

    @Test
    fun `nothing saved is the library`() = runTest {
        assertEquals(ScreensaverSource.Library, store.load())
    }

    @Test
    fun `an unreadable value is the library`() = runTest {
        for (bad in listOf("", "album", "album:", "Library", "albums:summer")) {
            storage.values["screensaver.source"] = bad
            assertEquals(bad, ScreensaverSource.Library, store.load())
        }
    }

    @Test
    fun `an album id with a colon in it survives`() = runTest {
        val odd = ScreensaverSource.Album(AlbumId("a:b:c"))
        store.save(odd)
        assertEquals(odd, store.load())
    }
}
