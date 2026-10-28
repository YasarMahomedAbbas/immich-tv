package io.github.yasarmahomedabbas.immichtv.domain.server

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectToServerTest {
    private val connector = FakeServerConnector()
    private val store = FakeSessionStore()
    private val connect = ConnectToServer(connector, store)

    @Test
    fun `an invalid address is refused without a call`() = runTest {
        assertEquals(ConnectResult.InvalidAddress, connect("ftp://nas", "key"))
        assertTrue(connector.checked.isEmpty())
        assertNull(store.session)
    }

    @Test
    fun `a blank key is refused without a call`() = runTest {
        assertEquals(ConnectResult.MissingKey, connect("nas:2283", "   "))
        assertTrue(connector.checked.isEmpty())
        assertNull(store.session)
    }

    @Test
    fun `connected saves the session with the trimmed key`() = runTest {
        val result = connect(" nas:2283/ ", " key \n")

        val expected = Session(ServerAddress.parse("http://nas:2283")!!, ApiKey("key"))
        assertEquals(ConnectResult.Connected(expected, "Ada"), result)
        assertEquals(listOf(expected), connector.checked)
        assertEquals(expected, store.session)
    }

    @Test
    fun `a failure saves nothing`() = runTest {
        val failures = listOf(
            ConnectResult.Unreachable,
            ConnectResult.NotPhotoServer,
            ConnectResult.KeyRejected,
            ConnectResult.KeyLacksPermission,
        )
        for (failure in failures) {
            connector.result = { failure }
            assertEquals(failure, connect("nas:2283", "key"))
        }
        assertEquals(0, store.saves)
        assertNull(store.session)
    }

    @Test
    fun `get session reads the store`() = runTest {
        assertNull(GetSession(store)())
        val session = Session(ServerAddress.parse("nas")!!, ApiKey("k"))
        store.session = session
        assertEquals(session, GetSession(store)())
    }
}
