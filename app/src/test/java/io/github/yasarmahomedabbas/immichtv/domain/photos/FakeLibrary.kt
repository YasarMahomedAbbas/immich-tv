package io.github.yasarmahomedabbas.immichtv.domain.photos

import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.Slide

/** A library whose answers a test sets. */
class FakeLibraryRepository(
    private val timeline: Timeline = FakeTimeline(),
    var slides: ServerResult<List<Slide>> = ServerResult.Ok(emptyList()),
    var recent: ServerResult<List<Photo>> = ServerResult.Ok(emptyList()),
) : LibraryRepository {
    override fun timeline(): Timeline = timeline

    override suspend fun slides(): ServerResult<List<Slide>> = slides

    val recentLimits = mutableListOf<Int>()

    override suspend fun recent(limit: Int): ServerResult<List<Photo>> {
        recentLimits += limit
        return recent
    }
}
