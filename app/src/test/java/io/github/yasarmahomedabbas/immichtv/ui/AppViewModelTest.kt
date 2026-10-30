package io.github.yasarmahomedabbas.immichtv.ui

import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumId
import io.github.yasarmahomedabbas.immichtv.domain.albums.album
import io.github.yasarmahomedabbas.immichtv.domain.server.ApiKey
import io.github.yasarmahomedabbas.immichtv.domain.server.FakeSessionStore
import io.github.yasarmahomedabbas.immichtv.domain.server.GetSession
import io.github.yasarmahomedabbas.immichtv.domain.server.ServerAddress
import io.github.yasarmahomedabbas.immichtv.domain.server.Session
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val session = Session(ServerAddress.parse("nas:2283")!!, ApiKey("key"))
    private val home = AppScreen.Main(Tab.Home)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(stored: Session?) = AppViewModel(GetSession(FakeSessionStore(stored)))

    private fun TestScope.signedIn() = viewModel(session).also { advanceUntilIdle() }

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
}
