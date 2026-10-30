package io.github.yasarmahomedabbas.immichtv.data.immich

import io.github.yasarmahomedabbas.immichtv.domain.Problem
import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import io.github.yasarmahomedabbas.immichtv.domain.albums.Album
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumContents
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumId
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumRepository
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.domain.map
import io.github.yasarmahomedabbas.immichtv.domain.photos.Timeline
import io.github.yasarmahomedabbas.immichtv.domain.photos.TimelineMonth
import io.github.yasarmahomedabbas.immichtv.domain.server.Session
import io.github.yasarmahomedabbas.immichtv.domain.server.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.YearMonth
import java.time.format.DateTimeParseException
import java.util.concurrent.ConcurrentHashMap

/**
 * Albums from Immich v1.133+: `GET /api/albums`, `/api/albums/{id}` and `/api/users/me` (who
 * "me" is, for owned vs shared), and the album's timeline (`/api/timeline/buckets` and
 * `/api/timeline/bucket` with `albumId`) for its photo and video counts, first items and its
 * photos month by month.
 */
internal class ImmichAlbumRepository(
    private val client: OkHttpClient,
    private val sessions: SessionStore,
) : AlbumRepository {

    /** The signed-in user's id, for the session it was read with. */
    @Volatile private var me: Pair<Session, String>? = null

    override suspend fun albums(): ServerResult<List<Album>> = signedIn { session ->
        val myId = when (val id = myId(session)) {
            is ServerResult.Ok -> id.value
            is ServerResult.Failed -> return@signedIn id
        }
        when (val albums = get<List<AlbumDto>>(session, "albums")) {
            is ServerResult.Ok -> mapAll(albums.value) { it.toAlbum(myId) }
            is ServerResult.Failed -> albums
        }
    }

    override suspend fun album(id: AlbumId): ServerResult<Album> = signedIn { session ->
        val myId = when (val me = myId(session)) {
            is ServerResult.Ok -> me.value
            is ServerResult.Failed -> return@signedIn me
        }
        when (val album = get<AlbumDto>(session, "albums/${id.value}")) {
            is ServerResult.Ok -> mapAll(listOf(album.value)) { it.toAlbum(myId) }.map { it.single() }
            is ServerResult.Failed -> album
        }
    }

    override suspend fun contents(album: Album, limit: Int): ServerResult<AlbumContents> = signedIn { session ->
        val buckets = when (val result = buckets(session, album)) {
            is ServerResult.Ok -> result.value
            is ServerResult.Failed -> return@signedIn result
        }
        val inFlight = Semaphore(MAX_IN_FLIGHT)
        val months = coroutineScope {
            buckets.map { bucket ->
                async { inFlight.withPermit { bucket(session, album, bucket.timeBucket) } }
            }.awaitAll()
        }
        val items = mutableListOf<Photo>()
        var photos = 0
        var videos = 0
        for (month in months) {
            val assets = when (month) {
                is ServerResult.Ok -> month.value
                is ServerResult.Failed -> return@signedIn month
            }
            for (photo in assets) {
                if (photo.isVideo) videos++ else photos++
                if (items.size < limit) items += photo
            }
        }
        ServerResult.Ok(AlbumContents(photos, videos, items))
    }

    override fun timeline(album: Album): Timeline = AlbumTimeline(album)

    /**
     * [album]'s months from `/timeline/buckets`, and each month's items from `/timeline/bucket`.
     * The bucket string Immich sent for a month is what goes back for it (v3 sends `YYYY-MM-DD`,
     * older servers a full timestamp), so they are remembered from [months].
     */
    private inner class AlbumTimeline(private val album: Album) : Timeline {
        private val bucketOf = ConcurrentHashMap<YearMonth, String>()

        override suspend fun months(): ServerResult<List<TimelineMonth>> = signedIn { session ->
            val buckets = when (val result = buckets(session, album)) {
                is ServerResult.Ok -> result.value
                is ServerResult.Failed -> return@signedIn result
            }
            try {
                ServerResult.Ok(
                    buckets.map { bucket ->
                        val month = YearMonth.from(LocalDate.parse(bucket.timeBucket.take(10)))
                        bucketOf[month] = bucket.timeBucket
                        TimelineMonth(month, bucket.count)
                    },
                )
            } catch (_: DateTimeParseException) {
                ServerResult.Failed(Problem.Unreachable)
            }
        }

        override suspend fun photos(month: YearMonth): ServerResult<List<Photo>> = signedIn { session ->
            bucket(session, album, bucketOf[month] ?: "$month-01T00:00:00.000Z")
        }
    }

    private suspend fun buckets(session: Session, album: Album): ServerResult<List<BucketDto>> =
        get(session, "timeline/buckets", listOf("albumId" to album.id.value, "order" to album.order))

    private suspend fun bucket(session: Session, album: Album, timeBucket: String): ServerResult<List<Photo>> =
        get<BucketAssetsDto>(
            session,
            "timeline/bucket",
            listOf("albumId" to album.id.value, "timeBucket" to timeBucket, "order" to album.order),
        ).map { it.toPhotos() }

    private suspend fun myId(session: Session): ServerResult<String> {
        me?.let { (cachedFor, id) -> if (cachedFor == session) return ServerResult.Ok(id) }
        return when (val result = get<MeDto>(session, "users/me")) {
            is ServerResult.Ok -> {
                me = session to result.value.id
                ServerResult.Ok(result.value.id)
            }
            is ServerResult.Failed -> result
        }
    }

    private suspend fun <T> signedIn(block: suspend (Session) -> ServerResult<T>): ServerResult<T> {
        val session = sessions.load() ?: return ServerResult.Failed(Problem.NotSignedIn)
        return block(session)
    }

    /** One `GET` with the key header, read as [T]; every failure becomes a [Problem]. */
    private suspend inline fun <reified T> get(
        session: Session,
        path: String,
        query: List<Pair<String, String>> = emptyList(),
    ): ServerResult<T> = withContext(Dispatchers.IO) {
        try {
            val url = apiUrl(session.server, path).newBuilder()
                .apply { for ((name, value) in query) addQueryParameter(name, value) }
                .build()
            val request = Request.Builder().url(url).header(API_KEY_HEADER, session.apiKey.value).get().build()
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> decode<T>(response.body?.string())
                        ?.let { ServerResult.Ok(it) }
                        ?: ServerResult.Failed(Problem.Unreachable)
                    401 -> ServerResult.Failed(Problem.KeyRejected)
                    403 -> ServerResult.Failed(Problem.KeyLacksPermission)
                    else -> ServerResult.Failed(Problem.Unreachable)
                }
            }
        } catch (_: IOException) {
            ServerResult.Failed(Problem.Unreachable)
        } catch (_: IllegalArgumentException) {
            // The address passed the domain's checks but OkHttp can't build a URL from it.
            ServerResult.Failed(Problem.Unreachable)
        }
    }

    private companion object {
        /** Bucket requests at once: enough to be quick, few enough not to swamp a small server. */
        const val MAX_IN_FLIGHT = 4
    }
}

