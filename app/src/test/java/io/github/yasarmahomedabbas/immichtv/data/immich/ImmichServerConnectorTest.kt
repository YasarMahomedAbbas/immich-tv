package io.github.yasarmahomedabbas.immichtv.data.immich

import io.github.yasarmahomedabbas.immichtv.domain.server.ApiKey
import io.github.yasarmahomedabbas.immichtv.domain.server.ConnectResult
import io.github.yasarmahomedabbas.immichtv.domain.server.ServerAddress
import io.github.yasarmahomedabbas.immichtv.domain.server.Session
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ImmichServerConnectorTest {
    private val server = MockWebServer()
    private val connector = ImmichServerConnector(immichHttpClient())

    private var ping = MockResponse().setBody("""{"res":"pong"}""")
    private var me = MockResponse().setBody(ME_V3_3_5)

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = when (request.path) {
                "/api/server/ping" -> ping
                "/api/users/me" -> me
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun session(address: String = server.url("/").toString()) =
        Session(ServerAddress.parse(address)!!, ApiKey(KEY))

    private suspend fun check(address: String = server.url("/").toString()) = connector.check(session(address))

    @Test
    fun `a v3_3_5 server and a good key connect`() = runTest {
        assertEquals(ConnectResult.Connected(session(), "Ada Lovelace"), check())
    }

    @Test
    fun `a v1_118 server and a good key connect`() = runTest {
        me = MockResponse().setBody(ME_V1_118)
        assertEquals(ConnectResult.Connected(session(), "Ada Lovelace"), check())
    }

    @Test
    fun `the paths are exact and the key goes only in the users me header`() = runTest {
        check()

        val first = server.takeRequest()
        assertEquals("/api/server/ping", first.path)
        assertNull(first.getHeader("x-api-key"))
        val second = server.takeRequest()
        assertEquals("/api/users/me", second.path)
        assertEquals(KEY, second.getHeader("x-api-key"))
        assertFalse(first.requestUrl.toString().contains(KEY))
        assertFalse(second.requestUrl.toString().contains(KEY))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `an address with a trailing api hits the same paths`() = runTest {
        val result = check(server.url("/api").toString())

        assertEquals(ConnectResult.Connected::class, result::class)
        assertEquals("/api/server/ping", server.takeRequest().path)
        assertEquals("/api/users/me", server.takeRequest().path)
    }

    @Test
    fun `a ping 404 is not immich`() = runTest {
        ping = MockResponse().setResponseCode(404)
        assertEquals(ConnectResult.NotPhotoServer, check())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a ping answered with html is not immich`() = runTest {
        ping = MockResponse().setBody("<html><body>Router login</body></html>")
        assertEquals(ConnectResult.NotPhotoServer, check())
    }

    @Test
    fun `a ping answered with something other than pong is not immich`() = runTest {
        ping = MockResponse().setBody("""{"res":"nope"}""")
        assertEquals(ConnectResult.NotPhotoServer, check())
    }

    @Test
    fun `a ping 500 is unreachable`() = runTest {
        ping = MockResponse().setResponseCode(500)
        assertEquals(ConnectResult.Unreachable, check())
    }

    @Test
    fun `a users me 401 is a rejected key`() = runTest {
        me = MockResponse().setResponseCode(401).setBody("""{"message":"Invalid API key","statusCode":401}""")
        assertEquals(ConnectResult.KeyRejected, check())
    }

    @Test
    fun `a users me 403 is a key without permission`() = runTest {
        me = MockResponse().setResponseCode(403).setBody("""{"message":"Missing required permission: user.read"}""")
        assertEquals(ConnectResult.KeyLacksPermission, check())
    }

    @Test
    fun `a users me 500 is unreachable`() = runTest {
        me = MockResponse().setResponseCode(500)
        assertEquals(ConnectResult.Unreachable, check())
    }

    @Test
    fun `a users me body without a name is not immich`() = runTest {
        me = MockResponse().setBody("""{"id":"1"}""")
        assertEquals(ConnectResult.NotPhotoServer, check())
    }

    @Test
    fun `a users me redirect is not immich`() = runTest {
        me = MockResponse().setResponseCode(304)
        assertEquals(ConnectResult.NotPhotoServer, check())
    }

    @Test
    fun `a redirect to another server does not carry the key`() = runTest {
        val elsewhere = MockWebServer().apply {
            enqueue(MockResponse().setBody(ME_V3_3_5))
            start()
        }
        try {
            me = MockResponse().setResponseCode(302).setHeader("Location", elsewhere.url("/api/users/me"))

            check()

            val followed = elsewhere.takeRequest()
            assertEquals("/api/users/me", followed.path)
            assertNull(followed.getHeader("x-api-key"))
        } finally {
            elsewhere.shutdown()
        }
    }

    @Test
    fun `a redirect on the same server keeps the key`() = runTest {
        var redirected = false
        val dispatcher = server.dispatcher
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) =
                if (request.path == "/api/users/me" && !redirected) {
                    redirected = true
                    MockResponse().setResponseCode(302).setHeader("Location", "/api/users/me")
                } else {
                    dispatcher.dispatch(request)
                }
        }

        assertEquals(ConnectResult.Connected::class, check()::class)

        server.takeRequest()
        server.takeRequest()
        assertEquals(KEY, server.takeRequest().getHeader("x-api-key"))
    }

    @Test
    fun `a server that is gone is unreachable`() = runTest {
        val address = server.url("/").toString()
        server.shutdown()
        assertEquals(ConnectResult.Unreachable, check(address))
    }

    private companion object {
        const val KEY = "k3y-Secret-0123456789"

        /** `UserAdminResponseDto` as v3.3.5 sends it, with the v3-only `clusterGroupId`. */
        val ME_V3_3_5 = """
            {
              "avatarColor": "amber",
              "clusterGroupId": null,
              "createdAt": "2024-01-01T00:00:00.000Z",
              "deletedAt": null,
              "email": "ada@example.com",
              "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
              "isAdmin": true,
              "license": {
                "activatedAt": "2024-01-01T00:00:00.000Z",
                "activationKey": "abc",
                "licenseKey": "IMSV-AAAA-BBBB-CCCC-DDDD-EEEE-FFFF-GGGG-HHHH"
              },
              "name": "Ada Lovelace",
              "oauthId": "",
              "profileChangedAt": "2024-01-01T00:00:00.000Z",
              "profileImagePath": "",
              "quotaSizeInBytes": null,
              "quotaUsageInBytes": 123456,
              "shouldChangePassword": false,
              "status": "active",
              "storageLabel": "admin",
              "updatedAt": "2024-06-01T00:00:00.000Z"
            }
        """.trimIndent()

        /** The same user as v1.118 sends it: no `clusterGroupId`, no license. */
        val ME_V1_118 = """
            {
              "avatarColor": "primary",
              "createdAt": "2024-01-01T00:00:00.000Z",
              "deletedAt": null,
              "email": "ada@example.com",
              "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
              "isAdmin": false,
              "license": null,
              "name": "Ada Lovelace",
              "oauthId": "",
              "profileChangedAt": "2024-01-01T00:00:00.000Z",
              "profileImagePath": "",
              "quotaSizeInBytes": null,
              "quotaUsageInBytes": 0,
              "shouldChangePassword": false,
              "status": "active",
              "storageLabel": null,
              "updatedAt": "2024-06-01T00:00:00.000Z"
            }
        """.trimIndent()
    }
}
