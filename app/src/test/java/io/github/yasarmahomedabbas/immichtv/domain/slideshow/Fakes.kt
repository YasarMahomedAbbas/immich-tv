package io.github.yasarmahomedabbas.immichtv.domain.slideshow

import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumId

/** Settings in a map, recording every save. */
class FakeSlideshowSettingsStore : SlideshowSettingsStore {
    val saved = mutableMapOf<AlbumId, SlideshowSettings>()
    val saves = mutableListOf<Pair<AlbumId, SlideshowSettings>>()

    override suspend fun load(album: AlbumId) = saved[album] ?: SlideshowSettings()

    override suspend fun save(album: AlbumId, settings: SlideshowSettings) {
        saved[album] = settings
        saves += album to settings
    }
}