private val Album.order get() = if (newestFirst) "desc" else "asc"

private inline fun <reified T> decode(body: String?): T? = try {
    body?.let { immichJson.decodeFromString<T>(it) }
} catch (_: SerializationException) {
    null
} catch (_: IllegalArgumentException) {
    null
}

/** Maps every item, or fails as [Problem.Unreachable] if one can't be read (a bad date). */
private inline fun <A, B> mapAll(items: List<A>, transform: (A) -> B): ServerResult<List<B>> = try {
    ServerResult.Ok(items.map(transform))
} catch (_: DateTimeParseException) {
    ServerResult.Failed(Problem.Unreachable)
}

/** The part of `UserAdminResponseDto` this reads; `id` is required in every version. */
@Serializable
private data class MeDto(val id: String)

/** The part of `AlbumResponseDto` the app reads. */
@Serializable
private data class AlbumDto(
    val id: String,
    val albumName: String,
    val albumThumbnailAssetId: String? = null,
    val assetCount: Int,
    val shared: Boolean,
    val updatedAt: String,
    val lastModifiedAssetTimestamp: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val order: String? = null,
    val ownerId: String? = null,
    val albumUsers: List<AlbumUserDto> = emptyList(),
) {
    fun toAlbum(myId: String): Album {
        // v1/v2 send `ownerId` (and their `albumUsers` lists only the people it is shared with);
        // v3 dropped it and puts the owner first in `albumUsers`.
        val owner = ownerId ?: albumUsers.firstOrNull()?.user?.id
        val updated = Instant.parse(updatedAt)
        val lastAdded = lastModifiedAssetTimestamp?.let(Instant::parse)
        return Album(
            id = AlbumId(id),
            name = albumName,
            itemCount = assetCount,
            cover = albumThumbnailAssetId?.let(::PhotoId),
            shared = shared,
            ownedByMe = owner == myId,
            updatedAt = if (lastAdded != null && lastAdded > updated) lastAdded else updated,
            startDate = startDate?.let(::calendarDay),
            endDate = endDate?.let(::calendarDay),
            newestFirst = order != "asc",
        )
    }
}

/**
 * `startDate`/`endDate` are the "UTC representation of (local)" date: the day the photo was taken,
 * written as midnight UTC. Read in the device's zone they would land on the day before, west of UTC.
 */
private fun calendarDay(value: String): LocalDate = Instant.parse(value).atOffset(ZoneOffset.UTC).toLocalDate()

@Serializable
private data class AlbumUserDto(val user: UserDto)

@Serializable
private data class UserDto(val id: String)

/** `TimeBucketsResponseDto`: a month (`timeBucket`, its first day) and how many items it has. */
@Serializable
private data class BucketDto(val timeBucket: String, val count: Int)

/** The two columns of the columnar `TimeBucketAssetResponseDto` the app reads. */
@Serializable
private data class BucketAssetsDto(val id: List<String>, val isImage: List<Boolean>) {
    fun toPhotos(): List<Photo> = id.zip(isImage) { id, image -> Photo(PhotoId(id), isVideo = !image) }
}
