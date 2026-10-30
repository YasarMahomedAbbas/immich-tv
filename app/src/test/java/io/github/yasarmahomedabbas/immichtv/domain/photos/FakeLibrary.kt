package io.github.yasarmahomedabbas.immichtv.domain.photos

import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.Slide

/** A library whose answers a test sets. */
class FakeLibraryRepository(
    private val timeline: Timeline = FakeTimeline(),
    var slides: ServerResult<List<Slide>> = ServerResult.Ok(emptyList()),
) : LibraryRepository {
    override fun timeline(): Timeline = timeline

    override suspend fun slides(): ServerResult<List<Slide>> = slides
}
