package io.github.yasarmahomedabbas.immichtv.data.immich

import io.github.yasarmahomedabbas.immichtv.domain.Problem
import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.domain.photos.TimelineMonth
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ImmichLibraryRepositoryTest {
    private val server = MockWebServer()
    private val sessions = FakeSessionStore()
    private val repository = ImmichLibraryRepository(immichHttpClient(), sessions)

    private var buckets = MockResponse().setBody("""[{"timeBucket":"2025-08-01","count":2},{"timeBucket":"2025-07-01","count":1}]""")

    /** The `timeBucket` parameter → its columns; anything else is a 404. */
    private val months = mutableMapOf(
        "2025-08-01" to MockResponse().setBody(AUGUST),
        "2025-07-01" to MockResponse().setBody(JULY),
    )

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                return when (url.encodedPath) {
                    "/api/timeline/buckets" -> buckets
                    "/api/timeline/bucket" -> months[url.queryParameter("timeBucket")]
                    else -> null
                } ?: MockResponse().setResponseCode(404)
            }
        }
        server.start()
        sessions.session = Session(ServerAddress.parse(server.url("/").toString())!!, ApiKey(KEY))
    }

    @After
    fun tearDown() = server.shutdown()

    private fun requests() = List(server.requestCount) { server.takeRequest() }

    private fun assertLibraryRequest(request: RecordedRequest) {
        val url = request.requestUrl!!
        assertEquals("timeline", url.queryParameter("visibility"))
        assertEquals("true", url.queryParameter("withStacked"))
        assertEquals("desc", url.queryParameter("order"))
        assertNull(url.queryParameter("albumId"))
        assertEquals(KEY, request.getHeader("x-api-key"))
    }

    @Test
    fun `the months are read for the user's own timeline`() = runTest {
        val months = repository.timeline().months()

        assertEquals(
            ServerResult.Ok(listOf(TimelineMonth(YearMonth.of(2025, 8), 2), TimelineMonth(YearMonth.of(2025, 7), 1))),
            months,
        )
        assertLibraryRequest(requests().single())
    }

    @Test
    fun `a month's photos are asked for with the bucket as the server wrote it, short`() = runTest {
        val timeline = repository.timeline()
        timeline.months()

        val photos = timeline.photos(YearMonth.of(2025, 8))

        assertEquals(ServerResult.Ok(listOf(Photo(PhotoId("a"), false), Photo(PhotoId("v"), true))), photos)
        val request = requests().last()
        assertLibraryRequest(request)
        assertEquals("2025-08-01", request.requestUrl!!.queryParameter("timeBucket"))
    }

    @Test
    fun `a month's photos are asked for with the bucket as the server wrote it, a full timestamp`() = runTest {
        val long = "2025-08-01T00:00:00.000Z"
        buckets = MockResponse().setBody("""[{"timeBucket":"$long","count":2}]""")
        months[long] = MockResponse().setBody(AUGUST)
        val timeline = repository.timeline()
        timeline.months()

        val photos = timeline.photos(YearMonth.of(2025, 8))

        assertEquals(2, (photos as ServerResult.Ok).value.size)
        assertEquals(long, requests().last().requestUrl!!.queryParameter("timeBucket"))
    }

    @Test
    fun `an empty library has no months`() = runTest {
        buckets = MockResponse().setBody("[]")
        assertEquals(ServerResult.Ok(emptyList<TimelineMonth>()), repository.timeline().months())
    }

    @Test
    fun `a bucket with a date that can't be read is unreachable`() = runTest {
        buckets = MockResponse().setBody("""[{"timeBucket":"soon","count":1}]""")
        assertEquals(ServerResult.Failed(Problem.Unreachable), repository.timeline().months())
    }

    @Test
    fun `a refused or limited key says so`() = runTest {
        buckets = MockResponse().setResponseCode(401)
        assertEquals(ServerResult.Failed(Problem.KeyRejected), repository.timeline().months())
        buckets = MockResponse().setResponseCode(403)
        assertEquals(ServerResult.Failed(Problem.KeyLacksPermission), repository.timeline().months())
    }

    @Test
    fun `no session is not signed in, with no request made`() = runTest {
        sessions.session = null
        assertEquals(ServerResult.Failed(Problem.NotSignedIn), repository.timeline().months())
        assertEquals(ServerResult.Failed(Problem.NotSignedIn), repository.slides())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `the slides are every month's items, newest month first, from one request a month`() = runTest {
        val slides = repository.slides()

        assertEquals(
            ServerResult.Ok(
                listOf(
                    Slide(Photo(PhotoId("a"), false), portrait = true, LocalDate.of(2025, 8, 23), Place("Lisbon", "Portugal")),
                    Slide(Photo(PhotoId("v"), true), portrait = false, null, null),
                    Slide(Photo(PhotoId("b"), false), portrait = false, null, null),
                ),
            ),
            slides,
        )
        val requests = requests()
        assertEquals(3, requests.size)
        requests.forEach(::assertLibraryRequest)
    }

    @Test
    fun `one failed month fails the slides`() = runTest {
        months["2025-07-01"] = MockResponse().setResponseCode(500)
        assertEquals(ServerResult.Failed(Problem.Unreachable), repository.slides())
    }

    @Test
    fun `an empty library has no slides`() = runTest {
        buckets = MockResponse().setBody("[]")
        assertEquals(ServerResult.Ok(emptyList<Slide>()), repository.slides())
    }

    private companion object {
        const val KEY = "secret-key"
        const val AUGUST = """{"id":["a","v"],"isImage":[true,false],"ratio":[0.75,null],
            "city":["Lisbon",null],"country":["Portugal",null],
            "fileCreatedAt":["2025-08-23T12:00:00.000Z",null],"localOffsetHours":[1,null]}"""
        const val JULY = """{"id":["b"],"isImage":[true]}"""
    }
}
