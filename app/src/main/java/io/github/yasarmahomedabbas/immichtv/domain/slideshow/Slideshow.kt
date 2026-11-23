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
 * One item of an album, the library or search results, as a slideshow needs it.
 *
 * @property portrait taller than wide, so it sits on a backdrop of itself; false when unknown.
 * @property takenOn the calendar day where it was taken, when known.
 */
data class Slide(val photo: Photo, val portrait: Boolean, val takenOn: LocalDate?, val place: Place?)

/** Where a photo was taken: never both null. */
data class Place(val city: String?, val country: String?)

/** These without their videos: a Skip show, and a show of search results, which plays photos only. */
fun List<Slide>.withoutVideos(): List<Slide> = filterNot { it.photo.isVideo }

/** What a slideshow plays, and the slide it begins on. */
data class Playlist(val slides: List<Slide>, val start: Int = 0)

/**
 * An album's show and how it was ordered.
 *
 * @property shuffleSeed the seed that shuffled it, or null when it plays in order.
 */
data class AlbumShow(val playlist: Playlist, val shuffleSeed: Long?) {
    /** The spot to remember when the show is on the slide at [index]; null when there is no such slide. */
    fun resumePointAt(index: Int): ResumePoint? =
        playlist.slides.getOrNull(index)?.let { ResumePoint(it.photo.id, index, shuffleSeed) }
}

/**
 * What a slideshow of an album plays: its items, as the Videos setting keeps them, in the order the
 * settings ask for, beginning where [ShowStart] says.
 *
 * A shuffled show shuffles the whole album with its seed before Videos leaves anything out, so the same
 * seed gives the same sequence whatever Videos is now. Continue resumes the show that was left: a
 * seeded spot plays that same shuffle, an unseeded one the current Order. A remembered photo that is
 * now left out resumes at the next kept slide, and one gone from the album at its old index, or the first.
 */
class LoadSlideshow(
    private val repository: AlbumRepository,
    private val resumePoints: ResumePointStore,
    private val random: Random = Random.Default,
) {
    suspend operator fun invoke(
        album: Album,
        order: SlideshowOrder,
        videos: VideoMode,
        start: ShowStart = ShowStart.Beginning,
    ): ServerResult<AlbumShow> {
        val spot = if (start == ShowStart.Continue) resumePoints.load(album.id) else null
        return repository.slides(album).map { all ->
            val seed = when {
                spot?.shuffleSeed != null -> spot.shuffleSeed
                start == ShowStart.Shuffled || order == SlideshowOrder.Shuffle -> random.nextLong()
                else -> null
            }
            val ordered = when {
                seed != null -> all.shuffled(Random(seed))
                order == SlideshowOrder.NewestFirst -> all.reversed()
                else -> all
            }
            val kept = if (videos == VideoMode.Skip) ordered.withoutVideos() else ordered
            val index = when (start) {
                ShowStart.Beginning, ShowStart.Shuffled -> 0
                ShowStart.RandomSlide -> if (kept.isEmpty()) 0 else random.nextInt(kept.size)
                ShowStart.Continue -> when {
                    spot == null -> 0
                    ordered.any { it.photo.id == spot.photo } -> playlistFrom(ordered, kept, spot.photo).start
                    spot.index in kept.indices -> spot.index
                    else -> 0
                }
            }
            AlbumShow(Playlist(kept, index), seed)
        }
    }
}

/**
 * What a slideshow of the whole library plays, newest to oldest with its videos in place, beginning
 * at [invoke]'s item, photo or video. An item no longer there begins at the newest.
 */
class LoadLibrarySlideshow(private val repository: LibraryRepository) {
    suspend operator fun invoke(from: PhotoId): ServerResult<Playlist> =
        repository.slides().map { all ->
            playlistFrom(all, all, from)
        }
}

/**
 * What a slideshow of an album plays from a tile of its grid, beginning at [invoke]'s photo. It follows
 * the grid's order, not the saved Order, as a show started from a tile carries on from where the grid is.
 */
class LoadSlideshowFromPhoto(private val repository: AlbumRepository) {
    suspend operator fun invoke(album: Album, from: PhotoId, videos: VideoMode): ServerResult<Playlist> =
        repository.slides(album).map { oldestFirst ->
            val all = if (album.newestFirst) oldestFirst.reversed() else oldestFirst
            playlistFrom(all, if (videos == VideoMode.Skip) all.withoutVideos() else all, from)
        }
}

/**
 * A show of [kept], which is [all] less what the show leaves out, both in play order. It begins at the
 * first kept slide at or after [from] in [all]; with none after it, or [from] not in [all], at the first.
 */
internal fun playlistFrom(all: List<Slide>, kept: List<Slide>, from: PhotoId): Playlist {
    val at = all.indexOfFirst { it.photo.id == from }
    val keptIds = kept.mapTo(HashSet()) { it.photo.id }
    val first = if (at < 0) null else all.drop(at).firstOrNull { it.photo.id in keptIds }
    return Playlist(kept, start = first?.let { slide -> kept.indexOfFirst { it.photo.id == slide.photo.id } } ?: 0)
}
