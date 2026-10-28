package io.github.yasarmahomedabbas.immichtv.domain.slideshow

import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumId

enum class SlideshowOrder { OldestFirst, Shuffle, NewestFirst }

enum class VideoMode { PlayMuted, PlayWithSound, Skip }

/**
 * How an album's slideshow plays. Each setting steps through its choices with OK on the album
 * screen, so each has a `next` that cycles in the order the screen shows them.
 */
data class SlideshowSettings(
    val secondsPerPhoto: Int = 8,
    val order: SlideshowOrder = SlideshowOrder.OldestFirst,
    val videos: VideoMode = VideoMode.PlayMuted,
    val showDateAndPlace: Boolean = true,
) {
    /** The next time per photo; a value that isn't one of [SECONDS] goes back to the first. */
    fun nextSeconds(): SlideshowSettings {
        val at = SECONDS.indexOf(secondsPerPhoto)
        return copy(secondsPerPhoto = if (at < 0) SECONDS.first() else SECONDS[(at + 1) % SECONDS.size])
    }

    fun nextOrder() = copy(order = order.next())

    fun nextVideos() = copy(videos = videos.next())

    fun toggleDateAndPlace() = copy(showDateAndPlace = !showDateAndPlace)

    companion object {
        /** The time-per-photo choices, in the order OK steps through them. */
        val SECONDS = listOf(8, 12, 20, 5)
    }
}

private inline fun <reified E : Enum<E>> E.next(): E {
    val all = enumValues<E>()
    return all[(ordinal + 1) % all.size]
}

/** Slideshow settings kept for each album. */
interface SlideshowSettingsStore {
    /** The album's settings, or the defaults if none were saved. */
    suspend fun load(album: AlbumId): SlideshowSettings
    suspend fun save(album: AlbumId, settings: SlideshowSettings)
}

class GetSlideshowSettings(private val store: SlideshowSettingsStore) {
    suspend operator fun invoke(album: AlbumId): SlideshowSettings = store.load(album)
}

class SaveSlideshowSettings(private val store: SlideshowSettingsStore) {
    suspend operator fun invoke(album: AlbumId, settings: SlideshowSettings) = store.save(album, settings)
}
