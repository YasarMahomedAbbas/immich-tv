package io.github.yasarmahomedabbas.immichtv.data.immich

import io.github.yasarmahomedabbas.immichtv.domain.server.ServerAddress
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * The one HTTP client for Immich. Short timeouts so an unreachable server fails fast, and
 * deliberately no logging interceptor: the API key travels in a header and must never be logged.
 */
internal fun immichHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .build()

/** Immich adds fields between releases; reading only what we need keeps older and newer servers working. */
internal val immichJson = Json { ignoreUnknownKeys = true }

/**
 * `<server>/api/<path>`. An address typed with a trailing `/api` (as the Immich mobile app
 * shows it) gets the same URL. Throws [IllegalArgumentException] if the address isn't a URL
 * OkHttp accepts.
 */
internal fun apiUrl(server: ServerAddress, path: String): HttpUrl {
    val base = server.url.removeSuffix("/api").trimEnd('/')
    return "$base/api/$path".toHttpUrl()
}
