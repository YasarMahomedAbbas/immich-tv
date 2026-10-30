package io.github.yasarmahomedabbas.immichtv.domain.albums

import java.time.Instant
import java.time.LocalDate

@JvmInline
value class AlbumId(val value: String)

@JvmInline
value class PhotoId(val value: String)

/** One item in the library: a photo, or a video when [isVideo]. */
data class Photo(val id: PhotoId, val isVideo: Boolean)

/** A preview-size image of [photo]: what a screen hands the image loader. */
data class Preview(val photo: PhotoId)

/** A video to play: what a screen hands the player. */
data class Video(val photo: PhotoId)

/**
 * An album the user can see.
 *
 * @property itemCount photos and videos together.
 * @property cover the photo shown for it, or null when the album has none.
 * @property ownedByMe false when someone else shared it with the user.
 * @property updatedAt when it last changed, its contents included.
 * @property startDate the calendar day of the oldest item, where it was taken; null for an empty album.
 * @property endDate the calendar day of the newest item, likewise.
 * @property newestFirst the order the album is set to show its items in.
 */
data class Album(
    val id: AlbumId,
    val name: String,
    val itemCount: Int,
    val cover: PhotoId?,
    val shared: Boolean,
    val ownedByMe: Boolean,
    val updatedAt: Instant,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val newestFirst: Boolean,
)

/** How many photos and videos an album holds, and its first [firstItems] in the album's order. */
data class AlbumContents(val photoCount: Int, val videoCount: Int, val firstItems: List<Photo>)

enum class AlbumFilter { All, Mine, SharedWithMe }

enum class AlbumSort { RecentlyUpdated, Name, MostItems }

/** The albums [filter] keeps, in [sort] order; ties go by name. */
fun List<Album>.filterAndSort(filter: AlbumFilter, sort: AlbumSort): List<Album> {
    val kept = when (filter) {
        AlbumFilter.All -> this
        AlbumFilter.Mine -> filter { it.ownedByMe }
        AlbumFilter.SharedWithMe -> filter { !it.ownedByMe }
    }
    val byName = compareBy(String.CASE_INSENSITIVE_ORDER) { album: Album -> album.name }
    val order = when (sort) {
        AlbumSort.RecentlyUpdated -> compareByDescending<Album> { it.updatedAt }.then(byName)
        AlbumSort.Name -> byName
        AlbumSort.MostItems -> compareByDescending<Album> { it.itemCount }.then(byName)
    }
    return kept.sortedWith(order)
}
