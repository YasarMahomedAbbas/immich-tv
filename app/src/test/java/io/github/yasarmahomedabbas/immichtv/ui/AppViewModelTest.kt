package io.github.yasarmahomedabbas.immichtv.ui

import io.github.yasarmahomedabbas.immichtv.domain.server.ApiKey
import io.github.yasarmahomedabbas.immichtv.domain.server.FakeSessionStore
import io.github.yasarmahomedabbas.immichtv.domain.server.GetSession
import io.github.yasarmahomedabbas.immichtv.domain.server.ServerAddress
import io.github.yasarmahomedabbas.immichtv.domain.server.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(stored: Session?) = AppViewModel(GetSession(FakeSessionStore(stored)))

    @Test
    fun `no session opens first-run setup`() = runTest(dispatcher) {
        val vm = viewModel(null)
        assertEquals(AppScreen.Loading, vm.screen.value)
        advanceUntilIdle()
        assertEquals(AppScreen.Setup(firstRun = true, current = null), vm.screen.value)
    }

    @Test
    fun `a stored session opens home`() = runTest(dispatcher) {
        val vm = viewModel(session)
        advanceUntilIdle()
        assertEquals(AppScreen.Home, vm.screen.value)
    }

    @Test
    fun `settings opens setup with the stored session`() = runTest(dispatcher) {
        val vm = viewModel(session)
        advanceUntilIdle()
        vm.openSettings()
        advanceUntilIdle()
        assertEquals(AppScreen.Setup(firstRun = false, current = session), vm.screen.value)
    }

    @Test
    fun `connected goes home`() = runTest(dispatcher) {
        val vm = viewModel(null)
        advanceUntilIdle()
        vm.connected()
        assertEquals(AppScreen.Home, vm.screen.value)
    }

    @Test
    fun `back from setup opened in settings returns home`() = runTest(dispatcher) {
        val vm = viewModel(session)
        advanceUntilIdle()
        vm.openSettings()
        advanceUntilIdle()
        assertTrue(vm.back())
        assertEquals(AppScreen.Home, vm.screen.value)
    }

    @Test
    fun `back on first-run setup and on home is left to the system`() = runTest(dispatcher) {
        val first = viewModel(null)
        advanceUntilIdle()
        assertFalse(first.back())
        assertEquals(AppScreen.Setup(firstRun = true, current = null), first.screen.value)

        val home = viewModel(session)
        advanceUntilIdle()
        assertFalse(home.back())
        assertEquals(AppScreen.Home, home.screen.value)
    }
}
