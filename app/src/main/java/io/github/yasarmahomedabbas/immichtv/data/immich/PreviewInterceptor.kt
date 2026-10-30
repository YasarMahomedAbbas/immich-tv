package io.github.yasarmahomedabbas.immichtv.data.immich

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.intercept.Interceptor
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.disk.DiskCache
import coil3.request.ErrorResult
import coil3.request.ImageResult
import coil3.request.crossfade
import io.github.yasarmahomedabbas.immichtv.domain.albums.Preview
import io.github.yasarmahomedabbas.immichtv.domain.search.PersonFace
import io.github.yasarmahomedabbas.immichtv.domain.server.SessionStore
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath

/**
 * Turns a domain [Preview] or [PersonFace] into the Immich thumbnail request: its URL, the key as
 * the `x-api-key` header and a cache key that holds neither. Screens hand Coil one of those and never
 * build a URL, so every Immich detail of loading an image stays in `data/`. The request goes
 * through the shared [immichHttpClient], whose network interceptor still strips the key from a
 * redirect to another host.
 */
internal class PreviewInterceptor(private val sessions: SessionStore) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val data = request.data
        if (data !is Preview && data !is PersonFace) return chain.proceed()
        val session = sessions.load()
            ?: return ErrorResult(null, request, IllegalStateException("Not signed in"))
        val (url, key) = when (data) {
            is Preview -> previewUrl(session.server, data.photo) to previewCacheKey(data.photo)
            is PersonFace -> personFaceUrl(session.server, data.person) to personFaceCacheKey(data.person)
            else -> return chain.proceed()
        }
        val rewritten = request.newBuilder()
            .data(url)
            .httpHeaders(NetworkHeaders.Builder().set(API_KEY_HEADER, session.apiKey.value).build())
            .memoryCacheKey(key)
            .diskCacheKey(key)
            .build()
        return chain.withRequest(rewritten).proceed()
    }
}

/** The app's image loader: previews through [PreviewInterceptor] on the one Immich client. */
internal fun immichImageLoader(context: PlatformContext, client: OkHttpClient, sessions: SessionStore): ImageLoader =
    ImageLoader.Builder(context)
        .components {
            add(PreviewInterceptor(sessions))
            add(OkHttpNetworkFetcherFactory(callFactory = { client }))
        }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("previews").toOkioPath())
                .maxSizeBytes(256L * 1024 * 1024)
                .build()
        }
        .crossfade(true)
        .build()
