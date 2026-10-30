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
import io.github.yasarmahomedabbas.immichtv.domain.home.timeline
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.SlideshowSettings
import io.github.yasarmahomedabbas.immichtv.ui.AppScreen
import io.github.yasarmahomedabbas.immichtv.ui.AppViewModel
import io.github.yasarmahomedabbas.immichtv.ui.CameFrom
import io.github.yasarmahomedabbas.immichtv.ui.Tab
import io.github.yasarmahomedabbas.immichtv.ui.album.AlbumPhotosScreen
import io.github.yasarmahomedabbas.immichtv.ui.album.AlbumScreen
import io.github.yasarmahomedabbas.immichtv.ui.album.AlbumViewModel
import io.github.yasarmahomedabbas.immichtv.ui.albums.AlbumsScreen
import io.github.yasarmahomedabbas.immichtv.ui.albums.AlbumsViewModel
import io.github.yasarmahomedabbas.immichtv.ui.home.HomeScreen
import io.github.yasarmahomedabbas.immichtv.ui.home.HomeViewModel
import io.github.yasarmahomedabbas.immichtv.ui.main.MainScreen
import io.github.yasarmahomedabbas.immichtv.ui.photos.PhotoGridViewModel
import io.github.yasarmahomedabbas.immichtv.ui.photos.PhotoViewerScreen
import io.github.yasarmahomedabbas.immichtv.ui.photos.PhotosScreen
import io.github.yasarmahomedabbas.immichtv.ui.search.SearchScreen
import io.github.yasarmahomedabbas.immichtv.ui.search.SearchViewModel
import io.github.yasarmahomedabbas.immichtv.ui.search.rememberVoiceInput
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
                    is AppScreen.Main -> {
                        // Scoped to the activity, so the results survive a slideshow of them.
                        val search: SearchViewModel = viewModel(key = "search-$connection", factory = SearchViewModel.factory(container))
                        val voice = rememberVoiceInput { search.submit(it) }
                        MainScreen(
                            tab = current.tab,
                            onSelectTab = app::selectTab,
                            onOpenSettings = app::openSettings,
                            onVoiceKey = voice,
                            home = {
                                // The grid's ViewModel, so the viewer opened from a tile moves through the timeline.
                                val photos: PhotoGridViewModel =
                                    viewModel(key = "photos-$connection", factory = PhotoGridViewModel.factory(container.libraryTimeline()))
                                HomeScreen(
                                    // Scoped to the activity, so the page and the focused item survive leaving Home.
                                    viewModel = viewModel(key = "home-$connection", factory = HomeViewModel.factory(container)),
                                    onPlayMemory = app::openMemorySlideshow,
                                    onMemoryPhotos = app::openMemoryPhotos,
                                    onPlayAlbum = { album, settings -> app.openSlideshow(album, settings, CameFrom.HomeHero) },
                                    onAlbumPhotos = { app.openAlbumPhotos(it, CameFrom.HomeHero) },
                                    onOpenAlbum = { app.openAlbum(it, CameFrom.HomeRow) },
                                    onOpenRecent = { photo ->
                                        photos.open(photo)
                                        // Nothing else refreshes it while Home is up, and the tile may be newer than its months.
                                        photos.refresh()
                                        app.openPhotoViewer(fromHome = true)
                                    },
                                    onSeeAll = { app.selectTab(Tab.Albums) },
                                )
                            },
                            search = { SearchScreen(search, onVoice = voice, onPlay = app::openResultsSlideshow, focusResults = current.focusResults) },
                            albums = {
                                // Scoped to the activity, so the list and the focused album survive opening one.
                                AlbumsScreen(
                                    viewModel(key = "albums-$connection", factory = AlbumsViewModel.factory(container)),
                                    onOpenAlbum = { app.openAlbum(it) },
                                )
                            },
                            photos = {
                                // The viewer uses the same ViewModel, so the grid knows where it got to.
                                PhotosScreen(
                                    viewModel(key = "photos-$connection", factory = PhotoGridViewModel.factory(container.libraryTimeline())),
                                    onOpenPhoto = { app.openPhotoViewer() },
                                    onStartSlideshow = app::openTimelineSlideshow,
                                )
                            },
                        )
                    }
                    is AppScreen.Album -> AlbumScreen(
                        viewModel = viewModel(
                            key = "album-$connection-${current.id.value}",
                            factory = AlbumViewModel.factory(container, current.id),
                        ),
                        onBack = { app.back() },
                        onBrowsePhotos = { app.openAlbumPhotos(it, current.cameFrom) },
                        onStartSlideshow = { album, settings -> app.openSlideshow(album, settings, current.cameFrom) },
                        focusBrowse = current.focusBrowse,
                        cameFrom = current.cameFrom,
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
                    is AppScreen.MemoryPhotos -> AlbumPhotosScreen(
                        albumName = current.title,
                        viewModel = viewModel(
                            key = "memory-photos-$connection-${current.memory.hashCode()}",
                            factory = PhotoGridViewModel.factory(current.memory.timeline()),
                        ),
                        onBack = { app.back() },
                    )
                    is AppScreen.PhotoViewer -> PhotoViewerScreen(
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
                        newPlayer = container::videoPlayer,
                        onBack = { app.back() },
                        source = if (current.cameFrom == CameFrom.HomeHero) SlideshowSource.Home else SlideshowSource.Album,
                    )
                    is AppScreen.MemorySlideshow -> SlideshowScreen(
                        viewModel = viewModel(
                            key = "slideshow-$connection-${System.identityHashCode(current)}",
                            factory = SlideshowViewModel.fromMemory(current.memory),
                        ),
                        settings = TimelineSettings,
                        newPlayer = container::videoPlayer,
                        onBack = { app.back() },
                        source = SlideshowSource.Home,
                    )
                    is AppScreen.ResultsSlideshow -> SlideshowScreen(
                        // A fresh ViewModel each time it starts, so it begins at the first photo.
                        viewModel = viewModel(
                            key = "results-slideshow-$connection-${System.identityHashCode(current)}",
                            factory = SlideshowViewModel.factory(current.slides),
                        ),
                        settings = SlideshowSettings(),
                        newPlayer = container::videoPlayer,
                        onBack = { app.back() },
                        source = SlideshowSource.Search,
                    )
                    is AppScreen.TimelineSlideshow -> SlideshowScreen(
                        viewModel = viewModel(
                            key = "slideshow-$connection-${System.identityHashCode(current)}",
                            factory = SlideshowViewModel.fromTimeline(container, current.from),
                        ),
                        settings = TimelineSettings,
                        newPlayer = container::videoPlayer,
                        onBack = { app.back() },
                        source = SlideshowSource.Library,
                    )
                }
            }
        }
    }
}
