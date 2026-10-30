package io.github.yasarmahomedabbas.immichtv.domain.slideshow

import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import io.github.yasarmahomedabbas.immichtv.domain.albums.Album
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumRepository
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.domain.map
import io.github.yasarmahomedabbas.immichtv.domain.photos.LibraryRepository
import java.time.LocalDate
import kotlin.random.Random

/**
 * One item of an album or the library, as a slideshow needs it.
 *
 * @property portrait taller than wide, so it sits on a backdrop of itself; false when unknown.
 * @property takenOn the calendar day where it was taken, when known.
 */
data class Slide(val photo: Photo, val portrait: Boolean, val takenOn: LocalDate?, val place: Place?)

/** Where a photo was taken: never both null. */
data class Place(val city: String?, val country: String?)

/** What a slideshow plays, and the slide it begins on. */
data class Playlist(val slides: List<Slide>, val start: Int = 0)

/** What a slideshow of an album plays: its photos, in the order the settings ask for. */
class LoadSlideshow(private val repository: AlbumRepository, private val random: Random = Random.Default) {
    suspend operator fun invoke(album: Album, order: SlideshowOrder): ServerResult<List<Slide>> =
        repository.slides(album).map { all ->
            // Videos are left out whatever the Videos setting says, until the slideshow can play them.
            val photos = all.filterNot { it.photo.isVideo }
            when (order) {
                SlideshowOrder.OldestFirst -> photos
                SlideshowOrder.NewestFirst -> photos.reversed()
                SlideshowOrder.Shuffle -> photos.shuffled(random)
            }
        }
}

/**
 * What a slideshow of the whole library plays, newest to oldest, beginning at [invoke]'s photo: the
 * first photo at or after it, since a video can't play yet. A video with no photo after it, or a
 * photo no longer there, begins at the newest.
 */
class LoadLibrarySlideshow(private val repository: LibraryRepository) {
    suspend operator fun invoke(from: PhotoId): ServerResult<Playlist> =
        repository.slides().map { all ->
            val photos = all.filterNot { it.photo.isVideo }
            val at = all.indexOfFirst { it.photo.id == from }
            val first = if (at < 0) null else all.drop(at).firstOrNull { !it.photo.isVideo }
            Playlist(photos, start = first?.let(photos::indexOf) ?: 0)
        }
}
