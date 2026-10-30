package io.github.yasarmahomedabbas.immichtv.data.immich

import io.github.yasarmahomedabbas.immichtv.domain.Problem
import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import io.github.yasarmahomedabbas.immichtv.domain.photos.TimelineMonth
import io.github.yasarmahomedabbas.immichtv.domain.server.Session
import io.github.yasarmahomedabbas.immichtv.domain.server.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException

/** The calls every Immich repository makes: the stored session, and a `GET` with its key header. */
internal class ImmichApi(
    internal val client: OkHttpClient,
    private val sessions: SessionStore,
) {
    suspend fun <T> signedIn(block: suspend (Session) -> ServerResult<T>): ServerResult<T> {
        val session = sessions.load() ?: return ServerResult.Failed(Problem.NotSignedIn)
        return block(session)
    }

    /** One `GET` with the key header, read as [T]; every failure becomes a [Problem]. */
    suspend inline fun <reified T> get(
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
}

internal inline fun <reified T> decode(body: String?): T? = try {
    body?.let { immichJson.decodeFromString<T>(it) }
} catch (_: SerializationException) {
    null
} catch (_: IllegalArgumentException) {
    null
}

/**
 * The months these buckets stand for, or [Problem.Unreachable] if one's date can't be read. The bucket
 * string Immich sent for a month is what goes back for it (v3 sends `YYYY-MM-DD`, older servers a
 * full timestamp), so each is handed to [remember].
 */
internal fun List<BucketDto>.toMonths(remember: (YearMonth, String) -> Unit): ServerResult<List<TimelineMonth>> = try {
    ServerResult.Ok(
        map { bucket ->
            val month = YearMonth.from(LocalDate.parse(bucket.timeBucket.take(10)))
            remember(month, bucket.timeBucket)
            TimelineMonth(month, bucket.count)
        },
    )
} catch (_: DateTimeParseException) {
    ServerResult.Failed(Problem.Unreachable)
}
