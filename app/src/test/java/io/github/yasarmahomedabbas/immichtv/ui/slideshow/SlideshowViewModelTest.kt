package io.github.yasarmahomedabbas.immichtv.ui.slideshow

import io.github.yasarmahomedabbas.immichtv.domain.Problem
import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import io.github.yasarmahomedabbas.immichtv.domain.albums.FakeAlbumRepository
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.domain.albums.album
import io.github.yasarmahomedabbas.immichtv.domain.map
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.LoadSlideshow
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.Playlist
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.Slide
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.SlideshowSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SlideshowViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeAlbumRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun slide(id: String) = Slide(Photo(PhotoId(id), false), false, null, null)

    private fun video(id: String) = Slide(Photo(PhotoId(id), true), false, null, null)

    private fun photo(id: String) = PhotoId(id)

    private val SlideshowViewModel.shown get() = state.value.current?.photo?.id?.value

    private fun albumShow(settings: SlideshowSettings) = SlideshowViewModel(settings) {
        LoadSlideshow(repository)(album(), settings.order, settings.videos).map { Playlist(it) }
    }

    /** A slideshow of [ids] at 5 s a photo, loaded, with every photo's image ready if [allReady]. */
    private fun TestScope.loaded(vararg ids: String, allReady: Boolean = true): SlideshowViewModel {
        return show(ids.map(::slide), allReady)
    }

    private fun TestScope.show(slides: List<Slide>, allReady: Boolean = true): SlideshowViewModel {
        repository.slides = ServerResult.Ok(slides)
        val vm = albumShow(SlideshowSettings(secondsPerPhoto = 5))
        advanceUntilIdle()
        if (allReady) slides.forEach { vm.imageReady(it.photo.id) }
        return vm
    }

    private fun SlideshowViewModel.failCurrent(serverFailure: Boolean) =
        videoFailed(state.value.current!!.photo.id, serverFailure)

    private fun TestScope.playing(vm: SlideshowViewModel) = backgroundScope.launch { vm.play() }

    /** Exactly [ms] of virtual time, with what is due at its end run too. */
    private fun TestScope.pass(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    @Test
    fun `it loads, then plays from the first photo`() = runTest(dispatcher) {
        repository.slides = ServerResult.Ok(listOf(slide("a"), slide("b")))
        val vm = albumShow(SlideshowSettings())
        assertEquals(SlideshowLoad.Loading, vm.state.value.load)
        advanceUntilIdle()

        assertEquals(SlideshowLoad.Playing, vm.state.value.load)
        assertEquals("a", vm.shown)
        assertEquals(1, vm.state.value.position)
    }

    @Test
    fun `a playlist with a start plays from there, and moves on and back from there`() = runTest(dispatcher) {
        val slides = listOf(slide("a"), slide("b"), slide("c"))
        val vm = SlideshowViewModel(SlideshowSettings()) { ServerResult.Ok(Playlist(slides, start = 2)) }
        advanceUntilIdle()

        assertEquals("c", vm.shown)
        assertEquals(3, vm.state.value.position)
        vm.skipForward()
        assertEquals("a", vm.shown)
    }

    @Test
    fun `retry goes back to the playlist's start`() = runTest(dispatcher) {
        var result: ServerResult<Playlist> = ServerResult.Failed(Problem.Unreachable)
        val vm = SlideshowViewModel(SlideshowSettings()) { result }
        advanceUntilIdle()

        result = ServerResult.Ok(Playlist(listOf(slide("a"), slide("b")), start = 1))
        vm.retry()
        advanceUntilIdle()
        assertEquals("b", vm.shown)
    }

    @Test
    fun `an album with nothing to play is empty`() = runTest(dispatcher) {
        assertEquals(SlideshowLoad.Empty, loaded().state.value.load)
    }

    @Test
    fun `a failed load says why, and retry plays`() = runTest(dispatcher) {
        repository.slides = ServerResult.Failed(Problem.KeyRejected)
        val vm = albumShow(SlideshowSettings())
        advanceUntilIdle()
        assertEquals(SlideshowLoad.Failed(Problem.KeyRejected), vm.state.value.load)

        repository.slides = ServerResult.Ok(listOf(slide("a")))
        vm.retry()
        assertEquals(SlideshowLoad.Loading, vm.state.value.load)
        advanceUntilIdle()
        assertEquals(SlideshowLoad.Playing, vm.state.value.load)
    }

    @Test
    fun `retry is ignored while loading`() = runTest(dispatcher) {
        repository.slides = ServerResult.Ok(listOf(slide("a")))
        val vm = albumShow(SlideshowSettings())
        vm.retry()
        advanceUntilIdle()
        assertEquals(1, repository.slidesCalls)
    }

    @Test
    fun `it moves on after the time per photo, and not before`() = runTest(dispatcher) {
        val vm = loaded("a", "b", "c")
        playing(vm)
        pass(4_900)
        assertEquals("a", vm.shown)
        pass(100)
        assertEquals("b", vm.shown)
        pass(5_000)
        assertEquals("c", vm.shown)
    }

    @Test
    fun `it wraps from the last photo to the first, in the same order`() = runTest(dispatcher) {
        val vm = loaded("a", "b")
        playing(vm)
        pass(10_000)
        assertEquals("a", vm.shown)
    }

    @Test
    fun `a photo's time doesn't start until it can be seen`() = runTest(dispatcher) {
        val vm = loaded("a", "b", allReady = false)
        vm.imageReady(photo("b"))
        playing(vm)
        pass(20_000)
        assertEquals("a", vm.shown)

        vm.imageReady(photo("a"))
        pass(4_900)
        assertEquals("a", vm.shown)
        pass(100)
        assertEquals("b", vm.shown)
    }

    @Test
    fun `it waits while the next isn't ready, and goes as soon as it is`() = runTest(dispatcher) {
        val vm = loaded("a", "b", allReady = false)
        vm.imageReady(photo("a"))
        playing(vm)
        pass(30_000)
        assertEquals("a", vm.shown)

        vm.imageReady(photo("b"))
        pass(100)
        assertEquals("b", vm.shown)
    }

    @Test
    fun `skips move at once, and wrap both ways`() = runTest(dispatcher) {
        val vm = loaded("a", "b", "c")
        vm.skipBack()
        assertEquals("c", vm.shown)
        vm.skipForward()
        assertEquals("a", vm.shown)
        vm.skipForward()
        assertEquals("b", vm.shown)
    }

    @Test
    fun `a skip restarts the time`() = runTest(dispatcher) {
        val vm = loaded("a", "b", "c")
        playing(vm)
        pass(4_000)
        vm.skipForward()
        pass(4_900)
        assertEquals("b", vm.shown)
        pass(100)
        assertEquals("c", vm.shown)
    }

    @Test
    fun `a pause holds the time and resuming plays only what was left`() = runTest(dispatcher) {
        val vm = loaded("a", "b")
        playing(vm)
        pass(3_000)
        vm.togglePause()
        assertTrue(vm.state.value.paused)
        pass(60_000)
        assertEquals("a", vm.shown)

        vm.togglePause()
        assertFalse(vm.state.value.paused)
        pass(1_900)
        assertEquals("a", vm.shown)
        pass(200)
        assertEquals("b", vm.shown)
    }

    @Test
    fun `a skip while paused stays paused`() = runTest(dispatcher) {
        val vm = loaded("a", "b", "c")
        playing(vm)
        vm.togglePause()
        vm.skipForward()
        assertEquals("b", vm.shown)
        assertTrue(vm.state.value.paused)
        pass(60_000)
        assertEquals("b", vm.shown)
    }

    @Test
    fun `a failed photo is left out, and the show stays on the same slide`() = runTest(dispatcher) {
        val vm = loaded("a", "b", "c")
        vm.skipForward()

        vm.imageFailed(photo("a"))
        assertEquals("b", vm.shown)
        assertEquals(listOf("b", "c"), vm.state.value.slides.map { it.photo.id.value })

        vm.imageFailed(photo("c"))
        assertEquals("b", vm.shown)
    }

    @Test
    fun `when the current photo fails the next takes its place, wrapping at the end`() = runTest(dispatcher) {
        val vm = loaded("a", "b", "c")
        vm.imageFailed(photo("a"))
        assertEquals("b", vm.shown)

        vm.skipForward()
        assertEquals("c", vm.shown)
        vm.imageFailed(photo("c"))
        assertEquals("b", vm.shown)
    }

    @Test
    fun `a report about a photo no longer in the show changes nothing`() = runTest(dispatcher) {
        val vm = loaded("a", "b")
        vm.imageFailed(photo("gone"))
        assertEquals(2, vm.state.value.slides.size)
    }

    @Test
    fun `five failures in a row fail the show`() = runTest(dispatcher) {
        val vm = loaded("a", "b", "c", "d", "e", "f")
        repeat(SlideshowViewModel.MAX_FAILURES - 1) { vm.imageFailed(vm.state.value.current!!.photo.id) }
        assertEquals(SlideshowLoad.Playing, vm.state.value.load)

        vm.imageFailed(vm.state.value.current!!.photo.id)
        assertEquals(SlideshowLoad.Failed(Problem.Unreachable), vm.state.value.load)
    }

    @Test
    fun `an image that loads starts the count of failures again`() = runTest(dispatcher) {
        val vm = loaded("a", "b", "c", "d", "e", "f", "g")
        repeat(4) { vm.imageFailed(vm.state.value.current!!.photo.id) }
        vm.imageReady(photo("g"))
        repeat(2) { vm.imageFailed(vm.state.value.current!!.photo.id) }
        assertEquals(SlideshowLoad.Playing, vm.state.value.load)
    }

    @Test
    fun `when no photo is left the show has failed`() = runTest(dispatcher) {
        val vm = loaded("a")
        vm.imageFailed(photo("a"))
        assertEquals(SlideshowLoad.Failed(Problem.Unreachable), vm.state.value.load)
    }

    @Test
    fun `the controls show on a key and hide after four seconds`() = runTest(dispatcher) {
        val vm = loaded("a", "b")
        assertFalse(vm.state.value.controlsVisible)
        vm.showControls()
        assertTrue(vm.state.value.controlsVisible)
        pass(SlideshowViewModel.CONTROLS_MS - 1)
        assertTrue(vm.state.value.controlsVisible)
        pass(1)
        assertFalse(vm.state.value.controlsVisible)
    }

    @Test
    fun `another key restarts the time until the controls hide`() = runTest(dispatcher) {
        val vm = loaded("a", "b")
        vm.skipForward()
        pass(3_000)
        vm.skipForward()
        pass(3_000)
        assertTrue(vm.state.value.controlsVisible)
        pass(1_000)
        assertFalse(vm.state.value.controlsVisible)
    }

    @Test
    fun `the controls hide while paused too`() = runTest(dispatcher) {
        val vm = loaded("a", "b")
        vm.togglePause()
        assertTrue(vm.state.value.controlsVisible)
        pass(SlideshowViewModel.CONTROLS_MS)
        assertFalse(vm.state.value.controlsVisible)
        assertTrue(vm.state.value.paused)
    }

    @Test
    fun `the remote does nothing until it plays`() = runTest(dispatcher) {
        val vm = loaded()
        vm.skipForward()
        vm.skipBack()
        vm.togglePause()
        vm.showControls()
        assertEquals(SlideshowState(load = SlideshowLoad.Empty), vm.state.value)
    }

    @Test
    fun `next and previous are the slides to load ahead, wrapping`() = runTest(dispatcher) {
        val vm = loaded("a", "b", "c")
        assertEquals("b", vm.state.value.next?.photo?.id?.value)
        assertEquals("c", vm.state.value.previous?.photo?.id?.value)
        vm.skipBack()
        assertEquals("a", vm.state.value.next?.photo?.id?.value)
        assertEquals("b", vm.state.value.previous?.photo?.id?.value)
    }

    @Test
    fun `no slides have no current, next or previous`() {
        val state = SlideshowState()
        assertNull(state.current)
        assertNull(state.next)
        assertNull(state.previous)
    }

    @Test
    fun `one photo is its own neighbour and just stays`() = runTest(dispatcher) {
        val vm = loaded("a")
        playing(vm)
        pass(20_000)
        assertEquals("a", vm.shown)
        assertEquals(1, vm.state.value.position)
    }

    @Test
    fun `no time passes once play is cancelled`() = runTest(dispatcher) {
        val vm = loaded("a", "b")
        val timer = playing(vm)
        pass(2_000)
        timer.cancel()
        pass(60_000)
        assertEquals("a", vm.shown)

        playing(vm)
        pass(2_900)
        assertEquals("a", vm.shown)
        pass(100)
        assertEquals("b", vm.shown)
    }

    @Test
    fun `a video ignores the time per photo`() = runTest(dispatcher) {
        val vm = show(listOf(video("v"), slide("a")))
        vm.videoPlaying(photo("v"))
        playing(vm)
        pass(120_000)
        assertEquals("v", vm.shown)
    }

    @Test
    fun `a video's end moves the show on`() = runTest(dispatcher) {
        val vm = show(listOf(video("v"), slide("a")))
        vm.videoPlaying(photo("v"))
        playing(vm)
        pass(1_000)
        vm.videoEnded(photo("v"))
        pass(100)
        assertEquals("a", vm.shown)
    }

    @Test
    fun `a photo after a video gets its full time`() = runTest(dispatcher) {
        val vm = show(listOf(video("v"), slide("a"), slide("b")))
        playing(vm)
        vm.videoEnded(photo("v"))
        pass(100)
        assertEquals("a", vm.shown)
        pass(4_900)
        assertEquals("a", vm.shown)
        pass(100)
        assertEquals("b", vm.shown)
    }

    @Test
    fun `a video's end waits if the next isn't ready`() = runTest(dispatcher) {
        val vm = show(listOf(video("v"), slide("a")), allReady = false)
        vm.imageReady(photo("v"))
        vm.videoPlaying(photo("v"))
        vm.videoEnded(photo("v"))
        playing(vm)
        // Well past the stall time: an ended video isn't stalled.
        pass(60_000)
        assertEquals("v", vm.shown)

        vm.imageReady(photo("a"))
        pass(100)
        assertEquals("a", vm.shown)
    }

    @Test
    fun `a sole video plays again at its end`() = runTest(dispatcher) {
        val vm = show(listOf(video("v")))
        vm.videoPlaying(photo("v"))
        playing(vm)
        assertEquals(0, vm.state.value.run)
        vm.videoEnded(photo("v"))
        pass(100)
        assertEquals("v", vm.shown)
        assertEquals(1, vm.state.value.run)

        // Once only, until it ends again.
        pass(5_000)
        assertEquals(1, vm.state.value.run)
    }

    @Test
    fun `every move raises the run`() = runTest(dispatcher) {
        val vm = loaded("a", "b")
        vm.skipForward()
        vm.skipBack()
        assertEquals(2, vm.state.value.run)
    }

    @Test
    fun `thirty seconds without playing fails a video, and the show goes on`() = runTest(dispatcher) {
        val vm = show(listOf(video("v"), slide("a")))
        playing(vm)
        pass(SlideshowViewModel.VIDEO_STALL_MS - 100)
        assertEquals("v", vm.shown)
        pass(100)
        assertEquals("a", vm.shown)
        assertEquals(1, vm.state.value.slides.size)
    }

    @Test
    fun `a video that stops moving half way fails after thirty seconds too`() = runTest(dispatcher) {
        val vm = show(listOf(video("v"), slide("a")))
        vm.videoPlaying(photo("v"))
        playing(vm)
        pass(60_000)
        assertEquals("v", vm.shown)

        vm.videoWaiting(photo("v"))
        pass(SlideshowViewModel.VIDEO_STALL_MS - 100)
        assertEquals("v", vm.shown)
        pass(100)
        assertEquals("a", vm.shown)
    }

    @Test
    fun `playing restarts the stall clock`() = runTest(dispatcher) {
        val vm = show(listOf(video("v"), slide("a")))
        playing(vm)
        pass(20_000)
        vm.videoPlaying(photo("v"))
        vm.videoWaiting(photo("v"))
        pass(SlideshowViewModel.VIDEO_STALL_MS - 100)
        assertEquals("v", vm.shown)
    }

    @Test
    fun `a pause holds the stall clock`() = runTest(dispatcher) {
        val vm = show(listOf(video("v"), slide("a")))
        playing(vm)
        pass(20_000)
        vm.togglePause()
        pass(60_000)
        assertEquals("v", vm.shown)

        vm.togglePause()
        pass(9_900)
        assertEquals("v", vm.shown)
        pass(200)
        assertEquals("a", vm.shown)
    }

    @Test
    fun `a skip resets the stall clock`() = runTest(dispatcher) {
        val vm = show(listOf(video("v"), video("w")))
        playing(vm)
        pass(20_000)
        vm.skipForward()
        pass(20_000)
        assertEquals("w", vm.shown)
        vm.skipBack()
        pass(SlideshowViewModel.VIDEO_STALL_MS - 100)
        assertEquals("v", vm.shown)
        assertEquals(2, vm.state.value.slides.size)
    }

    @Test
    fun `a video whose preview isn't up doesn't stall`() = runTest(dispatcher) {
        val vm = show(listOf(video("v"), slide("a")), allReady = false)
        playing(vm)
        pass(120_000)
        assertEquals("v", vm.shown)
    }

    @Test
    fun `a dropped video's end doesn't carry over to the next`() = runTest(dispatcher) {
        val vm = show(listOf(video("v"), video("w"), slide("a")))
        vm.videoEnded(photo("v"))
        vm.failCurrent(serverFailure = false)
        playing(vm)
        pass(1_000)
        assertEquals("w", vm.shown)
    }

    @Test
    fun `late reports about another slide change nothing`() = runTest(dispatcher) {
        val vm = show(listOf(video("v"), video("w"), slide("a")))
        vm.videoEnded(photo("w"))
        vm.videoFailed(photo("w"), serverFailure = true)
        vm.videoPlaying(photo("w"))
        playing(vm)
        pass(1_000)
        assertEquals("v", vm.shown)
        assertEquals(3, vm.state.value.slides.size)

        // Its playing didn't count for this one: the stall clock is still running.
        pass(SlideshowViewModel.VIDEO_STALL_MS - 1_000)
        assertEquals("w", vm.shown)
    }

    @Test
    fun `a video this TV can't decode doesn't count towards failing the show`() = runTest(dispatcher) {
        val vm = show(List(7) { video("v$it") })
        repeat(6) { vm.failCurrent(serverFailure = false) }
        assertEquals(SlideshowLoad.Playing, vm.state.value.load)
        assertEquals(1, vm.state.value.slides.size)
    }

    @Test
    fun `when an undecodable video was the last item the show is empty`() = runTest(dispatcher) {
        val vm = show(listOf(video("v")))
        vm.failCurrent(serverFailure = false)
        assertEquals(SlideshowLoad.Empty, vm.state.value.load)
    }

    @Test
    fun `five server failures fail the show`() = runTest(dispatcher) {
        val vm = show(List(6) { video("v$it") })
        repeat(SlideshowViewModel.MAX_FAILURES - 1) { vm.failCurrent(serverFailure = true) }
        assertEquals(SlideshowLoad.Playing, vm.state.value.load)
        vm.failCurrent(serverFailure = true)
        assertEquals(SlideshowLoad.Failed(Problem.Unreachable), vm.state.value.load)
    }

    @Test
    fun `when a server failure was the last item the show has failed`() = runTest(dispatcher) {
        val vm = show(listOf(video("v")))
        vm.failCurrent(serverFailure = true)
        assertEquals(SlideshowLoad.Failed(Problem.Unreachable), vm.state.value.load)
    }

    @Test
    fun `a video's preview doesn't start the count again, a cached one would hide a dead server`() = runTest(dispatcher) {
        val vm = show(List(6) { video("v$it") })
        repeat(4) { vm.failCurrent(serverFailure = true) }
        vm.imageReady(vm.state.value.current!!.photo.id)
        vm.failCurrent(serverFailure = true)
        assertEquals(SlideshowLoad.Failed(Problem.Unreachable), vm.state.value.load)
    }

    @Test
    fun `a video's first frame starts the count again`() = runTest(dispatcher) {
        val vm = show(List(7) { video("v$it") })
        repeat(4) { vm.failCurrent(serverFailure = true) }
        vm.videoPlaying(vm.state.value.current!!.photo.id)
        repeat(2) { vm.failCurrent(serverFailure = true) }
        assertEquals(SlideshowLoad.Playing, vm.state.value.load)
    }

    @Test
    fun `an image no longer in the show starts the count again`() = runTest(dispatcher) {
        val vm = show(List(7) { video("v$it") })
        repeat(4) { vm.failCurrent(serverFailure = true) }
        vm.imageReady(photo("gone"))
        repeat(2) { vm.failCurrent(serverFailure = true) }
        assertEquals(SlideshowLoad.Playing, vm.state.value.load)
    }
}
