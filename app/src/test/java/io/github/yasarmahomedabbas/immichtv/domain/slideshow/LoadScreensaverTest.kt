package io.github.yasarmahomedabbas.immichtv.domain.slideshow

import io.github.yasarmahomedabbas.immichtv.domain.Problem
import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import io.github.yasarmahomedabbas.immichtv.domain.albums.FakeAlbumRepository
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.domain.albums.album
import io.github.yasarmahomedabbas.immichtv.domain.photos.FakeLibraryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LoadScreensaverTest {
    private val summer = album(id = "a1")
    private val store = FakeScreensaverSettingsStore()
    private val albums = FakeAlbumRepository(albums = ServerResult.Ok(listOf(summer)))
    private val library = FakeLibraryRepository()
    private val load = LoadScreensaver(store, albums, library, Random(1))

    private fun photo(id: Int) = Slide(Photo(PhotoId("p$id"), false), false, null, null)

    private fun video(id: Int) = Slide(Photo(PhotoId("v$id"), true), false, null, null)

    private suspend fun played(): List<Slide> = (load() as ServerResult.Ok).value.slides

    @Test
    fun `the library plays its photos only, shuffled`() = runTest {
        val photos = List(20, ::photo)
        library.slides = ServerResult.Ok(photos + video(1))

        val slides = played()

        assertEquals(photos.toSet(), slides.toSet())
        assertEquals(photos.size, slides.size)
        assertNotEquals(photos, slides)
    }

    @Test
    fun `the library's order comes from the random it was given`() = runTest {
        library.slides = ServerResult.Ok(List(20, ::photo))
        assertEquals(List(20, ::photo).shuffled(Random(1)), played())
    }

    @Test
    fun `the library holds at most the cap, from all of it`() = runTest {
        val photos = List(MAX_LIBRARY_PHOTOS + 100, ::photo)
        library.slides = ServerResult.Ok(photos)

        val slides = played()

        assertEquals(MAX_LIBRARY_PHOTOS, slides.size)
        assertEquals(MAX_LIBRARY_PHOTOS, slides.toSet().size)
        // Taken after the shuffle, not the newest 500.
        assertTrue(slides.any { it !in photos.take(MAX_LIBRARY_PHOTOS) })
    }

    @Test
    fun `a library failure passes through`() = runTest {
        library.slides = ServerResult.Failed(Problem.KeyRejected)
        assertEquals(ServerResult.Failed(Problem.KeyRejected), load())
    }

    @Test
    fun `no session is a failure that passes through`() = runTest {
        library.slides = ServerResult.Failed(Problem.NotSignedIn)
        assertEquals(ServerResult.Failed(Problem.NotSignedIn), load())
    }

    @Test
    fun `an album plays its photos only, shuffled, without the cap`() = runTest {
        val photos = List(MAX_LIBRARY_PHOTOS + 1, ::photo)
        store.source = ScreensaverSource.Album(summer.id)
        albums.slides = ServerResult.Ok(photos + video(1))

        assertEquals(photos.shuffled(Random(1)), played())
    }

    @Test
    fun `a missing album fails and keeps the stored choice`() = runTest {
        val gone = ScreensaverSource.Album(album(id = "gone").id)
        store.source = gone

        assertEquals(ServerResult.Failed(Problem.Unreachable), load())
        assertEquals(gone, store.source)
        assertEquals(0, store.saves)
    }

    @Test
    fun `an album whose photos fail passes that through and keeps the stored choice`() = runTest {
        store.source = ScreensaverSource.Album(summer.id)
        albums.slides = ServerResult.Failed(Problem.KeyLacksPermission)

        assertEquals(ServerResult.Failed(Problem.KeyLacksPermission), load())
        assertEquals(ScreensaverSource.Album(summer.id), store.source)
    }

    @Test
    fun `the show is fixed, photos only and shuffled`() {
        assertEquals(SlideshowSettings(8, SlideshowOrder.Shuffle, VideoMode.Skip, showDateAndPlace = true), ScreensaverShow)
    }

    @Test
    fun `get and save go to the store`() = runTest {
        val chosen = ScreensaverSource.Album(summer.id)
        SaveScreensaverSource(store)(chosen)
        assertEquals(chosen, GetScreensaverSource(store)())
    }
}
