package io.github.yasarmahomedabbas.immichtv.domain.photos

import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import kotlinx.coroutines.CompletableDeferred
import java.time.YearMonth

/** A timeline whose answers a test sets, recording which months were asked for. */
class FakeTimeline(
    var months: ServerResult<List<TimelineMonth>> = ServerResult.Ok(emptyList()),
    val photos: MutableMap<YearMonth, ServerResult<List<Photo>>> = mutableMapOf(),
) : Timeline {
    var monthsCalls = 0
    val asked = mutableListOf<YearMonth>()

    /** While set, reading the months waits for it: a request still on its way. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun months(): ServerResult<List<TimelineMonth>> {
        monthsCalls++
        gate?.await()
        return months
    }

    override suspend fun photos(month: YearMonth): ServerResult<List<Photo>> {
        asked += month
        return photos[month] ?: ServerResult.Ok(emptyList())
    }
}
