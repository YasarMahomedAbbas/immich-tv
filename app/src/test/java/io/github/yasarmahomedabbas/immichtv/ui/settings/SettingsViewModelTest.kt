package io.github.yasarmahomedabbas.immichtv.ui.settings

import io.github.yasarmahomedabbas.immichtv.domain.Problem
import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumId
import io.github.yasarmahomedabbas.immichtv.domain.albums.FakeAlbumRepository
import io.github.yasarmahomedabbas.immichtv.domain.albums.LoadAlbums
import io.github.yasarmahomedabbas.immichtv.domain.albums.album
import io.github.yasarmahomedabbas.immichtv.domain.server.ApiKey
import io.github.yasarmahomedabbas.immichtv.domain.server.FakeSessionStore
import io.github.yasarmahomedabbas.immichtv.domain.server.GetSession
import io.github.yasarmahomedabbas.immichtv.domain.server.ServerAddress
import io.github.yasarmahomedabbas.immichtv.domain.server.Session
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.FakeScreensaverSettingsStore
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.GetScreensaverSource
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.SaveScreensaverSource
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.ScreensaverSource
import io.github.yasarmahomedabbas.immichtv.ui.albums.AlbumsLoad
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val summer = album(id = "s", name = "Summer")
    private val alps = album(id = "a", name = "Alps")
    private val repo = FakeAlbumRepository(albums = ServerResult.Ok(listOf(summer, alps)))
    private val store = FakeScreensaverSettingsStore()
    private val sessions = FakeSessionStore(Session(ServerAddress.parse("nas:2283")!!, ApiKey("key")))

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = SettingsViewModel(GetScreensaverSource(store), SaveScreensaverSource(store), GetSession(sessions), LoadAlbums(repo))

    @Test
    fun `loads the source, the address and the albums`() = runTest(dispatcher) {
        store.source = ScreensaverSource.Album(summer.id)
        val vm = viewModel()
        assertEquals(AlbumsLoad.Loading, vm.state.value.albums)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(ScreensaverSource.Album(summer.id), state.source)
        assertEquals("http://nas:2283", state.address)
        assertEquals(listOf(alps, summer), state.choices)
    }

    @Test
    fun `no session has no address`() = runTest(dispatcher) {
        sessions.session = null
        val vm = viewModel()
        advanceUntilIdle()
        assertNull(vm.state.value.address)
    }

    @Test
    fun `a failure shows, and try again loads`() = runTest(dispatcher) {
        repo.albums = ServerResult.Failed(Problem.Unreachable)
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(AlbumsLoad.Failed(Problem.Unreachable), vm.state.value.albums)

        repo.albums = ServerResult.Ok(listOf(summer))
        val gate = CompletableDeferred<Unit>().also { repo.gate = it }
        vm.retry()
        runCurrent()
        assertEquals(AlbumsLoad.Loading, vm.state.value.albums)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(AlbumsLoad.Loaded(listOf(summer)), vm.state.value.albums)
    }

    @Test
    fun `reading again keeps the list on screen, also when it fails`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val loaded = vm.state.value.albums

        repo.albums = ServerResult.Failed(Problem.Unreachable)
        vm.load()
        runCurrent()
        assertEquals(loaded, vm.state.value.albums)
        advanceUntilIdle()
        assertEquals(loaded, vm.state.value.albums)
    }

    @Test
    fun `loading while a load runs is ignored`() = runTest(dispatcher) {
        repo.gate = CompletableDeferred()
        val vm = viewModel()
        runCurrent()
        vm.load()
        vm.retry()
        repo.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, repo.albumsCalls)
    }

    @Test
    fun `choosing saves and shows it`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.choose(ScreensaverSource.Album(alps.id))
        assertEquals(ScreensaverSource.Album(alps.id), vm.state.value.source)
        advanceUntilIdle()
        assertEquals(ScreensaverSource.Album(alps.id), store.source)
        assertEquals(1, store.saves)

        vm.choose(ScreensaverSource.Library)
        advanceUntilIdle()
        assertEquals(ScreensaverSource.Library, store.source)
    }

    @Test
    fun `the value says random, the album's name, or that it is gone`() {
        val loaded = AlbumsLoad.Loaded(listOf(summer))
        assertEquals(SourceValue.Random, SettingsState(albums = loaded).sourceValue)
        assertEquals(SourceValue.Named("Summer"), SettingsState(ScreensaverSource.Album(summer.id), albums = loaded).sourceValue)
        assertEquals(SourceValue.AlbumGone, SettingsState(ScreensaverSource.Album(AlbumId("x")), albums = loaded).sourceValue)
    }

    @Test
    fun `an album isn't called gone before the albums are known`() {
        val album = ScreensaverSource.Album(summer.id)
        assertEquals(SourceValue.UnnamedAlbum, SettingsState(album).sourceValue)
        assertEquals(SourceValue.UnnamedAlbum, SettingsState(album, albums = AlbumsLoad.Failed(Problem.Unreachable)).sourceValue)
    }

    @Test
    fun `try again taking its button away asks for focus on a row, also with the random row selected`() {
        // Failed to Loading has to change enterRow, or the screen's effect doesn't run and nothing has focus.
        assertNull(SettingsState(albums = AlbumsLoad.Failed(Problem.Unreachable)).enterRow)
        assertEquals(0, SettingsState(albums = AlbumsLoad.Loading).enterRow)
        assertEquals(0, SettingsState(albums = AlbumsLoad.Loaded(listOf(summer))).enterRow)
        assertEquals(1, SettingsState(ScreensaverSource.Album(summer.id), albums = AlbumsLoad.Loaded(listOf(summer))).enterRow)
    }

    @Test
    fun `focus enters on the selected row, or the first if it's gone`() {
        val loaded = AlbumsLoad.Loaded(listOf(summer, alps))
        assertEquals(0, SettingsState(albums = loaded).selectedRow)
        assertEquals(1, SettingsState(ScreensaverSource.Album(alps.id), albums = loaded).selectedRow)
        assertEquals(2, SettingsState(ScreensaverSource.Album(summer.id), albums = loaded).selectedRow)
        assertEquals(0, SettingsState(ScreensaverSource.Album(AlbumId("x")), albums = loaded).selectedRow)
        assertEquals(0, SettingsState(ScreensaverSource.Album(summer.id)).selectedRow)
    }
}
