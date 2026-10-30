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
import kotlin.random.Random

class LoadSlideshowTest {
    private fun slide(id: String, isVideo: Boolean = false) = Slide(Photo(PhotoId(id), isVideo), false, null, null)

    private val repository = FakeAlbumRepository().apply {
        slides = ServerResult.Ok(listOf(slide("a"), slide("v", isVideo = true), slide("b"), slide("c"), slide("d")))
    }

    private suspend fun ids(order: SlideshowOrder, videos: VideoMode = VideoMode.Skip, seed: Int = 1): List<String> =
        (LoadSlideshow(repository, Random(seed))(album(), order, videos) as ServerResult.Ok).value.map { it.photo.id.value }

    @Test
    fun `oldest first keeps the order the repository gives`() = runTest {
        assertEquals(listOf("a", "b", "c", "d"), ids(SlideshowOrder.OldestFirst))
    }

    @Test
    fun `newest first is that order reversed`() = runTest {
        assertEquals(listOf("d", "c", "b", "a"), ids(SlideshowOrder.NewestFirst))
    }

    @Test
    fun `shuffle follows the random it is given and loses nothing`() = runTest {
        val shuffled = ids(SlideshowOrder.Shuffle, seed = 7)
        assertEquals(listOf("a", "b", "c", "d").shuffled(Random(7)), shuffled)
        assertEquals(listOf("a", "b", "c", "d"), shuffled.sorted())
    }

    @Test
    fun `withoutVideos leaves out videos and keeps the order`() {
        val list = listOf(slide("b"), slide("v", isVideo = true), slide("a"))
        assertEquals(listOf(slide("b"), slide("a")), list.withoutVideos())
    }

    @Test
    fun `skip leaves the videos out in each order`() = runTest {
        for (order in SlideshowOrder.entries) {
            assertEquals(false, "v" in ids(order, VideoMode.Skip))
        }
    }

    @Test
    fun `muted and with sound keep the videos where they are, oldest first`() = runTest {
        for (mode in listOf(VideoMode.PlayMuted, VideoMode.PlayWithSound)) {
            assertEquals(listOf("a", "v", "b", "c", "d"), ids(SlideshowOrder.OldestFirst, mode))
        }
    }

    @Test
    fun `muted and with sound keep the videos newest first`() = runTest {
        for (mode in listOf(VideoMode.PlayMuted, VideoMode.PlayWithSound)) {
            assertEquals(listOf("d", "c", "b", "v", "a"), ids(SlideshowOrder.NewestFirst, mode))
        }
    }

    @Test
    fun `muted and with sound keep the videos in a shuffle`() = runTest {
        for (mode in listOf(VideoMode.PlayMuted, VideoMode.PlayWithSound)) {
            assertEquals(
                listOf("a", "v", "b", "c", "d").shuffled(Random(7)),
                ids(SlideshowOrder.Shuffle, mode, seed = 7),
            )
        }
    }

    @Test
    fun `a failure passes through`() = runTest {
        repository.slides = ServerResult.Failed(Problem.KeyLacksPermission)
        assertEquals(
            ServerResult.Failed(Problem.KeyLacksPermission),
            LoadSlideshow(repository)(album(), SlideshowOrder.OldestFirst, VideoMode.PlayMuted),
        )
    }
}
