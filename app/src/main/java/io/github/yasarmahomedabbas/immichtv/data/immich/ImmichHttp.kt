package io.github.yasarmahomedabbas.immichtv.data.immich

import io.github.yasarmahomedabbas.immichtv.domain.server.ServerAddress
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * The one HTTP client for Immich. Short timeouts so an unreachable server fails fast, and
 * deliberately no logging interceptor: the API key travels in a header and must never be logged.
 */
internal fun immichHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .addNetworkInterceptor(KeepKeyOnServer)
    .build()

internal const val API_KEY_HEADER = "x-api-key"

/**
 * OkHttp follows redirects and, across hosts, strips only `Authorization`: a custom key header
 * would go wherever a redirect points. This sees every hop and drops the key from any hop that
 * left the server the call was made to. Staying on the same host is fine, including the usual
 * http → https upgrade by a reverse proxy, but not a downgrade to http.
 */
private object KeepKeyOnServer : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val hop = chain.request()
        val origin = chain.call().request().url
        if (hop.header(API_KEY_HEADER) == null || sameServer(origin, hop.url)) return chain.proceed(hop)
        return chain.proceed(hop.newBuilder().removeHeader(API_KEY_HEADER).build())
    }

    private fun sameServer(origin: HttpUrl, hop: HttpUrl): Boolean = when {
        origin.host != hop.host -> false
        origin.scheme == hop.scheme -> origin.port == hop.port
        else -> origin.scheme == "http" && hop.scheme == "https"
    }
}

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
