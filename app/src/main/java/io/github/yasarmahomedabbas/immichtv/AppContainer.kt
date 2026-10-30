package io.github.yasarmahomedabbas.immichtv

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import coil3.ImageLoader
import io.github.yasarmahomedabbas.immichtv.data.immich.ImmichAlbumRepository
import io.github.yasarmahomedabbas.immichtv.data.immich.ImmichLibraryRepository
import io.github.yasarmahomedabbas.immichtv.data.immich.ImmichMemoryRepository
import io.github.yasarmahomedabbas.immichtv.data.immich.ImmichSearchRepository
import io.github.yasarmahomedabbas.immichtv.data.immich.ImmichServerConnector
import io.github.yasarmahomedabbas.immichtv.data.immich.immichHttpClient
import io.github.yasarmahomedabbas.immichtv.data.immich.immichImageLoader
import io.github.yasarmahomedabbas.immichtv.data.immich.immichVideoPlayer
import io.github.yasarmahomedabbas.immichtv.data.settings.KeystoreCipher
import io.github.yasarmahomedabbas.immichtv.data.settings.SharedPreferencesStorage
import io.github.yasarmahomedabbas.immichtv.data.settings.StoredScreensaverSettings
import io.github.yasarmahomedabbas.immichtv.data.settings.StoredSessionRepository
import io.github.yasarmahomedabbas.immichtv.data.settings.StoredSlideshowSettings
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumTimeline
import io.github.yasarmahomedabbas.immichtv.domain.albums.LoadAlbum
import io.github.yasarmahomedabbas.immichtv.domain.albums.LoadAlbums
import io.github.yasarmahomedabbas.immichtv.domain.home.LoadHome
import io.github.yasarmahomedabbas.immichtv.domain.photos.LibraryTimeline
import io.github.yasarmahomedabbas.immichtv.domain.search.LoadPeople
import io.github.yasarmahomedabbas.immichtv.domain.search.LoadPhotosOf
import io.github.yasarmahomedabbas.immichtv.domain.search.SearchPhotos
import io.github.yasarmahomedabbas.immichtv.domain.server.ConnectToServer
import io.github.yasarmahomedabbas.immichtv.domain.server.GetSession
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.GetScreensaverSource
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.GetSlideshowSettings
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.LoadLibrarySlideshow
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.LoadScreensaver
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.LoadSlideshow
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.LoadSlideshowFromPhoto
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.SaveScreensaverSource
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
    val loadSlideshow = LoadSlideshow(albums)
    val loadSlideshowFromPhoto = LoadSlideshowFromPhoto(albums)

    private val library = ImmichLibraryRepository(http, sessions)
    val libraryTimeline = LibraryTimeline(library)
    val loadLibrarySlideshow = LoadLibrarySlideshow(library)

    private val memories = ImmichMemoryRepository(http, sessions)
    val loadHome = LoadHome(albums, memories, library)
    private val search = ImmichSearchRepository(http, sessions)
    val searchPhotos = SearchPhotos(search)
    val loadPeople = LoadPeople(search)
    val loadPhotosOf = LoadPhotosOf(search)

    private val slideshowStorage = SharedPreferencesStorage(context.getSharedPreferences("slideshow", Context.MODE_PRIVATE))
    private val slideshowSettings = StoredSlideshowSettings(slideshowStorage)
    private val screensaverSettings = StoredScreensaverSettings(slideshowStorage)

    /** Loads a domain `Preview` with the key header; the app's one Coil image loader. */
    fun imageLoader(context: Context): ImageLoader = immichImageLoader(context, http, sessions)

    /** Plays a domain `Video` with the key header; one per video layer on screen. */
    fun videoPlayer(context: Context): ExoPlayer = immichVideoPlayer(context, http, sessions)

    val getSlideshowSettings = GetSlideshowSettings(slideshowSettings)
    val saveSlideshowSettings = SaveSlideshowSettings(slideshowSettings)

    val getScreensaverSource = GetScreensaverSource(screensaverSettings)
    val saveScreensaverSource = SaveScreensaverSource(screensaverSettings)
    val loadScreensaver = LoadScreensaver(screensaverSettings, albums, library)
}
