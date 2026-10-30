package io.github.yasarmahomedabbas.immichtv.data.immich

import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.domain.albums.Video
import io.github.yasarmahomedabbas.immichtv.domain.server.ApiKey
import io.github.yasarmahomedabbas.immichtv.domain.server.ServerAddress
import io.github.yasarmahomedabbas.immichtv.domain.server.Session
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** The address and header [immichVideoPlayer] opens a video with, sent the way it sends them. */
class ImmichVideoTest {
    private val video = Video(PhotoId("9b2f1c4e-3d5a-4e6f-8a7b-1c2d3e4f5a6b"))

    private fun session(url: String) = Session(ServerAddress.parse(url)!!, ApiKey(KEY))

    @Test
    fun `the stream is the playback path, with the key only as a header`() {
        val stream = videoStream(session("https://photos.example.com"), video)
        assertEquals("https://photos.example.com/api/assets/${video.photo.value}/video/playback", stream.url)
        assertEquals(mapOf("x-api-key" to KEY), stream.headers)
        assertFalse(KEY in stream.url)
    }

    @Test
    fun `an address with a trailing api gives the same url`() {
        assertEquals(
            videoStream(session("http://nas:2283"), video).url,
            videoStream(session("http://nas:2283/api"), video).url,
        )
    }

    @Test
    fun `the key can't reach a log through toString`() {
        assertFalse(KEY in videoStream(session("http://nas:2283"), video).toString())
    }

    @Test
    fun `a stream request carries the key to the server in a header`() {
        val server = MockWebServer().apply {
            enqueue(MockResponse().setHeader("Content-Type", "video/mp4").setBody("mp4"))
            start()
        }
        try {
            fetch(videoStream(session(server.url("/").toString()), video)).close()

            val request = server.takeRequest()
            assertEquals("/api/assets/${video.photo.value}/video/playback", request.path)
            assertEquals(KEY, request.getHeader("x-api-key"))
            assertFalse(KEY in request.path!!)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `a range request answered 206 reads through`() {
        val server = MockWebServer().apply {
            enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Type", "video/mp4")
                    .setHeader("Content-Range", "bytes 2-3/4")
                    .setBody("p4"),
            )
            start()
        }
        try {
            fetch(videoStream(session(server.url("/").toString()), video), range = "bytes=2-3").use { response ->
                assertEquals(206, response.code)
                assertEquals("p4", response.body!!.string())
            }
            assertEquals("bytes=2-3", server.takeRequest().getHeader("Range"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `a stream redirected to another server loses the key`() {
        val elsewhere = MockWebServer().apply {
            enqueue(MockResponse().setBody("mp4"))
            start()
        }
        val server = MockWebServer().apply {
            enqueue(MockResponse().setResponseCode(302).setHeader("Location", elsewhere.url("/cdn/video.mp4")))
            start()
        }
        try {
            fetch(videoStream(session(server.url("/").toString()), video)).close()

            assertEquals(KEY, server.takeRequest().getHeader("x-api-key"))
            assertNull(elsewhere.takeRequest().getHeader("x-api-key"))
        } finally {
            server.shutdown()
            elsewhere.shutdown()
        }
    }

    private fun fetch(stream: VideoStream, range: String? = null): okhttp3.Response {
        val request = Request.Builder().url(stream.url).apply {
            stream.headers.forEach { (name, value) -> header(name, value) }
            if (range != null) header("Range", range)
        }.build()
        return immichHttpClient().newCall(request).execute()
    }

    private companion object {
        const val KEY = "k3y-Secret-0123456789"
    }
}
