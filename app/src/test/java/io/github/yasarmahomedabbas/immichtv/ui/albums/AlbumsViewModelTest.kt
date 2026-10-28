package io.github.yasarmahomedabbas.immichtv.ui.albums

import io.github.yasarmahomedabbas.immichtv.domain.Problem
import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumFilter
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumId
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumSort
import io.github.yasarmahomedabbas.immichtv.domain.albums.FakeAlbumRepository
import io.github.yasarmahomedabbas.immichtv.domain.albums.LoadAlbums
import io.github.yasarmahomedabbas.immichtv.domain.albums.album
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val beach = album(id = "b", name = "Beach", itemCount = 5, updatedAt = "2025-03-01T00:00:00Z")
    private val alps = album(id = "a", name = "Alps", itemCount = 50, updatedAt = "2025-01-01T00:00:00Z")
    private val wedding = album(id = "w", name = "Wedding", itemCount = 20, ownedByMe = false, updatedAt = "2025-02-01T00:00:00Z")
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
}
