package io.github.yasarmahomedabbas.immichtv.domain.photos

import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import java.time.YearMonth

/** A month in a [Timeline] and how many photos and videos it holds. */
data class TimelineMonth(val month: YearMonth, val count: Int)

/**
 * A set of photos grouped by month, read a month at a time so a long one opens quickly. An
 * album gives one; the whole library will too (#7). Months come in the source's own order,
 * newest or oldest first.
 */
interface Timeline {
    suspend fun months(): ServerResult<List<TimelineMonth>>

    /** The photos and videos of [month], in the timeline's order. */
    suspend fun photos(month: YearMonth): ServerResult<List<Photo>>
}
