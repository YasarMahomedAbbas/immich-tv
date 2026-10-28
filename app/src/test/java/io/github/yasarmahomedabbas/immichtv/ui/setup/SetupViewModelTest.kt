package io.github.yasarmahomedabbas.immichtv.ui.setup

import io.github.yasarmahomedabbas.immichtv.domain.server.ApiKey
import io.github.yasarmahomedabbas.immichtv.domain.server.ConnectResult
import io.github.yasarmahomedabbas.immichtv.domain.server.ConnectToServer
import io.github.yasarmahomedabbas.immichtv.domain.server.FakeServerConnector
import io.github.yasarmahomedabbas.immichtv.domain.server.FakeSessionStore
import io.github.yasarmahomedabbas.immichtv.domain.server.ServerAddress
import io.github.yasarmahomedabbas.immichtv.domain.server.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
class SetupViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val connector = FakeServerConnector()
    private val store = FakeSessionStore()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(current: Session? = null) = SetupViewModel(ConnectToServer(connector, store), current)

    private fun SetupViewModel.fill() {
        onAddressChange("nas:2283")
        onApiKeyChange("key")
    }

    @Test
    fun `starts empty without a session`() {
        assertEquals(SetupState(), viewModel().state.value)
    }

    @Test
    fun `is prefilled from the stored session`() {
        val vm = viewModel(Session(ServerAddress.parse("https://photos.example.com")!!, ApiKey("k3y")))
        assertEquals("https://photos.example.com", vm.state.value.address)
        assertEquals("k3y", vm.state.value.apiKey)
    }

    @Test
    fun `edits update the fields and the key can be shown`() {
        val vm = viewModel()
        vm.fill()
        vm.toggleKeyVisible()
        assertEquals(SetupState(address = "nas:2283", apiKey = "key", keyVisible = true), vm.state.value)
        vm.toggleKeyVisible()
        assertFalse(vm.state.value.keyVisible)
    }

    @Test
    fun `connect shows connecting, then fires connected once`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.fill()
        val event = async { vm.connected.first() }

        vm.connect()
        assertTrue(vm.state.value.connecting)
        advanceUntilIdle()

        assertFalse(vm.state.value.connecting)
        assertNull(vm.state.value.problem)
        assertTrue(event.isCompleted)
        assertEquals("http://nas:2283", store.session?.server?.url)
    }

    @Test
    fun `each failure shows its problem`() = runTest(dispatcher) {
        val cases = mapOf(
            ConnectResult.Unreachable to SetupProblem.Unreachable,
            ConnectResult.NotPhotoServer to SetupProblem.NotPhotoServer,
            ConnectResult.KeyRejected to SetupProblem.KeyRejected,
            ConnectResult.KeyLacksPermission to SetupProblem.KeyLacksPermission,
        )
        for ((result, problem) in cases) {
            connector.result = { result }
            val vm = viewModel()
            vm.fill()
            vm.connect()
            advanceUntilIdle()
            assertEquals(problem, vm.state.value.problem)
            assertFalse(vm.state.value.connecting)
        }
    }

    @Test
    fun `a server that is too old shows its version, cleared by an edit`() = runTest(dispatcher) {
        connector.result = { ConnectResult.ServerTooOld("1.118.0") }
        val vm = viewModel()
        vm.fill()
        vm.connect()
        advanceUntilIdle()

        assertEquals(SetupProblem.ServerTooOld, vm.state.value.problem)
        assertEquals("1.118.0", vm.state.value.serverVersion)
        vm.onAddressChange("nas2")
        assertNull(vm.state.value.serverVersion)
    }

    @Test
    fun `a bad address or a missing key shows its problem`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onApiKeyChange("key")
        vm.connect()
        advanceUntilIdle()
        assertEquals(SetupProblem.InvalidAddress, vm.state.value.problem)

        vm.onAddressChange("nas")
        vm.onApiKeyChange(" ")
        vm.connect()
        advanceUntilIdle()
        assertEquals(SetupProblem.MissingKey, vm.state.value.problem)
        assertTrue(connector.checked.isEmpty())
    }

    @Test
    fun `editing a field clears the problem`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.connect()
        advanceUntilIdle()
        assertEquals(SetupProblem.InvalidAddress, vm.state.value.problem)
        vm.onAddressChange("n")
        assertNull(vm.state.value.problem)

        vm.connect()
        advanceUntilIdle()
        vm.onApiKeyChange("k")
        assertNull(vm.state.value.problem)
    }

    @Test
    fun `a second connect while connecting is ignored`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.fill()
        vm.connect()
        vm.connect()
        advanceUntilIdle()
        assertEquals(1, connector.checked.size)
    }
}
