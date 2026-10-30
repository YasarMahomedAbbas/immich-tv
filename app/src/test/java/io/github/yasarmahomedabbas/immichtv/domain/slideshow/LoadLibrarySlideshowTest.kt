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
    fun `it plays the photos, without the videos, starting at the chosen one`() = runTest {
        val playlist = playlist("b")
        assertEquals(listOf("a", "b", "c"), playlist.slides.map { it.photo.id.value })
        assertEquals(1, playlist.start)
    }

    @Test
    fun `from a video it starts at the next photo`() = runTest {
        assertEquals(2, playlist("v1").start)
    }

    @Test
    fun `from a video at the very end it wraps to the first photo`() = runTest {
        assertEquals(0, playlist("v2").start)
    }

    @Test
    fun `a photo no longer there starts at the first`() = runTest {
        assertEquals(0, playlist("gone").start)
    }

    @Test
    fun `only videos gives nothing to play`() = runTest {
        repository.slides = ServerResult.Ok(listOf(slide("v1", isVideo = true)))
        assertEquals(Playlist(emptyList(), 0), playlist("v1"))
    }

    @Test
    fun `a failure passes through`() = runTest {
        repository.slides = ServerResult.Failed(Problem.Unreachable)
        assertEquals(ServerResult.Failed(Problem.Unreachable), LoadLibrarySlideshow(repository)(PhotoId("a")))
    }
}
