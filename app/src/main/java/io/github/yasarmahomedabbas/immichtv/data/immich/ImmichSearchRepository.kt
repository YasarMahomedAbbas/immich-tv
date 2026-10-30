package io.github.yasarmahomedabbas.immichtv.data.immich

import io.github.yasarmahomedabbas.immichtv.domain.Problem
import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.domain.map
import io.github.yasarmahomedabbas.immichtv.domain.search.Person
import io.github.yasarmahomedabbas.immichtv.domain.search.PersonId
import io.github.yasarmahomedabbas.immichtv.domain.search.SearchRepository
import io.github.yasarmahomedabbas.immichtv.domain.server.Session
import io.github.yasarmahomedabbas.immichtv.domain.server.SessionStore
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.Slide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeUnit

/**
 * Search from Immich v1.133+: `POST /api/search/smart`, `POST /api/search/metadata` by person and
 * `GET /api/people`. `total` and `nextPage` are not read: on smart search `total` is only the page's
 * count and `nextPage` never ends, so what one request returns is what the app shows.
 */
internal class ImmichSearchRepository(
    private val client: OkHttpClient,
    private val sessions: SessionStore,
) : SearchRepository {

    /** Derived, so it keeps the key's redirect rule; the first query after the model unloads is slow. */
    private val searchClient = client.newBuilder().readTimeout(SEARCH_TIMEOUT_SECONDS, TimeUnit.SECONDS).build()

    override suspend fun search(description: String, limit: Int): ServerResult<List<Slide>> = signedIn { session ->
        val body = immichJson.encodeToString(SmartSearchBody(description, limit, withExif = true))
        // Immich answers 400 to an empty query, which never leaves the domain, and when smart search is off.
        post<SearchResponseDto>(session, "search/smart", body, on400 = Problem.SearchUnavailable).map { it.toSlides() }
    }

    override suspend fun photosOf(person: PersonId, limit: Int): ServerResult<List<Slide>> = signedIn { session ->
        val body = immichJson.encodeToString(PersonSearchBody(listOf(person.value), limit, withExif = true, order = "desc"))
        post<SearchResponseDto>(session, "search/metadata", body, on400 = Problem.Unreachable).map { it.toSlides() }
    }

    override suspend fun people(): ServerResult<List<Person>> = signedIn { session ->
        withContext(Dispatchers.IO) {
            call {
                val url = apiUrl(session.server, "people").newBuilder().addQueryParameter("size", PEOPLE_PAGE).build()
                val request = Request.Builder().url(url).header(API_KEY_HEADER, session.apiKey.value).get().build()
                client.newCall(request).execute().use { response ->
                    read<PeopleDto>(response.code, response.body?.string(), on400 = Problem.Unreachable)
                }
            }
        }.map { dto -> dto.people.filterNot { it.isHidden }.map { Person(PersonId(it.id), it.name) } }
    }

    private suspend fun <T> signedIn(block: suspend (Session) -> ServerResult<T>): ServerResult<T> {
        val session = sessions.load() ?: return ServerResult.Failed(Problem.NotSignedIn)
        return block(session)
    }

    private suspend inline fun <reified T> post(
        session: Session,
        path: String,
        body: String,
        on400: Problem,
    ): ServerResult<T> = withContext(Dispatchers.IO) {
        call {
            val request = Request.Builder()
                .url(apiUrl(session.server, path))
                .header(API_KEY_HEADER, session.apiKey.value)
                .post(body.toRequestBody(JSON))
                .build()
            searchClient.newCall(request).execute().use { response ->
                read<T>(response.code, response.body?.string(), on400)
            }
        }
    }

    private inline fun <T> call(block: () -> ServerResult<T>): ServerResult<T> = try {
        block()
    } catch (_: IOException) {
        ServerResult.Failed(Problem.Unreachable)
    } catch (_: IllegalArgumentException) {
        // The address passed the domain's checks but OkHttp can't build a URL from it.
        ServerResult.Failed(Problem.Unreachable)
    }

    private inline fun <reified T> read(code: Int, body: String?, on400: Problem): ServerResult<T> = when (code) {
        200 -> decode<T>(body)?.let { ServerResult.Ok(it) } ?: ServerResult.Failed(Problem.Unreachable)
        400 -> ServerResult.Failed(on400)
        401 -> ServerResult.Failed(Problem.KeyRejected)
        403 -> ServerResult.Failed(Problem.KeyLacksPermission)
        else -> ServerResult.Failed(Problem.Unreachable)
    }

    private companion object {
        const val SEARCH_TIMEOUT_SECONDS = 60L
        const val PEOPLE_PAGE = "1000"
        val JSON = "application/json".toMediaType()
    }
}

@Serializable
private data class SmartSearchBody(val query: String, val size: Int, val withExif: Boolean)

/** Top-level `personIds` and `order` are deprecated since v3.2 but accepted; `filter.personIds` doesn't exist before it. */
@Serializable
private data class PersonSearchBody(val personIds: List<String>, val size: Int, val withExif: Boolean, val order: String)

@Serializable
private data class SearchResponseDto(val assets: SearchAssetsDto) {
    fun toSlides(): List<Slide> = assets.items.map { it.toSlide() }
}

@Serializable
private data class SearchAssetsDto(val items: List<SearchAssetDto>)

/** The part of `AssetResponseDto` the app reads. `width` and `height` are only there from v3. */
@Serializable
private data class SearchAssetDto(
    val id: String,
    val type: String,
    val width: Double? = null,
    val height: Double? = null,
    val localDateTime: String? = null,
    val exifInfo: SearchExifDto? = null,
) {
    fun toSlide() = Slide(
        photo = Photo(PhotoId(id), isVideo = type == "VIDEO"),
        portrait = portrait(),
        takenOn = takenOn(),
        place = place(exifInfo?.city, exifInfo?.country),
    )

    /** v3's `width` and `height` are already rotated; the exif sizes are as stored, so orientations 5–8 swap them. */
    private fun portrait(): Boolean {
        if (width != null && height != null) return width < height
        val exifWidth = exifInfo?.exifImageWidth ?: return false
        val exifHeight = exifInfo.exifImageHeight ?: return false
        val turned = exifInfo.orientation?.toIntOrNull() in TURNED_ORIENTATIONS
        return if (turned) exifHeight < exifWidth else exifWidth < exifHeight
    }

    private fun takenOn(): LocalDate? = try {
        localDateTime?.let { LocalDate.parse(it.take(ISO_DATE_LENGTH)) }
    } catch (_: DateTimeParseException) {
        null
    }
}

private val TURNED_ORIENTATIONS = 5..8
private const val ISO_DATE_LENGTH = 10

@Serializable
private data class SearchExifDto(
    val city: String? = null,
    val country: String? = null,
    val exifImageWidth: Double? = null,
    val exifImageHeight: Double? = null,
    val orientation: String? = null,
)

@Serializable
private data class PeopleDto(val people: List<PersonDto>)

@Serializable
private data class PersonDto(val id: String, val name: String = "", val isHidden: Boolean = false)
