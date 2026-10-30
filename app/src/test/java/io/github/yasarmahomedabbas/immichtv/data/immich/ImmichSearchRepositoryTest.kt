package io.github.yasarmahomedabbas.immichtv.data.immich

import io.github.yasarmahomedabbas.immichtv.domain.Problem
import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.domain.search.Person
import io.github.yasarmahomedabbas.immichtv.domain.search.PersonId
import io.github.yasarmahomedabbas.immichtv.domain.server.ApiKey
import io.github.yasarmahomedabbas.immichtv.domain.server.FakeSessionStore
import io.github.yasarmahomedabbas.immichtv.domain.server.ServerAddress
import io.github.yasarmahomedabbas.immichtv.domain.server.Session
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.Place
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.Slide
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class ImmichSearchRepositoryTest {
    private val server = MockWebServer()
    private val sessions = FakeSessionStore()
    private val repository = ImmichSearchRepository(immichHttpClient(), sessions)

    @Before
    fun setUp() {
        server.start()
        sessions.session = Session(ServerAddress.parse(server.url("/").toString())!!, ApiKey(KEY))
    }

    @After
    fun tearDown() = server.shutdown()

    private fun answer(body: String, code: Int = 200) = server.enqueue(MockResponse().setResponseCode(code).setBody(body))

    private fun assets(vararg items: String) = """{"albums":{"total":0,"count":0,"items":[],"facets":[]},"assets":{"total":${items.size},"count":${items.size},"items":[${items.joinToString()}],"facets":[],"nextPage":"2"}}"""

    private val expectedPortrait = Slide(
        Photo(PhotoId("p"), isVideo = false),
        portrait = true,
        takenOn = LocalDate.of(2025, 8, 23),
        place = Place("Lisbon", "Portugal"),
    )
    private val expectedLandscape = Slide(Photo(PhotoId("l"), isVideo = false), false, LocalDate.of(2024, 1, 2), null)

    @Test
    fun `a v3_3_5 response reads its shape from the top-level sizes, already rotated`() = runTest {
        answer(assets(PORTRAIT_V3, LANDSCAPE_V3))
        assertEquals(ServerResult.Ok(listOf(expectedPortrait, expectedLandscape)), repository.search("dog", 100))
    }

    @Test
    fun `a v1_133 response gives the same slides from the exif sizes`() = runTest {
        answer(assets(PORTRAIT_V1, LANDSCAPE_V1))
        assertEquals(ServerResult.Ok(listOf(expectedPortrait, expectedLandscape)), repository.search("dog", 100))
    }

    @Test
    fun `exif sizes without an orientation are taken as they are`() = runTest {
        answer(assets("""{"id":"a","type":"IMAGE","exifInfo":{"exifImageWidth":3024,"exifImageHeight":4032}}"""))
        assertEquals(true, (repository.search("dog", 100) as ServerResult.Ok).value.single().portrait)
    }

    @Test
    fun `a smart search sends the description, the limit and asks for exif, with the key only in the header`() = runTest {
        answer(assets())
        repository.search("dog on the beach", 100)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/search/smart", request.path)
        assertEquals(KEY, request.getHeader("x-api-key"))
        assertFalse(KEY in request.requestUrl.toString())
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals(setOf("query", "size", "withExif", "visibility"), body.keys)
        assertEquals("timeline", body.getValue("visibility").jsonPrimitive.content)
        assertEquals("dog on the beach", body.getValue("query").jsonPrimitive.content)
        assertEquals("100", body.getValue("size").jsonPrimitive.content)
        assertEquals("true", body.getValue("withExif").jsonPrimitive.content)
    }

    @Test
    fun `no results are an empty list`() = runTest {
        answer(assets())
        assertEquals(ServerResult.Ok(emptyList<Slide>()), repository.search("dog", 100))
    }

    @Test
    fun `a video is marked as one`() = runTest {
        answer(assets("""{"id":"v","type":"VIDEO"}"""))
        val slide = (repository.search("dog", 100) as ServerResult.Ok).value.single()
        assertEquals(true, slide.photo.isVideo)
    }

    @Test
    fun `missing exif and a date that can't be read leave those unknown, not a failure`() = runTest {
        answer(assets("""{"id":"a","type":"IMAGE","localDateTime":"not a date"}"""))
        assertEquals(
            ServerResult.Ok(listOf(Slide(Photo(PhotoId("a"), false), false, null, null))),
            repository.search("dog", 100),
        )
    }

    @Test
    fun `a 400 on smart search means it is turned off`() = runTest {
        answer("""{"message":"Smart search is not enabled"}""", 400)
        assertEquals(ServerResult.Failed(Problem.SearchUnavailable), repository.search("dog", 100))
    }

    @Test
    fun `other statuses and an unreadable body become problems`() = runTest {
        val cases = listOf(401 to Problem.KeyRejected, 403 to Problem.KeyLacksPermission, 500 to Problem.Unreachable)
        for ((code, problem) in cases) {
            answer("{}", code)
            assertEquals(ServerResult.Failed(problem), repository.search("dog", 100))
        }
        answer("not json")
        assertEquals(ServerResult.Failed(Problem.Unreachable), repository.search("dog", 100))
    }

    @Test
    fun `no session is a problem and no call`() = runTest {
        sessions.session = null
        assertEquals(ServerResult.Failed(Problem.NotSignedIn), repository.search("dog", 100))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a person's photos ask by person, newest first`() = runTest {
        answer(assets(PORTRAIT_V3))
        assertEquals(ServerResult.Ok(listOf(expectedPortrait)), repository.photosOf(PersonId("p1"), 1000))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/search/metadata", request.path)
        assertEquals(KEY, request.getHeader("x-api-key"))
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("""["p1"]""", body.getValue("personIds").toString())
        assertEquals("1000", body.getValue("size").jsonPrimitive.content)
        assertEquals("desc", body.getValue("order").jsonPrimitive.content)
        assertEquals("timeline", body.getValue("visibility").jsonPrimitive.content)
        assertEquals("true", body.getValue("withExif").jsonPrimitive.content)
    }

    @Test
    fun `a 400 on a person's photos is not smart search being off`() = runTest {
        answer("{}", 400)
        assertEquals(ServerResult.Failed(Problem.Unreachable), repository.photosOf(PersonId("p1"), 1000))
    }

    @Test
    fun `people are read without the hidden ones`() = runTest {
        answer(
            """{"total":3,"hidden":1,"hasNextPage":false,"people":[
                {"id":"1","name":"Ada","isHidden":false},
                {"id":"2","name":"Hid","isHidden":true},
                {"id":"3","name":"","isHidden":false}]}""",
        )
        assertEquals(
            ServerResult.Ok(listOf(Person(PersonId("1"), "Ada"), Person(PersonId("3"), ""))),
            repository.people(),
        )

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/people?size=1000", request.path)
        assertEquals(KEY, request.getHeader("x-api-key"))
    }

    @Test
    fun `a key that can't read people says so`() = runTest {
        answer("{}", 403)
        assertEquals(ServerResult.Failed(Problem.KeyLacksPermission), repository.people())
    }

    private companion object {
        const val KEY = "k3y-Secret-0123456789"

        const val EXIF_PORTRAIT = """"city":"Lisbon","country":"Portugal","exifImageWidth":4032,"exifImageHeight":3024,"orientation":"6""""

        const val PORTRAIT_V3 = """{"id":"p","type":"IMAGE","width":3024,"height":4032,"localDateTime":"2025-08-23T14:03:00.000Z","exifInfo":{$EXIF_PORTRAIT}}"""
        const val LANDSCAPE_V3 = """{"id":"l","type":"IMAGE","width":4032,"height":3024,"localDateTime":"2024-01-02T09:00:00.000Z","exifInfo":{"city":null,"country":null,"exifImageWidth":4032,"exifImageHeight":3024,"orientation":"1"}}"""

        const val PORTRAIT_V1 = """{"id":"p","type":"IMAGE","localDateTime":"2025-08-23T14:03:00.000Z","exifInfo":{$EXIF_PORTRAIT}}"""
        const val LANDSCAPE_V1 = """{"id":"l","type":"IMAGE","localDateTime":"2024-01-02T09:00:00.000Z","exifInfo":{"exifImageWidth":4032,"exifImageHeight":3024,"orientation":"1"}}"""
    }
}
