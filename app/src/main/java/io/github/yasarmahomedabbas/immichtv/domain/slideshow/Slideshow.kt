package io.github.yasarmahomedabbas.immichtv.domain.slideshow

import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import io.github.yasarmahomedabbas.immichtv.domain.albums.Album
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumRepository
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.map
import java.time.LocalDate
import kotlin.random.Random

/**
 * One photo or video, as a slideshow needs it.
 *
 * @property portrait taller than wide, so it sits on a backdrop of itself; false when unknown.
 * @property takenOn the calendar day where it was taken, when known.
 */
data class Slide(val photo: Photo, val portrait: Boolean, val takenOn: LocalDate?, val place: Place?)

/** Where a photo was taken: never both null. */
data class Place(val city: String?, val country: String?)

/** What of these a slideshow can play: videos are left out until it can play them. */
fun List<Slide>.playable(): List<Slide> = filterNot { it.photo.isVideo }

/** What a slideshow of an album plays: its photos, in the order the settings ask for. */
class LoadSlideshow(private val repository: AlbumRepository, private val random: Random = Random.Default) {
    suspend operator fun invoke(album: Album, order: SlideshowOrder): ServerResult<List<Slide>> =
        repository.slides(album).map { all ->
            // Whatever the Videos setting says.
            val photos = all.playable()
            when (order) {
                SlideshowOrder.OldestFirst -> photos
                SlideshowOrder.NewestFirst -> photos.reversed()
                SlideshowOrder.Shuffle -> photos.shuffled(random)
            }
        }
}
