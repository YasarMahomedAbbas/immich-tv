package io.github.yasarmahomedabbas.immichtv.domain.slideshow

import io.github.yasarmahomedabbas.immichtv.domain.Problem
import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.domain.photos.FakeLibraryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LoadLibrarySlideshowTest {
    private fun slide(id: String, isVideo: Boolean = false) = Slide(Photo(PhotoId(id), isVideo), false, null, null)

    private val repository = FakeLibraryRepository(
        slides = ServerResult.Ok(listOf(slide("a"), slide("b"), slide("v1", isVideo = true), slide("c"), slide("v2", isVideo = true))),
    )

    private suspend fun playlist(from: String) =
        (LoadLibrarySlideshow(repository)(PhotoId(from)) as ServerResult.Ok).value

    @Test
    fun `it plays everything in timeline order, videos in their place, starting at the chosen photo`() = runTest {
        val playlist = playlist("b")
        assertEquals(listOf("a", "b", "v1", "c", "v2"), playlist.slides.map { it.photo.id.value })
        assertEquals(1, playlist.start)
    }

    @Test
    fun `from a video it starts at that video`() = runTest {
        assertEquals(2, playlist("v1").start)
    }

    @Test
    fun `from a video at the very end it starts there`() = runTest {
        assertEquals(4, playlist("v2").start)
    }

    @Test
    fun `a photo no longer there starts at the first`() = runTest {
        assertEquals(0, playlist("gone").start)
    }

    @Test
    fun `only videos plays them`() = runTest {
        val v1 = slide("v1", isVideo = true)
        repository.slides = ServerResult.Ok(listOf(v1))
        assertEquals(Playlist(listOf(v1), 0), playlist("v1"))
    }

    @Test
    fun `an empty library has nothing to play`() = runTest {
        repository.slides = ServerResult.Ok(emptyList())
        assertEquals(Playlist(emptyList(), 0), playlist("a"))
    }

    @Test
    fun `a failure passes through`() = runTest {
        repository.slides = ServerResult.Failed(Problem.Unreachable)
        assertEquals(ServerResult.Failed(Problem.Unreachable), LoadLibrarySlideshow(repository)(PhotoId("a")))
    }
}
