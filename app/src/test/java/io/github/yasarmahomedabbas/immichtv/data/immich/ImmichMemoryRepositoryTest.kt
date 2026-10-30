package io.github.yasarmahomedabbas.immichtv.data.immich

import io.github.yasarmahomedabbas.immichtv.domain.Problem
import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.domain.home.Memory
import io.github.yasarmahomedabbas.immichtv.domain.server.ApiKey
import io.github.yasarmahomedabbas.immichtv.domain.server.FakeSessionStore
import io.github.yasarmahomedabbas.immichtv.domain.server.ServerAddress
import io.github.yasarmahomedabbas.immichtv.domain.server.Session
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.Place
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.Slide
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class ImmichMemoryRepositoryTest {
    private val server = MockWebServer()
    private val sessions = FakeSessionStore()
    private val repository = ImmichMemoryRepository(immichHttpClient(), sessions)
    private val day = LocalDate.of(2026, 10, 30)

    private var answer = MockResponse().setBody("[]")

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.requestUrl!!.encodedPath == "/api/memories") answer else MockResponse().setResponseCode(404)
        }
        server.start()
        sessions.session = Session(ServerAddress.parse(server.url("/").toString())!!, ApiKey(KEY))
    }

    @After
    fun tearDown() = server.shutdown()

    private fun memories(json: String) {
        answer = MockResponse().setBody(json)
    }

    @Test
    fun `it asks for today's on-this-day memories, date-only, with the key`() = runTest {
        repository.memoriesOn(day)

        val request = server.takeRequest()
        val url = request.requestUrl!!
        assertEquals("2026-10-30", url.queryParameter("for"))
        assertEquals("on_this_day", url.queryParameter("type"))
        assertEquals(KEY, request.getHeader("x-api-key"))
        assertEquals(null, url.queryParameter("apiKey"))
    }

    @Test
    fun `a v1_133 memory reads, with no shape on its assets`() = runTest {
        memories(
            """[{"id":"m","createdAt":"2025-10-30T00:00:00.000Z","memoryAt":"2024-10-30T00:00:00.000Z","type":"on_this_day",
            "data":{"year":2024},"isSaved":false,"assets":[
            {"id":"a","type":"IMAGE","visibility":"timeline","localDateTime":"2024-10-30T18:00:00.000Z",
             "exifInfo":{"city":"Lisbon","country":"Portugal"}}]}]""",
        )

        assertEquals(
            ServerResult.Ok(
                listOf(
                    Memory(
                        LocalDate.of(2024, 10, 30),
                        listOf(Slide(Photo(PhotoId("a"), false), false, LocalDate.of(2024, 10, 30), Place("Lisbon", "Portugal"))),
                    ),
                ),
            ),
            repository.memoriesOn(day),
        )
    }

    @Test
    fun `a v3 memory reads, with its shape when it is known`() = runTest {
        memories(
            """[{"id":"m","memoryAt":"2023-10-30T00:00:00.000Z","type":"on_this_day","data":{"year":2023},"assets":[
            {"id":"tall","type":"IMAGE","visibility":"timeline","width":3,"height":4,"localDateTime":"2023-10-30T08:00:00.000Z"},
            {"id":"wide","type":"VIDEO","visibility":"timeline","width":4,"height":3,"localDateTime":"2023-10-30T09:00:00.000Z"},
            {"id":"unknown","type":"IMAGE","visibility":"timeline","width":null,"height":null,"localDateTime":"2023-10-30T10:00:00.000Z"}]}]""",
        )

        val slides = (repository.memoriesOn(day) as ServerResult.Ok).value.single().slides

        assertEquals(listOf(true, false, false), slides.map { it.portrait })
        assertEquals(listOf(false, true, false), slides.map { it.photo.isVideo })
    }

    @Test
    fun `assets off the timeline, and of any other type, are dropped`() = runTest {
        memories(
            """[{"memoryAt":"2024-10-30T00:00:00.000Z","assets":[
            {"id":"kept","type":"IMAGE","visibility":"timeline"},
            {"id":"no-visibility","type":"IMAGE"},
            {"id":"archived","type":"IMAGE","visibility":"archive"},
            {"id":"hidden","type":"VIDEO","visibility":"hidden"},
            {"id":"sound","type":"AUDIO","visibility":"timeline"}]}]""",
        )

        val slides = (repository.memoriesOn(day) as ServerResult.Ok).value.single().slides

        assertEquals(listOf("kept", "no-visibility"), slides.map { it.photo.id.value })
    }

    @Test
    fun `an asset's date that can't be read is unknown`() = runTest {
        memories("""[{"memoryAt":"2024-10-30T00:00:00.000Z","assets":[{"id":"a","type":"IMAGE","localDateTime":"soon"}]}]""")

        val slide = (repository.memoriesOn(day) as ServerResult.Ok).value.single().slides.single()

        assertEquals(null, slide.takenOn)
    }

    @Test
    fun `a memory's date is the day it says, whatever the device's zone`() = runTest {
        memories("""[{"memoryAt":"2024-10-30T00:00:00.000Z","assets":[]}]""")
        assertEquals(LocalDate.of(2024, 10, 30), (repository.memoriesOn(day) as ServerResult.Ok).value.single().takenOn)
    }

    @Test
    fun `a memory's date that can't be read is unreachable`() = runTest {
        memories("""[{"memoryAt":"soon","assets":[]}]""")
        assertEquals(ServerResult.Failed(Problem.Unreachable), repository.memoriesOn(day))
    }

    @Test
    fun `no memories is an empty list`() = runTest {
        assertEquals(ServerResult.Ok(emptyList<Memory>()), repository.memoriesOn(day))
    }

    @Test
    fun `each refusal says why`() = runTest {
        answer = MockResponse().setResponseCode(401)
        assertEquals(ServerResult.Failed(Problem.KeyRejected), repository.memoriesOn(day))
        answer = MockResponse().setResponseCode(403)
        assertEquals(ServerResult.Failed(Problem.KeyLacksPermission), repository.memoriesOn(day))
        answer = MockResponse().setResponseCode(500)
        assertEquals(ServerResult.Failed(Problem.Unreachable), repository.memoriesOn(day))
        answer = MockResponse().setResponseCode(400)
        assertEquals(ServerResult.Failed(Problem.Unreachable), repository.memoriesOn(day))
    }

    @Test
    fun `an answer that can't be read is unreachable`() = runTest {
        memories("not json")
        assertEquals(ServerResult.Failed(Problem.Unreachable), repository.memoriesOn(day))
    }

    @Test
    fun `no session is not signed in, with no request made`() = runTest {
        sessions.session = null
        assertEquals(ServerResult.Failed(Problem.NotSignedIn), repository.memoriesOn(day))
        assertEquals(0, server.requestCount)
    }

    private companion object {
        const val KEY = "secret-key"
    }
}
