package io.github.yasarmahomedabbas.immichtv.domain.photos

import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.Slide

/** Everything on the user's own timeline, newest first. */
interface LibraryRepository {
    fun timeline(): Timeline

    /** Every item, newest first. */
    suspend fun slides(): ServerResult<List<Slide>>
}

class LibraryTimeline(private val repository: LibraryRepository) {
    operator fun invoke(): Timeline = repository.timeline()
}
