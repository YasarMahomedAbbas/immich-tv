package io.github.yasarmahomedabbas.immichtv.data.settings

import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumId
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.ScreensaverSettingsStore
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.ScreensaverSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What the screensaver plays, on the device: `screensaver.source` holding `library` or
 * `album:<id>`. Anything missing or unreadable is the library.
 */
internal class StoredScreensaverSettings(private val storage: StringStorage) : ScreensaverSettingsStore {

    override suspend fun load(): ScreensaverSource = withContext(Dispatchers.IO) {
        storage.get(KEY)?.let(::decode) ?: ScreensaverSource.Library
    }

    override suspend fun save(source: ScreensaverSource) = withContext(Dispatchers.IO) {
        storage.put(mapOf(KEY to encode(source)))
    }

    private fun encode(source: ScreensaverSource) = when (source) {
        ScreensaverSource.Library -> LIBRARY
        is ScreensaverSource.Album -> ALBUM_PREFIX + source.id.value
    }

    private fun decode(value: String): ScreensaverSource? = when {
        value == LIBRARY -> ScreensaverSource.Library
        // Only the first colon splits: what follows is the album's id, whatever is in it.
        value.startsWith(ALBUM_PREFIX) -> value.removePrefix(ALBUM_PREFIX).takeIf { it.isNotEmpty() }?.let { ScreensaverSource.Album(AlbumId(it)) }
        else -> null
    }

    private companion object {
        const val KEY = "screensaver.source"
        const val LIBRARY = "library"
        const val ALBUM_PREFIX = "album:"
    }
}
