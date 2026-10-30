package io.github.yasarmahomedabbas.immichtv.domain.slideshow

import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumId
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumRepository
import io.github.yasarmahomedabbas.immichtv.domain.map
import io.github.yasarmahomedabbas.immichtv.domain.photos.LibraryRepository
import kotlin.random.Random

/** What the TV's screensaver plays. */
sealed interface ScreensaverSource {
    /** A random selection of the whole library. */
    data object Library : ScreensaverSource

    data class Album(val id: AlbumId) : ScreensaverSource
}

/** The screensaver's source, kept on the device. */
interface ScreensaverSettingsStore {
    /** The stored source, or [ScreensaverSource.Library] if none was saved. */
    suspend fun load(): ScreensaverSource
    suspend fun save(source: ScreensaverSource)
}

class GetScreensaverSource(private val store: ScreensaverSettingsStore) {
    suspend operator fun invoke(): ScreensaverSource = store.load()
}

class SaveScreensaverSource(private val store: ScreensaverSettingsStore) {
    suspend operator fun invoke(source: ScreensaverSource) = store.save(source)
}

/** How the screensaver plays, whatever its source: nothing here is a choice, and an album's saved settings don't apply. */
val ScreensaverShow = SlideshowSettings(
    secondsPerPhoto = 8,
    order = SlideshowOrder.Shuffle,
    videos = VideoMode.Skip,
    showDateAndPlace = true,
)

/** The most photos a library screensaver holds on to: the TV has about 2 GB of RAM. */
const val MAX_LIBRARY_PHOTOS = 500

/**
 * What the screensaver plays: the stored source's photos, shuffled. A failure passes through and
 * leaves the stored choice alone, so an album that comes back plays again.
 */
class LoadScreensaver(
    private val store: ScreensaverSettingsStore,
    private val albums: AlbumRepository,
    private val library: LibraryRepository,
    private val random: Random = Random.Default,
) {
    suspend operator fun invoke(): ServerResult<Playlist> = when (val source = store.load()) {
        ScreensaverSource.Library -> library.slides().map { Playlist(shuffledPhotos(it).take(MAX_LIBRARY_PHOTOS)) }
        is ScreensaverSource.Album -> when (val album = albums.album(source.id)) {
            is ServerResult.Ok -> albums.slides(album.value).map { Playlist(shuffledPhotos(it)) }
            is ServerResult.Failed -> album
        }
    }

    private fun shuffledPhotos(slides: List<Slide>) = slides.filterNot { it.photo.isVideo }.shuffled(random)
}
