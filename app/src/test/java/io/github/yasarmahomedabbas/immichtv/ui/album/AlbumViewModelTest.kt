package io.github.yasarmahomedabbas.immichtv.ui.album

import io.github.yasarmahomedabbas.immichtv.domain.Problem
import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumContents
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumId
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumPage
import io.github.yasarmahomedabbas.immichtv.domain.albums.FakeAlbumRepository
import io.github.yasarmahomedabbas.immichtv.domain.albums.LoadAlbum
import io.github.yasarmahomedabbas.immichtv.domain.albums.album
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.FakeSlideshowSettingsStore
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.GetSlideshowSettings
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.SaveSlideshowSettings
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.SlideshowOrder
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.SlideshowSettings
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.VideoMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val summer = album(id = "a1")
    private val repo = FakeAlbumRepository(albums = ServerResult.Ok(listOf(summer)))
    private val store = FakeSlideshowSettingsStore()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(id: String = "a1") =
        AlbumViewModel(AlbumId(id), LoadAlbum(repo), GetSlideshowSettings(store), SaveSlideshowSettings(store))

    @Test
    fun `loading, then the album and its contents`() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(AlbumLoad.Loading, vm.state.value.load)
        advanceUntilIdle()
        assertEquals(AlbumLoad.Loaded(AlbumPage(summer, AlbumContents(3, 1, emptyList()))), vm.state.value.load)
    }

    @Test
    fun `a failure shows, and try again loads`() = runTest(dispatcher) {
        repo.albumFailure = Problem.KeyRejected
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(AlbumLoad.Failed(Problem.KeyRejected), vm.state.value.load)

        repo.albumFailure = null
        vm.retry()
        assertEquals(AlbumLoad.Loading, vm.state.value.load)
        advanceUntilIdle()
        assertEquals(AlbumLoad.Loaded::class, vm.state.value.load::class)
    }

    @Test
    fun `try again while loading is ignored`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.retry()
        advanceUntilIdle()
        assertEquals(listOf(30), repo.contentsLimits)
    }

    @Test
    fun `settings load from the store, even when the album fails`() = runTest(dispatcher) {
        val saved = SlideshowSettings(secondsPerPhoto = 20, order = SlideshowOrder.Shuffle)
        store.saved[AlbumId("a1")] = saved
        repo.albumFailure = Problem.Unreachable
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(saved, vm.state.value.settings)
    }

    @Test
    fun `each change is shown and saved for this album only`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.cycleSeconds()
        vm.cycleOrder()
        vm.cycleVideos()
        vm.toggleDateAndPlace()
        advanceUntilIdle()

        val expected = SlideshowSettings(12, SlideshowOrder.Shuffle, VideoMode.PlayWithSound, showDateAndPlace = false)
        assertEquals(expected, vm.state.value.settings)
        assertEquals(4, store.saves.size)
        assertEquals(setOf(AlbumId("a1")), store.saves.map { it.first }.toSet())
        assertEquals(expected, store.saved[AlbumId("a1")])
        assertEquals(SlideshowSettings(), store.load(AlbumId("other")))
    }

    /** Every load state the screen sees from here on. */
    private fun TestScope.loadsOf(vm: AlbumViewModel): List<AlbumLoad> {
        val seen = mutableListOf<AlbumLoad>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { seen += it.load } }
        return seen
    }

    @Test
    fun `a refresh of an unchanged album keeps the state and doesn't read contents`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val before = vm.state.value
        val loads = loadsOf(vm)

        vm.refresh()
        advanceUntilIdle()

        assertSame(before, vm.state.value)
        assertEquals(1, loads.size)
        assertEquals(2, repo.albumCalls)
        assertEquals(listOf(30), repo.contentsLimits)
    }

    @Test
    fun `a refresh of a changed album updates the page without passing through loading`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val loads = loadsOf(vm)

        val changed = album(id = "a1", itemCount = 11)
        val contents = AlbumContents(4, 1, emptyList())
        repo.albums = ServerResult.Ok(listOf(changed))
        repo.contents = ServerResult.Ok(contents)
        vm.refresh()
        advanceUntilIdle()

        assertEquals(AlbumLoad.Loaded(AlbumPage(changed, contents)), vm.state.value.load)
        assertFalse(AlbumLoad.Loading in loads)
        assertEquals(listOf(30, 30), repo.contentsLimits)
    }

    @Test
    fun `a refresh that fails keeps the page`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val before = vm.state.value

        repo.albumFailure = Problem.Unreachable
        vm.refresh()
        advanceUntilIdle()

        assertSame(before, vm.state.value)
    }

    @Test
    fun `a refresh whose contents fail keeps the page`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val before = vm.state.value

        repo.albums = ServerResult.Ok(listOf(album(id = "a1", itemCount = 11)))
        repo.contents = ServerResult.Failed(Problem.Unreachable)
        vm.refresh()
        advanceUntilIdle()

        assertSame(before, vm.state.value)
    }

    @Test
    fun `a failed screen recovers on refresh without passing through loading`() = runTest(dispatcher) {
        repo.albumFailure = Problem.Unreachable
        val vm = viewModel()
        advanceUntilIdle()
        val loads = loadsOf(vm)

        vm.refresh()
        advanceUntilIdle()
        assertEquals(AlbumLoad.Failed(Problem.Unreachable), vm.state.value.load)

        repo.albumFailure = null
        vm.refresh()
        advanceUntilIdle()

        assertEquals(AlbumLoad.Loaded(AlbumPage(summer, AlbumContents(3, 1, emptyList()))), vm.state.value.load)
        assertFalse(AlbumLoad.Loading in loads)
    }

    @Test
    fun `a refresh during the first load is ignored`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()
        assertEquals(1, repo.albumCalls)
    }

    @Test
    fun `a second refresh while one runs is ignored`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        val gate = CompletableDeferred<Unit>().also { repo.gate = it }
        vm.refresh()
        runCurrent()
        vm.refresh()
        runCurrent()
        assertEquals(2, repo.albumCalls)
        gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `settings changed before a refresh survive it`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.cycleSeconds()
        val settings = vm.state.value.settings

        repo.albums = ServerResult.Ok(listOf(album(id = "a1", itemCount = 11)))
        vm.refresh()
        advanceUntilIdle()

        assertEquals(settings, vm.state.value.settings)
        assertEquals(11, (vm.state.value.load as AlbumLoad.Loaded).page.album.itemCount)
    }

    @Test
    fun `an older refresh can't overwrite what try again loaded`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        val gate = CompletableDeferred<Unit>().also { repo.gate = it }
        vm.refresh()
        runCurrent()

        repo.gate = null
        val newer = album(id = "a1", itemCount = 11)
        repo.albums = ServerResult.Ok(listOf(newer))
        vm.retry()
        advanceUntilIdle()

        repo.albums = ServerResult.Ok(listOf(album(id = "a1", itemCount = 99)))
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(newer, (vm.state.value.load as AlbumLoad.Loaded).page.album)
    }

    private fun summary(photos: Int, videos: Int, start: String?, end: String?) =
        albumSummary(photos, videos, start?.let { LocalDate.parse(it.take(10)) }, end?.let { LocalDate.parse(it.take(10)) }, Locale.ENGLISH)

    @Test
    fun `summary with photos, videos and a range in one year`() {
        assertEquals(
            "1,214 photos · 12 videos · June – August 2025",
            summary(1214, 12, "2025-06-03T10:00:00Z", "2025-08-28T10:00:00Z"),
        )
    }

    @Test
    fun `summary drops zero videos and zero photos, and says one in the singular`() {
        assertEquals("214 photos · June 2025", summary(214, 0, "2025-06-03T10:00:00Z", "2025-06-28T10:00:00Z"))
        assertEquals("1 video · June 2025", summary(0, 1, "2025-06-03T10:00:00Z", "2025-06-03T10:00:00Z"))
        assertEquals("1 photo", summary(1, 0, null, null))
    }

    @Test
    fun `summary across years names both`() {
        assertEquals(
            "3 photos · December 2024 – January 2025",
            summary(3, 0, "2024-12-30T10:00:00Z", "2025-01-02T10:00:00Z"),
        )
    }

    @Test
    fun `summary with one date or none`() {
        assertEquals("2 photos · June 2025", summary(2, 0, "2025-06-03T10:00:00Z", null))
        assertEquals("2 photos · June 2025", summary(2, 0, null, "2025-06-03T10:00:00Z"))
        assertEquals("", summary(0, 0, null, null))
    }

    @Test
    fun `summary puts swapped dates in order`() {
        assertEquals("2 photos · June – August 2025", summary(2, 0, "2025-08-03T10:00:00Z", "2025-06-03T10:00:00Z"))
    }
}
