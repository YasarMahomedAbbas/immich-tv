package io.github.yasarmahomedabbas.immichtv.data.immich

import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.domain.server.ServerAddress
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The URL and cache key [PreviewInterceptor] gives Coil, and that a preview request made the
 * way Coil makes it (the shared client, the key as a header) carries the key only to the server.
 */
class PreviewInterceptorTest {
    private val photo = PhotoId("9b2f1c4e-3d5a-4e6f-8a7b-1c2d3e4f5a6b")

    @Test
    fun `the preview url is the thumbnail path at preview size`() {
        assertEquals(
            "https://photos.example.com/api/assets/${photo.value}/thumbnail?size=preview",
            previewUrl(ServerAddress.parse("https://photos.example.com")!!, photo),
        )
    }

    @Test
    fun `an address with a trailing api gives the same url`() {
        assertEquals(
            previewUrl(ServerAddress.parse("http://nas:2283")!!, photo),
            previewUrl(ServerAddress.parse("http://nas:2283/api")!!, photo),
        )
    }

    @Test
    fun `the cache key is the photo alone`() {
        assertEquals("preview:${photo.value}", previewCacheKey(photo))
        assertFalse("http" in previewCacheKey(photo))
    }

    @Test
    fun `a preview request carries the key to the server`() {
        val server = MockWebServer().apply {
            enqueue(MockResponse().setHeader("Content-Type", "image/jpeg").setBody("jpeg"))
            start()
        }
        try {
            fetch(previewUrl(ServerAddress.parse(server.url("/").toString())!!, photo))

            val request = server.takeRequest()
            assertEquals("/api/assets/${photo.value}/thumbnail?size=preview", request.path)
            assertEquals(KEY, request.getHeader("x-api-key"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `a preview redirected to another server loses the key`() {
        val elsewhere = MockWebServer().apply {
            enqueue(MockResponse().setBody("jpeg"))
            start()
        }
        val server = MockWebServer().apply {
            enqueue(MockResponse().setResponseCode(302).setHeader("Location", elsewhere.url("/cdn/preview.jpg")))
            start()
        }
        try {
            fetch(previewUrl(ServerAddress.parse(server.url("/").toString())!!, photo))

            assertEquals(KEY, server.takeRequest().getHeader("x-api-key"))
            assertNull(elsewhere.takeRequest().getHeader("x-api-key"))
        } finally {
            server.shutdown()
            elsewhere.shutdown()
        }
    }

    private fun fetch(url: String) {
        val request = Request.Builder().url(url).header(API_KEY_HEADER, KEY).build()
        immichHttpClient().newCall(request).execute().close()
    }

    private companion object {
        const val KEY = "k3y-Secret-0123456789"
    }
}
