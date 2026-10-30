package io.github.yasarmahomedabbas.immichtv.data.immich

import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.map
import io.github.yasarmahomedabbas.immichtv.domain.photos.LibraryRepository
import io.github.yasarmahomedabbas.immichtv.domain.photos.Timeline
import io.github.yasarmahomedabbas.immichtv.domain.photos.TimelineMonth
import io.github.yasarmahomedabbas.immichtv.domain.server.SessionStore
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.Slide
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import java.time.YearMonth
import java.util.concurrent.ConcurrentHashMap

/**
 * The user's own timeline from Immich v1.133+, as Immich's own Photos page shows it: `/api/timeline/buckets`
 * and `/api/timeline/bucket` without `albumId`, stacks collapsed. Partners' photos stay out.
 */
internal class ImmichLibraryRepository(client: OkHttpClient, sessions: SessionStore) : LibraryRepository {
    private val api = ImmichApi(client, sessions)

    override fun timeline(): Timeline = LibraryMonths()

    override suspend fun slides(): ServerResult<List<Slide>> = api.signedIn { session ->
        val buckets = when (val result = api.get<List<BucketDto>>(session, "timeline/buckets", QUERY)) {
            is ServerResult.Ok -> result.value
            is ServerResult.Failed -> return@signedIn result
        }
        val inFlight = Semaphore(MAX_IN_FLIGHT)
        val months = coroutineScope {
            buckets.map { bucket ->
                async {
                    inFlight.withPermit {
                        api.get<BucketAssetsDto>(session, "timeline/bucket", QUERY + ("timeBucket" to bucket.timeBucket))
                    }
                }
            }.awaitAll()
        }
        val slides = mutableListOf<Slide>()
        for (month in months) {
            when (month) {
                is ServerResult.Ok -> slides += month.value.toSlides()
                is ServerResult.Failed -> return@signedIn month
            }
        }
        ServerResult.Ok(slides)
    }

    /** The bucket string Immich sent for a month is what goes back for it: see [toMonths]. */
    private inner class LibraryMonths : Timeline {
        private val bucketOf = ConcurrentHashMap<YearMonth, String>()

        override suspend fun months(): ServerResult<List<TimelineMonth>> = api.signedIn { session ->
            when (val buckets = api.get<List<BucketDto>>(session, "timeline/buckets", QUERY)) {
                is ServerResult.Ok -> buckets.value.toMonths { month, bucket -> bucketOf[month] = bucket }
                is ServerResult.Failed -> buckets
            }
        }

        override suspend fun photos(month: YearMonth): ServerResult<List<Photo>> = api.signedIn { session ->
            val timeBucket = bucketOf[month] ?: "$month-01T00:00:00.000Z"
            api.get<BucketAssetsDto>(session, "timeline/bucket", QUERY + ("timeBucket" to timeBucket)).map { it.toPhotos() }
        }
    }

    private companion object {
        /** Bucket requests at once: enough to be quick, few enough not to swamp a small server. */
        const val MAX_IN_FLIGHT = 4
        val QUERY = listOf("visibility" to "timeline", "withStacked" to "true", "order" to "desc")
    }
}
