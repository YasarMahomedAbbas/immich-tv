package io.github.yasarmahomedabbas.immichtv.data.immich

import io.github.yasarmahomedabbas.immichtv.domain.Problem
import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import io.github.yasarmahomedabbas.immichtv.domain.albums.Album
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumContents
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumId
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.domain.server.ApiKey
import io.github.yasarmahomedabbas.immichtv.domain.server.FakeSessionStore
import io.github.yasarmahomedabbas.immichtv.domain.server.ServerAddress
import io.github.yasarmahomedabbas.immichtv.domain.server.Session
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.concurrent.TimeUnit

class ImmichAlbumRepositoryTest {
    private val server = MockWebServer()
    private val sessions = FakeSessionStore()
    private val repository = ImmichAlbumRepository(immichHttpClient(), sessions)

    /** Path (with query) → response, served as often as asked (MockResponse hands out its body afresh); anything else is a 404. */
    private val routes = mutableMapOf<String, MockResponse>()

    @Before
    fun setUp() {
        routes["/api/users/me"] = MockResponse().setBody("""{"id":"$ME","name":"Ada","email":"ada@example.com"}""")
        routes["/api/albums"] = MockResponse().setBody(ALBUMS_V3_3_5)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) =
                routes[request.path] ?: MockResponse().setResponseCode(404)
        }
        server.start()
        sessions.session = Session(ServerAddress.parse(server.url("/").toString())!!, ApiKey(KEY))
    }

    @After
    fun tearDown() = server.shutdown()

    private fun requests() = List(server.requestCount) { server.takeRequest() }

    private val expectedAlbums = listOf(
        Album(
            id = AlbumId(SUMMER),
            name = "Summer 2025",
            itemCount = 226,
            cover = PhotoId(COVER),
            shared = true,
            ownedByMe = true,
            updatedAt = Instant.parse("2025-09-02T10:00:00Z"),
            startDate = Instant.parse("2025-06-03T09:12:00Z"),
            endDate = Instant.parse("2025-08-28T18:40:00Z"),
            newestFirst = true,
        ),
        Album(
            id = AlbumId(EMPTY),
            name = "Wedding",
            itemCount = 0,
            cover = null,
            shared = true,
            ownedByMe = false,
            updatedAt = Instant.parse("2025-05-01T12:00:00Z"),
            startDate = null,
            endDate = null,
            newestFirst = false,
        ),
    )

    @Test
    fun `a v3_3_5 albums body maps to albums, the owner read from album users`() = runTest {
        assertEquals(ServerResult.Ok(expectedAlbums), repository.albums())
    }

    @Test
    fun `a v2_0_0 albums body maps to the same albums, the owner read from owner id`() = runTest {
        routes["/api/albums"] = MockResponse().setBody(ALBUMS_V2_0_0)
        assertEquals(ServerResult.Ok(expectedAlbums), repository.albums())
    }

    @Test
    fun `users me is asked once for two album lists`() = runTest {
        repository.albums()
        repository.albums()
        assertEquals(1, requests().count { it.path == "/api/users/me" })
    }

    @Test
    fun `users me is asked again for a new session`() = runTest {
        repository.albums()
        sessions.session = sessions.session!!.copy(apiKey = ApiKey("other-key"))
        repository.albums()
        assertEquals(2, requests().count { it.path == "/api/users/me" })
    }

    @Test
    fun `one album by id`() = runTest {
        routes["/api/albums/$SUMMER"] = MockResponse().setBody(ALBUM_V2_0_0_WITH_ASSETS)
        assertEquals(ServerResult.Ok(expectedAlbums[0]), repository.album(AlbumId(SUMMER)))
        assertEquals(listOf("/api/users/me", "/api/albums/$SUMMER"), requests().map { it.path })
    }

    @Test
    fun `every request carries the key in the header and never in the url`() = runTest {
        routes["/api/albums/$SUMMER"] = MockResponse().setBody(ALBUM_V2_0_0_WITH_ASSETS)
        repository.albums()
        repository.album(AlbumId(SUMMER))
        repository.contents(expectedAlbums[0], 30)

        val all = requests()
        assertTrue(all.isNotEmpty())
        for (request in all) {
            assertEquals(KEY, request.getHeader("x-api-key"))
            assertTrue(KEY !in request.requestUrl.toString())
        }
    }

    @Test
    fun `contents sums both buckets, keeps their order and caps the items`() = runTest {
        routes["/api/timeline/buckets?albumId=$SUMMER&order=desc"] =
            MockResponse().setBody("""[{"timeBucket":"2025-08-01","count":2},{"timeBucket":"2025-07-01","count":3}]""")
        routes["/api/timeline/bucket?albumId=$SUMMER&timeBucket=2025-08-01&order=desc"] =
            MockResponse().setBody(bucket(listOf("p1" to true, "v1" to false))).setBodyDelay(100, TimeUnit.MILLISECONDS)
        routes["/api/timeline/bucket?albumId=$SUMMER&timeBucket=2025-07-01&order=desc"] =
            MockResponse().setBody(bucket(listOf("p2" to true, "p3" to true, "v2" to false)))

        val result = repository.contents(expectedAlbums[0], 3)

        val expected = AlbumContents(
            photoCount = 3,
            videoCount = 2,
            firstItems = listOf(Photo(PhotoId("p1"), false), Photo(PhotoId("v1"), true), Photo(PhotoId("p2"), false)),
        )
        assertEquals(ServerResult.Ok(expected), result)
    }

    @Test
    fun `contents of an oldest-first album asks in ascending order`() = runTest {
        val oldestFirst = expectedAlbums[0].copy(newestFirst = false)
        routes["/api/timeline/buckets?albumId=$SUMMER&order=asc"] =
            MockResponse().setBody("""[{"timeBucket":"2025-06-01","count":1}]""")
        routes["/api/timeline/bucket?albumId=$SUMMER&timeBucket=2025-06-01&order=asc"] =
            MockResponse().setBody(bucket(listOf("p1" to true)))

        assertEquals(
            ServerResult.Ok(AlbumContents(1, 0, listOf(Photo(PhotoId("p1"), false)))),
            repository.contents(oldestFirst, 30),
        )
    }

    @Test
    fun `an empty album has no buckets and zero counts`() = runTest {
        routes["/api/timeline/buckets?albumId=$EMPTY&order=asc"] = MockResponse().setBody("[]")
        assertEquals(ServerResult.Ok(AlbumContents(0, 0, emptyList())), repository.contents(expectedAlbums[1], 30))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `one failed bucket fails the contents`() = runTest {
        routes["/api/timeline/buckets?albumId=$SUMMER&order=desc"] =
            MockResponse().setBody("""[{"timeBucket":"2025-08-01","count":2},{"timeBucket":"2025-07-01","count":3}]""")
        routes["/api/timeline/bucket?albumId=$SUMMER&timeBucket=2025-08-01&order=desc"] =
            MockResponse().setBody(bucket(listOf("p1" to true)))
        routes["/api/timeline/bucket?albumId=$SUMMER&timeBucket=2025-07-01&order=desc"] =
            MockResponse().setResponseCode(403)

        assertEquals(ServerResult.Failed(Problem.KeyLacksPermission), repository.contents(expectedAlbums[0], 30))
    }

    @Test
    fun `failed buckets list fails the contents`() = runTest {
        routes["/api/timeline/buckets?albumId=$SUMMER&order=desc"] = MockResponse().setResponseCode(500)
        assertEquals(ServerResult.Failed(Problem.Unreachable), repository.contents(expectedAlbums[0], 30))
    }

    @Test
    fun `a 401 is a rejected key`() = runTest {
        routes["/api/albums"] = MockResponse().setResponseCode(401)
        assertEquals(ServerResult.Failed(Problem.KeyRejected), repository.albums())
    }

    @Test
    fun `a 403 is a key without permission`() = runTest {
        routes["/api/albums"] = MockResponse().setResponseCode(403)
        assertEquals(ServerResult.Failed(Problem.KeyLacksPermission), repository.albums())
    }

    @Test
    fun `a users me failure fails the albums`() = runTest {
        routes["/api/users/me"] = MockResponse().setResponseCode(401)
        assertEquals(ServerResult.Failed(Problem.KeyRejected), repository.albums())
        routes["/api/users/me"] = MockResponse().setResponseCode(403)
        assertEquals(ServerResult.Failed(Problem.KeyLacksPermission), repository.album(AlbumId(SUMMER)))
    }

    @Test
    fun `a 500 is unreachable`() = runTest {
        routes["/api/albums"] = MockResponse().setResponseCode(500)
        assertEquals(ServerResult.Failed(Problem.Unreachable), repository.albums())
    }

    @Test
    fun `a body that isn't albums is unreachable`() = runTest {
        routes["/api/albums"] = MockResponse().setBody("<html>proxy error</html>")
        assertEquals(ServerResult.Failed(Problem.Unreachable), repository.albums())
    }

    @Test
    fun `an album with a date that doesn't parse is unreachable`() = runTest {
        routes["/api/albums"] = MockResponse().setBody(ALBUMS_V3_3_5.replace("2025-05-01T12:00:00.000Z", "yesterday"))
        assertEquals(ServerResult.Failed(Problem.Unreachable), repository.albums())
    }

    @Test
    fun `a dropped connection is unreachable`() = runTest {
        routes["/api/albums"] = MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
        assertEquals(ServerResult.Failed(Problem.Unreachable), repository.albums())
    }

    @Test
    fun `a server that is gone is unreachable`() = runTest {
        server.shutdown()
        assertEquals(ServerResult.Failed(Problem.Unreachable), repository.albums())
    }

    @Test
    fun `no session is not signed in, and nothing is asked`() = runTest {
        sessions.session = null
        assertEquals(ServerResult.Failed(Problem.NotSignedIn), repository.albums())
        assertEquals(ServerResult.Failed(Problem.NotSignedIn), repository.album(AlbumId(SUMMER)))
        assertEquals(ServerResult.Failed(Problem.NotSignedIn), repository.contents(expectedAlbums[0], 30))
        assertEquals(0, server.requestCount)
    }

    private companion object {
        const val KEY = "k3y-Secret-0123456789"
        const val ME = "3fa85f64-5717-4562-b3fc-2c963f66afa6"
        const val OTHER = "7c9e6679-7425-40de-944b-e07fc1f90ae7"
        const val SUMMER = "1b4e28ba-2fa1-41d2-883f-0016d3cca427"
        const val EMPTY = "6ec0bd7f-11c0-43da-975e-2a8ad9ebae0b"
        const val COVER = "9b2f1c4e-3d5a-4e6f-8a7b-1c2d3e4f5a6b"

        /** A columnar `TimeBucketAssetResponseDto` with the extra columns a real server sends. */
        fun bucket(items: List<Pair<String, Boolean>>): String {
            val ids = items.joinToString { "\"${it.first}\"" }
            val images = items.joinToString { it.second.toString() }
            val nulls = items.joinToString { "null" }
            val falses = items.joinToString { "false" }
            return """{"id":[$ids],"isImage":[$images],"isFavorite":[$falses],"isTrashed":[$falses],""" +
                """"city":[$nulls],"country":[$nulls],"duration":[$nulls],"ownerId":[${items.joinToString { "\"$ME\"" }}]}"""
        }

        fun user(id: String, name: String) =
            """{"id":"$id","name":"$name","email":"$name@example.com","avatarColor":"amber","profileImagePath":"","profileChangedAt":"2024-01-01T00:00:00.000Z"}"""

        /**
         * `AlbumResponseDto[]` as v3.3.5 sends it: no `ownerId`, `owner` or `assets`; the owner is
         * first in `albumUsers`. The second album is someone else's, shared with me.
         */
        val ALBUMS_V3_3_5 = """
            [
              {
                "albumName": "Summer 2025",
                "albumThumbnailAssetId": "$COVER",
                "albumUsers": [
                  {"role": "owner", "user": ${user(ME, "ada")}},
                  {"role": "viewer", "user": ${user(OTHER, "bob")}}
                ],
                "assetCount": 226,
                "contributorCounts": [],
                "createdAt": "2025-06-01T08:00:00.000Z",
                "description": "",
                "endDate": "2025-08-28T18:40:00.000Z",
                "hasSharedLink": false,
                "id": "$SUMMER",
                "isActivityEnabled": true,
                "lastModifiedAssetTimestamp": "2025-09-02T10:00:00.000Z",
                "order": "desc",
                "shared": true,
                "startDate": "2025-06-03T09:12:00.000Z",
                "updatedAt": "2025-08-30T10:00:00.000Z"
              },
              {
                "albumName": "Wedding",
                "albumThumbnailAssetId": null,
                "albumUsers": [
                  {"role": "owner", "user": ${user(OTHER, "bob")}},
                  {"role": "editor", "user": ${user(ME, "ada")}}
                ],
                "assetCount": 0,
                "createdAt": "2025-05-01T12:00:00.000Z",
                "description": "",
                "hasSharedLink": false,
                "id": "$EMPTY",
                "isActivityEnabled": true,
                "order": "asc",
                "shared": true,
                "updatedAt": "2025-05-01T12:00:00.000Z"
              }
            ]
        """.trimIndent()

        /** The same albums as v2.0.0 sends them: `ownerId`, `owner`, `assets`; `albumUsers` lists only who it's shared with. */
        val ALBUMS_V2_0_0 = """
            [
              {
                "albumName": "Summer 2025",
                "albumThumbnailAssetId": "$COVER",
                "albumUsers": [{"role": "viewer", "user": ${user(OTHER, "bob")}}],
                "assetCount": 226,
                "assets": [],
                "createdAt": "2025-06-01T08:00:00.000Z",
                "description": "",
                "endDate": "2025-08-28T18:40:00.000Z",
                "hasSharedLink": false,
                "id": "$SUMMER",
                "isActivityEnabled": true,
                "lastModifiedAssetTimestamp": "2025-09-02T10:00:00.000Z",
                "order": "desc",
                "owner": ${user(ME, "ada")},
                "ownerId": "$ME",
                "shared": true,
                "startDate": "2025-06-03T09:12:00.000Z",
                "updatedAt": "2025-08-30T10:00:00.000Z"
              },
              {
                "albumName": "Wedding",
                "albumThumbnailAssetId": null,
                "albumUsers": [{"role": "editor", "user": ${user(ME, "ada")}}],
                "assetCount": 0,
                "assets": [],
                "createdAt": "2025-05-01T12:00:00.000Z",
                "description": "",
                "hasSharedLink": false,
                "id": "$EMPTY",
                "isActivityEnabled": true,
                "order": "asc",
                "owner": ${user(OTHER, "bob")},
                "ownerId": "$OTHER",
                "shared": true,
                "updatedAt": "2025-05-01T12:00:00.000Z"
              }
            ]
        """.trimIndent()

        /** One album as v2.0.0's `GET /albums/{id}` sends it, with its assets embedded (ignored). */
        val ALBUM_V2_0_0_WITH_ASSETS = """
            {
              "albumName": "Summer 2025",
              "albumThumbnailAssetId": "$COVER",
              "albumUsers": [],
              "assetCount": 226,
              "assets": [{"id": "$COVER", "type": "IMAGE", "originalFileName": "a.jpg"}],
              "createdAt": "2025-06-01T08:00:00.000Z",
              "description": "",
              "endDate": "2025-08-28T18:40:00.000Z",
              "hasSharedLink": false,
              "id": "$SUMMER",
              "isActivityEnabled": true,
              "lastModifiedAssetTimestamp": "2025-09-02T10:00:00.000Z",
              "order": "desc",
              "owner": ${user(ME, "ada")},
              "ownerId": "$ME",
              "shared": true,
              "startDate": "2025-06-03T09:12:00.000Z",
              "updatedAt": "2025-08-30T10:00:00.000Z"
            }
        """.trimIndent()
    }
}
