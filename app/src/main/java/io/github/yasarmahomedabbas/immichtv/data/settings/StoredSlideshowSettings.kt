package io.github.yasarmahomedabbas.immichtv.data.settings

import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumId
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.SlideshowOrder
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.SlideshowSettings
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.SlideshowSettingsStore
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.VideoMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Each album's slideshow settings on the device, one key per album (`slideshow.<id>`) holding
 * `seconds|ORDER|VIDEOS|showDateAndPlace`. Anything missing or unreadable is the defaults.
 */
internal class StoredSlideshowSettings(private val storage: StringStorage) : SlideshowSettingsStore {

    override suspend fun load(album: AlbumId): SlideshowSettings = withContext(Dispatchers.IO) {
        storage.get(key(album))?.let(::decode) ?: SlideshowSettings()
    }

    override suspend fun save(album: AlbumId, settings: SlideshowSettings) = withContext(Dispatchers.IO) {
        storage.put(mapOf(key(album) to encode(settings)))
    }

    private fun key(album: AlbumId) = "slideshow.${album.value}"

    private fun encode(s: SlideshowSettings) = listOf(s.secondsPerPhoto, s.order.name, s.videos.name, s.showDateAndPlace).joinToString("|")

    private fun decode(value: String): SlideshowSettings? {
        val parts = value.split("|")
        if (parts.size != 4) return null
        return SlideshowSettings(
            secondsPerPhoto = parts[0].toIntOrNull()?.takeIf { it > 0 } ?: return null,
            order = SlideshowOrder.entries.firstOrNull { it.name == parts[1] } ?: return null,
            videos = VideoMode.entries.firstOrNull { it.name == parts[2] } ?: return null,
            showDateAndPlace = parts[3].toBooleanStrictOrNull() ?: return null,
        )
    }
}
