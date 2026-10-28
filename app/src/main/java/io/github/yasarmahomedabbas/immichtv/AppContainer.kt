package io.github.yasarmahomedabbas.immichtv

import android.content.Context
import coil3.ImageLoader
import io.github.yasarmahomedabbas.immichtv.data.immich.ImmichAlbumRepository
import io.github.yasarmahomedabbas.immichtv.data.immich.ImmichServerConnector
import io.github.yasarmahomedabbas.immichtv.data.immich.immichHttpClient
import io.github.yasarmahomedabbas.immichtv.data.immich.immichImageLoader
import io.github.yasarmahomedabbas.immichtv.data.settings.KeystoreCipher
import io.github.yasarmahomedabbas.immichtv.data.settings.SharedPreferencesStorage
import io.github.yasarmahomedabbas.immichtv.data.settings.StoredSessionRepository
import io.github.yasarmahomedabbas.immichtv.data.settings.StoredSlideshowSettings
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumTimeline
import io.github.yasarmahomedabbas.immichtv.domain.albums.LoadAlbum
import io.github.yasarmahomedabbas.immichtv.domain.albums.LoadAlbums
import io.github.yasarmahomedabbas.immichtv.domain.server.ConnectToServer
import io.github.yasarmahomedabbas.immichtv.domain.server.GetSession
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.GetSlideshowSettings
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.SaveSlideshowSettings

/**
 * The one place that names a `data/` class: it builds the Immich implementations and hands the
 * rest of the app only domain use cases. Swapping the photo server means changing this wiring.
 */
class AppContainer(context: Context) {
    private val http = immichHttpClient()
    private val sessions = StoredSessionRepository(
        SharedPreferencesStorage(context.getSharedPreferences("session", Context.MODE_PRIVATE)),
        KeystoreCipher(),
    )

    val connectToServer = ConnectToServer(ImmichServerConnector(http), sessions)
    val getSession = GetSession(sessions)

    private val albums = ImmichAlbumRepository(http, sessions)
    val loadAlbums = LoadAlbums(albums)
    val loadAlbum = LoadAlbum(albums)
    val albumTimeline = AlbumTimeline(albums)

    private val slideshowSettings = StoredSlideshowSettings(
        SharedPreferencesStorage(context.getSharedPreferences("slideshow", Context.MODE_PRIVATE)),
    )
    /** Loads a domain `Preview` with the key header; the app's one Coil image loader. */
    fun imageLoader(context: Context): ImageLoader = immichImageLoader(context, http, sessions)

    val getSlideshowSettings = GetSlideshowSettings(slideshowSettings)
    val saveSlideshowSettings = SaveSlideshowSettings(slideshowSettings)
}
