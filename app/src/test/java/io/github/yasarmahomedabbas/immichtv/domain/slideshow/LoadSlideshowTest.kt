package io.github.yasarmahomedabbas.immichtv.domain.slideshow

import io.github.yasarmahomedabbas.immichtv.domain.Problem
import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import io.github.yasarmahomedabbas.immichtv.domain.albums.FakeAlbumRepository
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.domain.albums.album
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class LoadSlideshowTest {
    private fun slide(id: String, isVideo: Boolean = false) = Slide(Photo(PhotoId(id), isVideo), false, null, null)

    private val everything = listOf(slide("a"), slide("v", isVideo = true), slide("b"), slide("c"), slide("d"))
    private val repository = FakeAlbumRepository().apply { slides = ServerResult.Ok(everything) }
    private val spots = FakeResumePointStore()
    private val albumId = album().id

    private suspend fun show(
        order: SlideshowOrder,
        videos: VideoMode = VideoMode.Skip,
        start: ShowStart = ShowStart.Beginning,
        seed: Int = 1,
    ): AlbumShow = (LoadSlideshow(repository, spots, Random(seed))(album(), order, videos, start) as ServerResult.Ok).value

    private suspend fun ids(order: SlideshowOrder, videos: VideoMode = VideoMode.Skip, seed: Int = 1): List<String> =
        show(order, videos, seed = seed).playlist.slides.ids()

    private fun List<Slide>.ids() = map { it.photo.id.value }

    /** The seed a show draws first from `Random(seed)`, and the album shuffled with it. */
    private fun firstSeed(seed: Int) = Random(seed).nextLong()
    private fun shuffledWith(seed: Long, videos: VideoMode = VideoMode.Skip) =
        everything.shuffled(Random(seed)).let { if (videos == VideoMode.Skip) it.withoutVideos() else it }.ids()

    private fun remember(photo: String, index: Int = 0, seed: Long? = null) {
        spots.saved[albumId] = ResumePoint(PhotoId(photo), index, seed)
    }

    // Beginning

    @Test
    fun `oldest first keeps the order the repository gives`() = runTest {
        assertEquals(listOf("a", "b", "c", "d"), ids(SlideshowOrder.OldestFirst))
    }

    @Test
    fun `newest first is that order reversed`() = runTest {
        assertEquals(listOf("d", "c", "b", "a"), ids(SlideshowOrder.NewestFirst))
    }

    @Test
    fun `shuffle follows the random it is given, loses nothing and returns its seed`() = runTest {
        val shown = show(SlideshowOrder.Shuffle, seed = 7)
        assertEquals(shuffledWith(firstSeed(7)), shown.playlist.slides.ids())
        assertEquals(listOf("a", "b", "c", "d"), shown.playlist.slides.ids().sorted())
        assertEquals(firstSeed(7), shown.shuffleSeed)
    }

    @Test
    fun `beginning starts at the first slide in each order, and only a shuffle has a seed`() = runTest {
        for (order in SlideshowOrder.entries) {
            val shown = show(order)
            assertEquals(order.name, 0, shown.playlist.start)
            assertEquals(order.name, order == SlideshowOrder.Shuffle, shown.shuffleSeed != null)
        }
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
            assertEquals(shuffledWith(firstSeed(7), mode), ids(SlideshowOrder.Shuffle, mode, seed = 7))
        }
    }

    // Random start

    @Test
    fun `random start begins at the seeded random's slide, in order`() = runTest {
        for (order in listOf(SlideshowOrder.OldestFirst, SlideshowOrder.NewestFirst)) {
            val shown = show(order, start = ShowStart.RandomSlide, seed = 3)
            assertEquals(order.name, ids(order), shown.playlist.slides.ids())
            assertEquals(order.name, Random(3).nextInt(4), shown.playlist.start)
            assertNull(shown.shuffleSeed)
        }
    }

    @Test
    fun `random start in a shuffle draws the seed, then the slide`() = runTest {
        val random = Random(3)
        val seed = random.nextLong()
        val shown = show(SlideshowOrder.Shuffle, start = ShowStart.RandomSlide, seed = 3)
        assertEquals(shuffledWith(seed), shown.playlist.slides.ids())
        assertEquals(random.nextInt(4), shown.playlist.start)
        assertEquals(seed, shown.shuffleSeed)
    }

    @Test
    fun `random start of an empty album begins at 0`() = runTest {
        repository.slides = ServerResult.Ok(emptyList())
        assertEquals(Playlist(emptyList(), 0), show(SlideshowOrder.OldestFirst, start = ShowStart.RandomSlide).playlist)
    }

    // Shuffle button

    @Test
    fun `shuffled plays shuffled whatever the order, from the first slide, and returns its seed`() = runTest {
        val shown = show(SlideshowOrder.OldestFirst, start = ShowStart.Shuffled, seed = 7)
        assertEquals(shuffledWith(firstSeed(7)), shown.playlist.slides.ids())
        assertEquals(0, shown.playlist.start)
        assertEquals(firstSeed(7), shown.shuffleSeed)
    }

    // Continue

    @Test
    fun `continue resumes at the remembered photo, oldest first`() = runTest {
        remember("c", index = 9)
        val shown = show(SlideshowOrder.OldestFirst, start = ShowStart.Continue)
        assertEquals(listOf("a", "b", "c", "d"), shown.playlist.slides.ids())
        assertEquals(2, shown.playlist.start)
        assertNull(shown.shuffleSeed)
    }

    @Test
    fun `continue resumes at the remembered photo, newest first`() = runTest {
        remember("c")
        val shown = show(SlideshowOrder.NewestFirst, start = ShowStart.Continue)
        assertEquals(listOf("d", "c", "b", "a"), shown.playlist.slides.ids())
        assertEquals(1, shown.playlist.start)
    }

    @Test
    fun `continue after a shuffle plays the same sequence at the same slide, whatever the order is now`() = runTest {
        val first = show(SlideshowOrder.OldestFirst, VideoMode.PlayMuted, ShowStart.Shuffled, seed = 7)
        spots.save(albumId, first.resumePointAt(3)!!)

        val resumed = show(SlideshowOrder.OldestFirst, VideoMode.PlayMuted, ShowStart.Continue, seed = 99)

        assertEquals(first.playlist.slides, resumed.playlist.slides)
        assertEquals(3, resumed.playlist.start)
        assertEquals(first.shuffleSeed, resumed.shuffleSeed)
    }

    @Test
    fun `continue after a shuffle keeps the sequence when videos are now skipped`() = runTest {
        val first = show(SlideshowOrder.Shuffle, VideoMode.PlayMuted, seed = 7)
        val at = first.playlist.slides.indexOfLast { !it.photo.isVideo }
        spots.save(albumId, first.resumePointAt(at)!!)

        val resumed = show(SlideshowOrder.Shuffle, VideoMode.Skip, ShowStart.Continue, seed = 99)

        val kept = first.playlist.slides.withoutVideos()
        assertEquals(kept, resumed.playlist.slides)
        assertEquals(kept.indexOf(first.playlist.slides[at]), resumed.playlist.start)
        assertEquals(first.shuffleSeed, resumed.shuffleSeed)
    }

    @Test
    fun `continue without a seed in a shuffle order is a fresh shuffle from the photo`() = runTest {
        remember("b")
        val shown = show(SlideshowOrder.Shuffle, start = ShowStart.Continue, seed = 5)
        assertEquals(shuffledWith(firstSeed(5)), shown.playlist.slides.ids())
        assertEquals(shown.playlist.slides.ids().indexOf("b"), shown.playlist.start)
        assertEquals(firstSeed(5), shown.shuffleSeed)
    }

    @Test
    fun `continue at a video now skipped resumes at the next kept slide`() = runTest {
        remember("v", index = 1)
        val shown = show(SlideshowOrder.OldestFirst, VideoMode.Skip, ShowStart.Continue)
        assertEquals(listOf("a", "b", "c", "d"), shown.playlist.slides.ids())
        assertEquals(1, shown.playlist.start)
    }

    @Test
    fun `continue at a skipped video with nothing kept after it begins at the first`() = runTest {
        repository.slides = ServerResult.Ok(listOf(slide("a"), slide("b"), slide("v", isVideo = true)))
        remember("v", index = 2)
        assertEquals(0, show(SlideshowOrder.OldestFirst, VideoMode.Skip, ShowStart.Continue).playlist.start)
    }

    @Test
    fun `continue at a photo gone from the album resumes at its old index`() = runTest {
        remember("gone", index = 2)
        assertEquals(2, show(SlideshowOrder.OldestFirst, start = ShowStart.Continue).playlist.start)
    }

    @Test
    fun `continue at a photo gone from the album past the end begins at the first`() = runTest {
        remember("gone", index = 4)
        assertEquals(0, show(SlideshowOrder.OldestFirst, start = ShowStart.Continue).playlist.start)
    }

    @Test
    fun `continue with no spot begins at the first`() = runTest {
        val shown = show(SlideshowOrder.OldestFirst, start = ShowStart.Continue)
        assertEquals(Playlist(everything.withoutVideos(), 0), shown.playlist)
    }

    @Test
    fun `only continue reads the spot`() = runTest {
        remember("c")
        for (start in listOf(ShowStart.Beginning, ShowStart.Shuffled)) {
            assertEquals(start.name, 0, show(SlideshowOrder.OldestFirst, start = start).playlist.start)
        }
    }

    // AlbumShow

    @Test
    fun `resumePointAt gives the slide's photo, index and seed, or null out of range`() {
        val shown = AlbumShow(Playlist(listOf(slide("a"), slide("b"))), shuffleSeed = 4L)
        assertEquals(ResumePoint(PhotoId("b"), 1, 4L), shown.resumePointAt(1))
        assertNull(shown.resumePointAt(2))
        assertNull(shown.resumePointAt(-1))
        assertEquals(ResumePoint(PhotoId("a"), 0, null), shown.copy(shuffleSeed = null).resumePointAt(0))
    }

    @Test
    fun `a failure passes through`() = runTest {
        repository.slides = ServerResult.Failed(Problem.KeyLacksPermission)
        for (start in ShowStart.entries) {
            assertEquals(
                ServerResult.Failed(Problem.KeyLacksPermission),
                LoadSlideshow(repository, spots)(album(), SlideshowOrder.OldestFirst, VideoMode.PlayMuted, start),
            )
        }
    }
}
