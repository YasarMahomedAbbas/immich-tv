package io.github.yasarmahomedabbas.immichtv.data.immich

import io.github.yasarmahomedabbas.immichtv.domain.server.ConnectResult
import io.github.yasarmahomedabbas.immichtv.domain.server.ServerConnector
import io.github.yasarmahomedabbas.immichtv.domain.server.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Checks a session against Immich v1.118+: `GET /api/server/ping` (no auth) proves it is an
 * Immich server, then `GET /api/users/me` with the `x-api-key` header proves the key works.
 */
internal class ImmichServerConnector(private val client: OkHttpClient) : ServerConnector {

    override suspend fun check(session: Session): ConnectResult = withContext(Dispatchers.IO) {
        try {
            ping(session) ?: me(session)
        } catch (_: IOException) {
            ConnectResult.Unreachable
        } catch (_: IllegalArgumentException) {
            // The address passed the domain's checks but OkHttp can't build a URL from it.
            ConnectResult.Unreachable
        }
    }

    /** Null when the server answered as Immich; otherwise why not. */
    private fun ping(session: Session): ConnectResult? {
        val request = Request.Builder().url(apiUrl(session.server, "server/ping")).get().build()
        return client.newCall(request).execute().use { response ->
            when {
                response.code >= 500 -> ConnectResult.Unreachable
                response.code != 200 -> ConnectResult.NotPhotoServer
                parse<PingDto>(response.body?.string())?.res == "pong" -> null
                else -> ConnectResult.NotPhotoServer
            }
        }
    }

    private fun me(session: Session): ConnectResult {
        val request = Request.Builder()
            .url(apiUrl(session.server, "users/me"))
            .header("x-api-key", session.apiKey.value)
            .get()
            .build()
        return client.newCall(request).execute().use { response ->
            when (response.code) {
                200 -> parse<MeDto>(response.body?.string())
                    ?.let { ConnectResult.Connected(session, it.name) }
                    ?: ConnectResult.NotPhotoServer
                401 -> ConnectResult.KeyRejected
                403 -> ConnectResult.KeyLacksPermission
                in 500..599 -> ConnectResult.Unreachable
                else -> ConnectResult.NotPhotoServer
            }
        }
    }

    private inline fun <reified T> parse(body: String?): T? = try {
        body?.let { immichJson.decodeFromString<T>(it) }
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    /** `ServerPingResponse`. */
    @Serializable
    private data class PingDto(val res: String)

    /** The part of `UserAdminResponseDto` the app reads; `name` is required in every version. */
    @Serializable
    private data class MeDto(val name: String)
}
