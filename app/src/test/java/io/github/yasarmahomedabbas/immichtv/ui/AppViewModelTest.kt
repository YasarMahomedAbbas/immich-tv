package io.github.yasarmahomedabbas.immichtv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumId
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.domain.albums.album
import io.github.yasarmahomedabbas.immichtv.domain.home.Memory
import io.github.yasarmahomedabbas.immichtv.domain.server.ApiKey
import io.github.yasarmahomedabbas.immichtv.domain.server.FakeSessionStore
import io.github.yasarmahomedabbas.immichtv.domain.server.GetSession
import io.github.yasarmahomedabbas.immichtv.domain.server.ServerAddress
import io.github.yasarmahomedabbas.immichtv.domain.server.Session
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.Slide
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.SlideshowSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val session = Session(ServerAddress.parse("nas:2283")!!, ApiKey("key"))
    private val home = AppScreen.Main(Tab.Home)
    private val memory = Memory(LocalDate.of(2025, 10, 30), emptyList())

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(stored: Session?) = AppViewModel(GetSession(FakeSessionStore(stored)))

    private fun TestScope.signedIn() = viewModel(session).also { advanceUntilIdle() }

    private class ShowProbe : ViewModel() {
        var cleared = false

        override fun onCleared() {
            cleared = true
        }
    }

    private fun AppViewModel.showProbe() =
        ViewModelProvider(shows, viewModelFactory { initializer { ShowProbe() } })[ShowProbe::class.java]

    @Test
    fun `no session opens first-run setup`() = runTest(dispatcher) {
        val vm = viewModel(null)
        assertEquals(AppScreen.Loading, vm.screen.value)
        advanceUntilIdle()
        assertEquals(AppScreen.Setup(firstRun = true, current = null), vm.screen.value)
    }

    @Test
    fun `a stored session opens the home tab`() = runTest(dispatcher) {
        assertEquals(home, signedIn().screen.value)
    }

    @Test
    fun `selecting a tab shows it`() = runTest(dispatcher) {
        val vm = signedIn()
        vm.selectTab(Tab.Albums)
        assertEquals(AppScreen.Main(Tab.Albums), vm.screen.value)
    }

    @Test
    fun `opening an album shows it, and back returns to albums`() = runTest(dispatcher) {
        val vm = signedIn()
        vm.selectTab(Tab.Albums)
        vm.openAlbum(AlbumId("a1"))
        assertEquals(AppScreen.Album(AlbumId("a1")), vm.screen.value)

        assertTrue(vm.back())
        assertEquals(AppScreen.Main(Tab.Albums), vm.screen.value)
    }

    @Test
    fun `browse photos opens the album's photos, and back returns to the album on browse photos`() = runTest(dispatcher) {
        val vm = signedIn()
        val summer = album(id = "a1")
        vm.selectTab(Tab.Albums)
        vm.openAlbum(summer.id)
        vm.openAlbumPhotos(summer)
        assertEquals(AppScreen.AlbumPhotos(summer), vm.screen.value)

        assertTrue(vm.back())
        assertEquals(AppScreen.Album(summer.id, focusBrowse = true), vm.screen.value)
        assertTrue(vm.back())
        assertEquals(AppScreen.Main(Tab.Albums), vm.screen.value)
    }

    @Test
    fun `start slideshow plays with the settings given, and back returns to the album on start slideshow`() = runTest(dispatcher) {
        val vm = signedIn()
        val summer = album(id = "a1")
        val settings = SlideshowSettings(secondsPerPhoto = 5)
        vm.openAlbum(summer.id)
        vm.openSlideshow(summer, settings)
        assertEquals(AppScreen.Slideshow(summer, settings), vm.screen.value)

        assertTrue(vm.back())
        assertEquals(AppScreen.Album(summer.id, focusBrowse = false), vm.screen.value)
    }

    @Test
    fun `results play as a slideshow, and back returns to search with focus on the results`() = runTest(dispatcher) {
        val vm = signedIn()
        val slides = listOf(Slide(Photo(PhotoId("a"), false), false, null, null))
        vm.selectTab(Tab.Search)
        vm.openResultsSlideshow(slides)
        assertEquals(AppScreen.ResultsSlideshow(slides), vm.screen.value)

        assertTrue(vm.back())
        assertEquals(AppScreen.Main(Tab.Search, focusResults = true), vm.screen.value)

        assertTrue(vm.back())
        assertEquals(home, vm.screen.value)
    }

    @Test
    fun `selecting search again forgets the focus on the results`() = runTest(dispatcher) {
        val vm = signedIn()
        vm.openResultsSlideshow(emptyList())
        vm.back()
        vm.selectTab(Tab.Search)
        assertEquals(AppScreen.Main(Tab.Search), vm.screen.value)
    }

    @Test
    fun `opening a photo shows the viewer, and back returns to the photos tab`() = runTest(dispatcher) {
        val vm = signedIn()
        vm.selectTab(Tab.Photos)
        vm.openPhotoViewer()
        assertEquals(AppScreen.PhotoViewer(), vm.screen.value)

        assertTrue(vm.back())
        assertEquals(AppScreen.Main(Tab.Photos), vm.screen.value)
    }

    @Test
    fun `a slideshow from a photo plays, and back returns to the photos tab`() = runTest(dispatcher) {
        val vm = signedIn()
        vm.selectTab(Tab.Photos)
        vm.openTimelineSlideshow(PhotoId("p1"))
        assertEquals(AppScreen.TimelineSlideshow(PhotoId("p1")), vm.screen.value)

        assertTrue(vm.back())
        assertEquals(AppScreen.Main(Tab.Photos), vm.screen.value)
    }

    @Test
    fun `a card in home's row opens the album, and back returns home`() = runTest(dispatcher) {
        val vm = signedIn()
        vm.openAlbum(AlbumId("a1"), CameFrom.HomeRow)
        assertEquals(AppScreen.Album(AlbumId("a1"), cameFrom = CameFrom.HomeRow), vm.screen.value)

        assertTrue(vm.back())
        assertEquals(home, vm.screen.value)
    }

    @Test
    fun `an album opened from home's row keeps that through its photos and slideshow`() = runTest(dispatcher) {
        val vm = signedIn()
        val summer = album(id = "a1")
        vm.openAlbum(summer.id, CameFrom.HomeRow)
        vm.openAlbumPhotos(summer, CameFrom.HomeRow)
        assertTrue(vm.back())
        assertEquals(AppScreen.Album(summer.id, focusBrowse = true, cameFrom = CameFrom.HomeRow), vm.screen.value)

        vm.openSlideshow(summer, SlideshowSettings(), CameFrom.HomeRow)
        assertTrue(vm.back())
        assertEquals(AppScreen.Album(summer.id, cameFrom = CameFrom.HomeRow), vm.screen.value)

        assertTrue(vm.back())
        assertEquals(home, vm.screen.value)
    }

    @Test
    fun `the hero's album photos go back straight to home`() = runTest(dispatcher) {
        val vm = signedIn()
        vm.openAlbumPhotos(album(id = "a1"), CameFrom.HomeHero)

        assertTrue(vm.back())
        assertEquals(home, vm.screen.value)
    }

    @Test
    fun `the hero's album slideshow goes back straight to home`() = runTest(dispatcher) {
        val vm = signedIn()
        vm.openSlideshow(album(id = "a1"), SlideshowSettings(), CameFrom.HomeHero)

        assertTrue(vm.back())
        assertEquals(home, vm.screen.value)
    }

    @Test
    fun `a memory's slideshow plays, and back returns home`() = runTest(dispatcher) {
        val vm = signedIn()
        vm.openMemorySlideshow(memory)
        assertEquals(AppScreen.MemorySlideshow(memory), vm.screen.value)

        assertTrue(vm.back())
        assertEquals(home, vm.screen.value)
    }

    @Test
    fun `a memory's photos open under its title, and back returns home`() = runTest(dispatcher) {
        val vm = signedIn()
        vm.openMemoryPhotos(memory, "One year ago today")
        assertEquals(AppScreen.MemoryPhotos(memory, "One year ago today"), vm.screen.value)

        assertTrue(vm.back())
        assertEquals(home, vm.screen.value)
    }

    @Test
    fun `a recent photo opens the viewer, and back returns home`() = runTest(dispatcher) {
        val vm = signedIn()
        vm.openPhotoViewer(fromHome = true)
        assertEquals(AppScreen.PhotoViewer(fromHome = true), vm.screen.value)

        assertTrue(vm.back())
        assertEquals(home, vm.screen.value)
    }

    @Test
    fun `back from any other tab goes home, and back on home is left to the system`() = runTest(dispatcher) {
        val vm = signedIn()
        for (tab in listOf(Tab.Albums, Tab.Photos, Tab.Search)) {
            vm.selectTab(tab)
            assertTrue(vm.back())
            assertEquals(home, vm.screen.value)
        }
        assertFalse(vm.back())
        assertEquals(home, vm.screen.value)
    }

    @Test
    fun `settings opens setup with the stored session, and back returns where it came from`() = runTest(dispatcher) {
        val vm = signedIn()
        vm.selectTab(Tab.Albums)
        vm.openSettings()
        advanceUntilIdle()
        assertEquals(AppScreen.Setup(firstRun = false, current = session, from = AppScreen.Main(Tab.Albums)), vm.screen.value)

        assertTrue(vm.back())
        assertEquals(AppScreen.Main(Tab.Albums), vm.screen.value)
    }

    @Test
    fun `connected goes to the home tab`() = runTest(dispatcher) {
        val vm = viewModel(null)
        advanceUntilIdle()
        vm.connected()
        assertEquals(home, vm.screen.value)
    }

    @Test
    fun `connecting starts a new connection, going back from settings doesn't`() = runTest(dispatcher) {
        val vm = viewModel(session)
        advanceUntilIdle()
        val before = vm.connection.value

        vm.openSettings()
        advanceUntilIdle()
        vm.back()
        assertEquals(before, vm.connection.value)

        vm.openSettings()
        advanceUntilIdle()
        vm.connected()
        assertTrue(vm.connection.value != before)
    }

    @Test
    fun `back on first-run setup and while loading is left to the system`() = runTest(dispatcher) {
        val loading = viewModel(null)
        assertFalse(loading.back())

        advanceUntilIdle()
        assertFalse(loading.back())
        assertEquals(AppScreen.Setup(firstRun = true, current = null), loading.screen.value)
    }

    @Test
    fun `leaving a show by Back clears it`() = runTest(dispatcher) {
        val vm = signedIn()
        val starts = listOf<() -> Unit>(
            { vm.openSlideshow(album(id = "a1"), SlideshowSettings()) },
            { vm.openMemorySlideshow(memory) },
            { vm.openResultsSlideshow(emptyList()) },
            { vm.openTimelineSlideshow(PhotoId("p1")) },
        )
        for (start in starts) {
            start()
            val show = vm.showProbe()
            assertFalse(show.cleared)

            assertTrue(vm.back())
            assertTrue(show.cleared)
        }
    }

    @Test
    fun `leaving a show another way clears it`() = runTest(dispatcher) {
        val vm = signedIn()
        vm.openTimelineSlideshow(PhotoId("p1"))
        val show = vm.showProbe()

        vm.selectTab(Tab.Albums)
        assertTrue(show.cleared)
    }

    @Test
    fun `the next show gets a new ViewModel`() = runTest(dispatcher) {
        val vm = signedIn()
        vm.openResultsSlideshow(emptyList())
        val first = vm.showProbe()
        vm.back()

        vm.openResultsSlideshow(emptyList())
        assertNotSame(first, vm.showProbe())
    }

    @Test
    fun `a show asked again while it plays keeps its ViewModel`() = runTest(dispatcher) {
        val vm = signedIn()
        vm.openResultsSlideshow(emptyList())
        val show = vm.showProbe()

        assertSame(show, vm.showProbe())
        assertFalse(show.cleared)
    }

    @Test
    fun `clearing the app clears the show`() = runTest(dispatcher) {
        val store = ViewModelStore()
        val factory = viewModelFactory { initializer { viewModel(session) } }
        val vm = ViewModelProvider(store, factory)[AppViewModel::class.java]
        advanceUntilIdle()
        vm.openResultsSlideshow(emptyList())
        val show = vm.showProbe()

        store.clear()
        assertTrue(show.cleared)
    }
}
