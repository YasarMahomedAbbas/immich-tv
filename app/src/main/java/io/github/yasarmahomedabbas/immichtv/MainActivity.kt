package io.github.yasarmahomedabbas.immichtv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import io.github.yasarmahomedabbas.immichtv.ui.AppScreen
import io.github.yasarmahomedabbas.immichtv.ui.AppViewModel
import io.github.yasarmahomedabbas.immichtv.ui.album.AlbumPhotosScreen
import io.github.yasarmahomedabbas.immichtv.ui.album.AlbumScreen
import io.github.yasarmahomedabbas.immichtv.ui.album.AlbumViewModel
import io.github.yasarmahomedabbas.immichtv.ui.albums.AlbumsScreen
import io.github.yasarmahomedabbas.immichtv.ui.albums.AlbumsViewModel
import io.github.yasarmahomedabbas.immichtv.ui.main.MainScreen
import io.github.yasarmahomedabbas.immichtv.ui.photos.PhotoGridViewModel
import io.github.yasarmahomedabbas.immichtv.ui.photos.PhotoViewerScreen
import io.github.yasarmahomedabbas.immichtv.ui.photos.PhotosScreen
import io.github.yasarmahomedabbas.immichtv.ui.setup.SetupScreen
import io.github.yasarmahomedabbas.immichtv.ui.setup.SetupViewModel
import io.github.yasarmahomedabbas.immichtv.ui.slideshow.SlideshowScreen
import io.github.yasarmahomedabbas.immichtv.ui.slideshow.SlideshowSource
import io.github.yasarmahomedabbas.immichtv.ui.slideshow.SlideshowViewModel
import io.github.yasarmahomedabbas.immichtv.ui.slideshow.TimelineSettings
import io.github.yasarmahomedabbas.immichtv.ui.theme.ImmichTvTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as ImmichTvApp).container
        setContent {
            ImmichTvTheme {
                val app: AppViewModel = viewModel(factory = AppViewModel.factory(container))
                val screen by app.screen.collectAsState()
                val connection by app.connection.collectAsState()
                BackHandler { if (!app.back()) finish() }
                when (val current = screen) {
                    AppScreen.Loading -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                    is AppScreen.Setup -> SetupScreen(
                        // A fresh ViewModel each time Setup opens, so it is prefilled from that session.
                        viewModel = viewModel(
                            key = "setup-${System.identityHashCode(current)}",
                            factory = SetupViewModel.factory(container, current.current),
                        ),
                        firstRun = current.firstRun,
                        onConnected = app::connected,
                        onBack = { app.back() },
                    )
                    is AppScreen.Main -> MainScreen(
                        tab = current.tab,
                        onSelectTab = app::selectTab,
                        onOpenSettings = app::openSettings,
                        albums = {
                            // Scoped to the activity, so the list and the focused album survive opening one.
                            AlbumsScreen(
                                viewModel(key = "albums-$connection", factory = AlbumsViewModel.factory(container)),
                                onOpenAlbum = app::openAlbum,
                            )
                        },
                        photos = {
                            // The viewer uses the same ViewModel, so the grid knows where it got to.
                            PhotosScreen(
                                viewModel(key = "photos-$connection", factory = PhotoGridViewModel.factory(container.libraryTimeline())),
                                onOpenPhoto = app::openPhotoViewer,
                                onStartSlideshow = app::openTimelineSlideshow,
                            )
                        },
                    )
                    is AppScreen.Album -> AlbumScreen(
                        viewModel = viewModel(
                            key = "album-$connection-${current.id.value}",
                            factory = AlbumViewModel.factory(container, current.id),
                        ),
                        onBack = { app.back() },
                        onBrowsePhotos = app::openAlbumPhotos,
                        onStartSlideshow = app::openSlideshow,
                        focusBrowse = current.focusBrowse,
                    )
                    is AppScreen.AlbumPhotos -> AlbumPhotosScreen(
                        albumName = current.album.name,
                        viewModel = viewModel(
                            // The timeline keeps the order it was created with, so a new order needs a new grid.
                            key = "album-photos-$connection-${current.album.id.value}-${current.album.newestFirst}",
                            factory = PhotoGridViewModel.factory(container.albumTimeline(current.album)),
                        ),
                        onBack = { app.back() },
                    )
                    AppScreen.PhotoViewer -> PhotoViewerScreen(
                        viewModel = viewModel(key = "photos-$connection", factory = PhotoGridViewModel.factory(container.libraryTimeline())),
                        onBack = { app.back() },
                    )
                    is AppScreen.Slideshow -> SlideshowScreen(
                        // A fresh ViewModel each time it starts, so it begins at the first photo.
                        viewModel = viewModel(
                            key = "slideshow-$connection-${System.identityHashCode(current)}",
                            factory = SlideshowViewModel.factory(container, current.album, current.settings),
                        ),
                        settings = current.settings,
                        onBack = { app.back() },
                    )
                    is AppScreen.TimelineSlideshow -> SlideshowScreen(
                        viewModel = viewModel(
                            key = "slideshow-$connection-${System.identityHashCode(current)}",
                            factory = SlideshowViewModel.fromTimeline(container, current.from),
                        ),
                        settings = TimelineSettings,
                        onBack = { app.back() },
                        source = SlideshowSource.Library,
                    )
                }
            }
        }
    }
}
