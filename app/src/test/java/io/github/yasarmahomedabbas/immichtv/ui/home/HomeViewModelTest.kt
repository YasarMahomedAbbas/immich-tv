package io.github.yasarmahomedabbas.immichtv.ui.home

import io.github.yasarmahomedabbas.immichtv.domain.Problem
import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumId
import io.github.yasarmahomedabbas.immichtv.domain.albums.FakeAlbumRepository
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.domain.albums.album
import io.github.yasarmahomedabbas.immichtv.domain.home.FakeMemoryRepository
import io.github.yasarmahomedabbas.immichtv.domain.home.HomeHero
import io.github.yasarmahomedabbas.immichtv.domain.home.HomePage
import io.github.yasarmahomedabbas.immichtv.domain.home.LoadHome
import io.github.yasarmahomedabbas.immichtv.domain.photos.FakeLibraryRepository
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.FakeSlideshowSettingsStore
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.GetSlideshowSettings
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.SlideshowSettings
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
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val summer = album(id = "s")
    private val albums = FakeAlbumRepository(albums = ServerResult.Ok(listOf(summer)))
    private val library = FakeLibraryRepository()
    private val settings = FakeSlideshowSettingsStore()
    private val page = HomePage(HomeHero.FromAlbum(summer), listOf(summer), emptyList())

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = HomeViewModel(
        LoadHome(albums, FakeMemoryRepository(), library) { LocalDate.of(2026, 10, 30) },
        GetSlideshowSettings(settings),
    )

    @Test
    fun `loading, then the page`() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(HomeLoad.Loading, vm.state.value.load)
        advanceUntilIdle()
        assertEquals(HomeLoad.Loaded(page), vm.state.value.load)
    }

    @Test
    fun `a failure shows, and try again loads`() = runTest(dispatcher) {
        albums.albums = ServerResult.Failed(Problem.Unreachable)
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(HomeLoad.Failed(Problem.Unreachable), vm.state.value.load)

        albums.albums = ServerResult.Ok(listOf(summer))
        vm.retry()
        assertEquals(HomeLoad.Loading, vm.state.value.load)
        advanceUntilIdle()
        assertEquals(HomeLoad.Loaded(page), vm.state.value.load)
    }

    @Test
    fun `try again while loading is ignored`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.retry()
        advanceUntilIdle()
        assertEquals(1, albums.albumsCalls)
    }

    @Test
    fun `a refresh shows what changed, with no skeleton`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        val photos = listOf(Photo(PhotoId("r"), false))
        library.recent = ServerResult.Ok(photos)
        albums.gate = CompletableDeferred()
        vm.refresh()
        runCurrent()
        assertEquals(HomeLoad.Loaded(page), vm.state.value.load)

        albums.gate?.complete(Unit)
        advanceUntilIdle()
        assertEquals(HomeLoad.Loaded(page.copy(recent = photos)), vm.state.value.load)
    }

    @Test
    fun `a refresh that fails keeps the screen`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        albums.albums = ServerResult.Failed(Problem.Unreachable)
        vm.refresh()
        advanceUntilIdle()
        assertEquals(HomeLoad.Loaded(page), vm.state.value.load)
    }

    @Test
    fun `a refresh while one is running or while loading does nothing`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()
        assertEquals(1, albums.albumsCalls)

        albums.gate = CompletableDeferred()
        vm.refresh()
        vm.refresh()
        runCurrent()
        albums.gate?.complete(Unit)
        advanceUntilIdle()
        assertEquals(2, albums.albumsCalls)
    }

    @Test
    fun `a refresh after a failure can bring the page in`() = runTest(dispatcher) {
        albums.albums = ServerResult.Failed(Problem.Unreachable)
        val vm = viewModel()
        advanceUntilIdle()

        albums.albums = ServerResult.Ok(listOf(summer))
        vm.refresh()
        advanceUntilIdle()
        assertEquals(HomeLoad.Loaded(page), vm.state.value.load)
    }

    @Test
    fun `focus starts on play and is remembered, through a refresh`() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(HomeFocus.Play, vm.state.value.focused)
        advanceUntilIdle()

        vm.onFocused(HomeFocus.AlbumCard(summer.id))
        vm.refresh()
        advanceUntilIdle()
        assertEquals(HomeFocus.AlbumCard(summer.id), vm.state.value.focused)
    }

    @Test
    fun `the hero album plays with its saved settings`() = runTest(dispatcher) {
        val saved = SlideshowSettings(secondsPerPhoto = 5)
        settings.saved[summer.id] = saved
        assertEquals(saved, viewModel().heroAlbumSettings(summer))
    }

    @Test
    fun `focus stays on what is still on the page`() {
        val photo = Photo(PhotoId("r"), false)
        val full = page.copy(recent = listOf(photo))
        for (focus in listOf(
            HomeFocus.Play,
            HomeFocus.ViewPhotos,
            HomeFocus.SeeAll,
            HomeFocus.AlbumCard(summer.id),
            HomeFocus.Recent(photo.id),
        )) {
            assertEquals(focus, full.focusTarget(focus))
        }
    }

    @Test
    fun `focus on what has gone goes to play`() {
        assertEquals(HomeFocus.Play, page.focusTarget(HomeFocus.AlbumCard(AlbumId("gone"))))
        assertEquals(HomeFocus.Play, page.focusTarget(HomeFocus.Recent(PhotoId("gone"))))
    }

    @Test
    fun `with no hero focus goes to the first thing shown`() {
        val photo = Photo(PhotoId("r"), false)
        val noHero = HomePage(null, listOf(summer), listOf(photo))
        assertEquals(HomeFocus.AlbumCard(summer.id), noHero.focusTarget(HomeFocus.Play))
        assertEquals(HomeFocus.AlbumCard(summer.id), noHero.focusTarget(HomeFocus.ViewPhotos))

        val onlyRecent = noHero.copy(albums = emptyList())
        assertEquals(HomeFocus.Recent(photo.id), onlyRecent.focusTarget(HomeFocus.SeeAll))
    }

    @Test
    fun `an empty page has nothing to focus`() {
        assertNull(HomePage(null, emptyList(), emptyList()).focusTarget(HomeFocus.Play))
    }
}
