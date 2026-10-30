package io.github.yasarmahomedabbas.immichtv.ui.albums

import io.github.yasarmahomedabbas.immichtv.domain.Problem
import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumFilter
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumId
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumSort
import io.github.yasarmahomedabbas.immichtv.domain.albums.FakeAlbumRepository
import io.github.yasarmahomedabbas.immichtv.domain.albums.LoadAlbums
import io.github.yasarmahomedabbas.immichtv.domain.albums.album
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val beach = album(id = "b", name = "Beach", itemCount = 5, updatedAt = "2025-03-01T00:00:00Z")
    private val alps = album(id = "a", name = "Alps", itemCount = 50, updatedAt = "2025-01-01T00:00:00Z")
    private val wedding = album(id = "w", name = "Wedding", itemCount = 20, ownedByMe = false, updatedAt = "2025-02-01T00:00:00Z")
    private val snow = album(id = "s", name = "Snow", itemCount = 1, updatedAt = "2025-04-01T00:00:00Z")
    private val repo = FakeAlbumRepository(albums = ServerResult.Ok(listOf(alps, wedding, beach)))

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = AlbumsViewModel(LoadAlbums(repo))

    private fun AlbumsViewModel.visibleIds() = state.value.visible.map { it.id.value }

    @Test
    fun `loading, then the albums by recently updated`() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(AlbumsLoad.Loading, vm.state.value.load)
        assertTrue(vm.state.value.visible.isEmpty())
        advanceUntilIdle()
        assertEquals(AlbumsLoad.Loaded(listOf(alps, wedding, beach)), vm.state.value.load)
        assertEquals(listOf("b", "w", "a"), vm.visibleIds())
    }

    @Test
    fun `no albums at all is loaded and empty`() = runTest(dispatcher) {
        repo.albums = ServerResult.Ok(emptyList())
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(AlbumsLoad.Loaded(emptyList()), vm.state.value.load)
    }

    @Test
    fun `a failure shows, and try again loads`() = runTest(dispatcher) {
        repo.albums = ServerResult.Failed(Problem.Unreachable)
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(AlbumsLoad.Failed(Problem.Unreachable), vm.state.value.load)

        repo.albums = ServerResult.Ok(listOf(beach))
        vm.retry()
        assertEquals(AlbumsLoad.Loading, vm.state.value.load)
        advanceUntilIdle()
        assertEquals(AlbumsLoad.Loaded(listOf(beach)), vm.state.value.load)
        assertEquals(2, repo.albumsCalls)
    }

    @Test
    fun `try again while loading is ignored`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.retry()
        advanceUntilIdle()
        assertEquals(1, repo.albumsCalls)
    }

    @Test
    fun `filter and sort change what shows without loading again`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.setFilter(AlbumFilter.Mine)
        assertEquals(listOf("b", "a"), vm.visibleIds())
        vm.setFilter(AlbumFilter.SharedWithMe)
        assertEquals(listOf("w"), vm.visibleIds())
        vm.setFilter(AlbumFilter.All)

        vm.cycleSort()
        assertEquals(AlbumSort.Name, vm.state.value.sort)
        assertEquals(listOf("a", "b", "w"), vm.visibleIds())
        vm.cycleSort()
        assertEquals(AlbumSort.MostItems, vm.state.value.sort)
        assertEquals(listOf("a", "w", "b"), vm.visibleIds())
        vm.cycleSort()
        assertEquals(AlbumSort.RecentlyUpdated, vm.state.value.sort)
        assertEquals(1, repo.albumsCalls)
    }

    @Test
    fun `the focused album is remembered`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onFocused(AlbumId("w"))
        assertEquals(AlbumId("w"), vm.state.value.focused)
    }

    /** Every load state the screen sees from here on. */
    private fun TestScope.loadsOf(vm: AlbumsViewModel): List<AlbumsLoad> {
        val seen = mutableListOf<AlbumsLoad>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { seen += it.load } }
        return seen
    }

    @Test
    fun `a refresh shows a new album without passing through loading`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val loads = loadsOf(vm)

        repo.albums = ServerResult.Ok(listOf(alps, wedding, beach, snow))
        vm.refresh()
        advanceUntilIdle()

        assertEquals(listOf("s", "b", "w", "a"), vm.visibleIds())
        assertFalse(AlbumsLoad.Loading in loads)
        assertEquals(2, loads.size)
    }

    @Test
    fun `a refresh with the same albums leaves the same state`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val before = vm.state.value
        val loads = loadsOf(vm)

        repo.albums = ServerResult.Ok(listOf(alps, wedding, beach))
        vm.refresh()
        advanceUntilIdle()

        assertSame(before, vm.state.value)
        assertEquals(1, loads.size)
        assertEquals(2, repo.albumsCalls)
    }

    @Test
    fun `a refresh that fails keeps what's loaded`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val before = vm.state.value

        repo.albums = ServerResult.Failed(Problem.Unreachable)
        vm.refresh()
        advanceUntilIdle()

        assertSame(before, vm.state.value)
    }

    @Test
    fun `a failed screen recovers on refresh without passing through loading`() = runTest(dispatcher) {
        repo.albums = ServerResult.Failed(Problem.Unreachable)
        val vm = viewModel()
        advanceUntilIdle()
        val loads = loadsOf(vm)

        vm.refresh()
        advanceUntilIdle()
        assertEquals(AlbumsLoad.Failed(Problem.Unreachable), vm.state.value.load)

        repo.albums = ServerResult.Ok(listOf(beach))
        vm.refresh()
        advanceUntilIdle()

        assertEquals(AlbumsLoad.Loaded(listOf(beach)), vm.state.value.load)
        assertFalse(AlbumsLoad.Loading in loads)
    }

    @Test
    fun `a refresh during the first load is ignored`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()
        assertEquals(1, repo.albumsCalls)
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
        assertEquals(2, repo.albumsCalls)

        gate.complete(Unit)
        advanceUntilIdle()
        vm.refresh()
        advanceUntilIdle()
        assertEquals(3, repo.albumsCalls)
    }

    @Test
    fun `a refresh keeps the filter, the sort and the focused album`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.setFilter(AlbumFilter.Mine)
        vm.cycleSort()
        vm.onFocused(AlbumId("a"))

        repo.albums = ServerResult.Ok(listOf(alps, beach, snow))
        vm.refresh()
        advanceUntilIdle()

        assertEquals(AlbumFilter.Mine, vm.state.value.filter)
        assertEquals(AlbumSort.Name, vm.state.value.sort)
        assertEquals(AlbumId("a"), vm.state.value.focused)
        assertEquals(listOf("a", "b", "s"), vm.visibleIds())
    }

    @Test
    fun `an older refresh can't overwrite what try again loaded`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        val gate = CompletableDeferred<Unit>().also { repo.gate = it }
        vm.refresh()
        runCurrent()

        repo.gate = null
        repo.albums = ServerResult.Ok(listOf(beach))
        vm.retry()
        advanceUntilIdle()
        assertEquals(AlbumsLoad.Loaded(listOf(beach)), vm.state.value.load)

        repo.albums = ServerResult.Ok(listOf(alps))
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(AlbumsLoad.Loaded(listOf(beach)), vm.state.value.load)
    }
}
