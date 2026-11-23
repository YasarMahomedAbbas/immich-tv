package io.github.yasarmahomedabbas.immichtv.domain.slideshow

import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumId
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId

/** How an album's slideshow begins, from the album screen's buttons. */
enum class ShowStart {
    /** At the first slide, in the album's Order. */
    Beginning,

    /** At the album's remembered spot, in the show that was left; at the first slide when there is none. */
    Continue,

    /** At a random slide, in the album's Order. */
    RandomSlide,

    /** At the first slide, shuffled for this show only, whatever the album's Order. */
    Shuffled,
}

/**
 * Where an album's slideshow stopped.
 *
 * @property index the photo's position in the show that saved it: where to resume if the photo is gone.
 * @property shuffleSeed the seed that shuffled that show, or null when it played in order.
 */
data class ResumePoint(val photo: PhotoId, val index: Int, val shuffleSeed: Long?)

/** Each album's remembered spot, kept on the device. */
interface ResumePointStore {
    /** The album's spot, or null if none was saved or it can't be read. */
    suspend fun load(album: AlbumId): ResumePoint?
    suspend fun save(album: AlbumId, point: ResumePoint)
}

class GetResumePoint(private val store: ResumePointStore) {
    suspend operator fun invoke(album: AlbumId): ResumePoint? = store.load(album)
}

class SaveResumePoint(private val store: ResumePointStore) {
    suspend operator fun invoke(album: AlbumId, point: ResumePoint) = store.save(album, point)
}
