package io.github.yasarmahomedabbas.immichtv.domain.slideshow

import io.github.yasarmahomedabbas.immichtv.domain.Problem
import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import io.github.yasarmahomedabbas.immichtv.domain.albums.FakeAlbumRepository
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.domain.albums.album
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LoadSlideshowFromPhotoTest {
    private fun slide(id: String, isVideo: Boolean = false) = Slide(Photo(PhotoId(id), isVideo), false, null, null)

    /** Oldest first, as the repository gives them. */
    private val repository = FakeAlbumRepository().apply {
        slides = ServerResult.Ok(listOf(slide("a"), slide("v1", isVideo = true), slide("b"), slide("c"), slide("v2", isVideo = true)))
    }

    private suspend fun playlist(from: String, newestFirst: Boolean = false, videos: VideoMode = VideoMode.PlayMuted) =
        (LoadSlideshowFromPhoto(repository)(album(newestFirst = newestFirst), PhotoId(from), videos) as ServerResult.Ok).value

    private fun Playlist.ids() = slides.map { it.photo.id.value }

    @Test
    fun `an oldest-first album plays oldest first, from the chosen photo`() = runTest {
        val playlist = playlist("b")
        assertEquals(listOf("a", "v1", "b", "c", "v2"), playlist.ids())
        assertEquals(2, playlist.start)
    }

    @Test
    fun `a newest-first album plays newest first, from the chosen photo`() = runTest {
        val playlist = playlist("b", newestFirst = true)
        assertEquals(listOf("v2", "c", "b", "v1", "a"), playlist.ids())
        assertEquals(2, playlist.start)
    }

    @Test
    fun `skip leaves the videos out`() = runTest {
        val playlist = playlist("b", videos = VideoMode.Skip)
        assertEquals(listOf("a", "b", "c"), playlist.ids())
        assertEquals(1, playlist.start)
    }

    @Test
    fun `a held video starts itself when videos play`() = runTest {
        assertEquals(1, playlist("v1").start)
    }

    @Test
    fun `a held video starts at the next photo when videos are skipped`() = runTest {
        assertEquals(1, playlist("v1", videos = VideoMode.Skip).start)
    }

    @Test
    fun `next after a skipped video follows the grid's order`() = runTest {
        val playlist = playlist("v1", newestFirst = true, videos = VideoMode.Skip)
        assertEquals(listOf("c", "b", "a"), playlist.ids())
        assertEquals(2, playlist.start)
    }

    @Test
    fun `a skipped video with nothing after it starts at the first`() = runTest {
        assertEquals(0, playlist("v2", videos = VideoMode.Skip).start)
    }

    @Test
    fun `a photo no longer there starts at the first`() = runTest {
        assertEquals(0, playlist("gone").start)
    }

    @Test
    fun `an album of only videos, skipped, gives nothing to play`() = runTest {
        repository.slides = ServerResult.Ok(listOf(slide("v1", isVideo = true)))
        assertEquals(Playlist(emptyList(), 0), playlist("v1", videos = VideoMode.Skip))
    }

    @Test
    fun `an empty album gives nothing to play`() = runTest {
        repository.slides = ServerResult.Ok(emptyList())
        assertEquals(Playlist(emptyList(), 0), playlist("a"))
    }

    @Test
    fun `a failure passes through`() = runTest {
        repository.slides = ServerResult.Failed(Problem.Unreachable)
        assertEquals(
            ServerResult.Failed(Problem.Unreachable),
            LoadSlideshowFromPhoto(repository)(album(), PhotoId("a"), VideoMode.PlayMuted),
        )
    }
}
