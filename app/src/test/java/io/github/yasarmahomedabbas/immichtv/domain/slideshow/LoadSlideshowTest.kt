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

    private suspend fun ids(order: SlideshowOrder, seed: Int = 1): List<String> =
        (LoadSlideshow(repository, Random(seed))(album(), order) as ServerResult.Ok).value.map { it.photo.id.value }

    @Test
    fun `oldest first keeps the order the repository gives, without the videos`() = runTest {
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
    fun `a failure passes through`() = runTest {
        repository.slides = ServerResult.Failed(Problem.KeyLacksPermission)
        assertEquals(
            ServerResult.Failed(Problem.KeyLacksPermission),
            LoadSlideshow(repository)(album(), SlideshowOrder.OldestFirst),
        )
    }
}
