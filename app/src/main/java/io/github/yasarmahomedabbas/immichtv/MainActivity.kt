package io.github.yasarmahomedabbas.immichtv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import io.github.yasarmahomedabbas.immichtv.domain.albums.Album
import io.github.yasarmahomedabbas.immichtv.domain.home.Memory
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
import io.github.yasarmahomedabbas.immichtv.ui.components.label
import io.github.yasarmahomedabbas.immichtv.ui.home.HomeScreen
import io.github.yasarmahomedabbas.immichtv.ui.home.HomeViewModel
import io.github.yasarmahomedabbas.immichtv.ui.main.MainScreen
import io.github.yasarmahomedabbas.immichtv.ui.photos.PhotoGridViewModel
import io.github.yasarmahomedabbas.immichtv.ui.photos.PhotoViewerScreen
import io.github.yasarmahomedabbas.immichtv.ui.photos.PhotosScreen
import io.github.yasarmahomedabbas.immichtv.ui.search.SearchScreen
import io.github.yasarmahomedabbas.immichtv.ui.search.SearchViewModel
import io.github.yasarmahomedabbas.immichtv.ui.search.rememberVoiceInput
import io.github.yasarmahomedabbas.immichtv.ui.settings.ScreensaverPickerScreen
import io.github.yasarmahomedabbas.immichtv.ui.settings.SettingsScreen
import io.github.yasarmahomedabbas.immichtv.ui.settings.SettingsViewModel
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
                    is AppScreen.Settings -> SettingsScreen(
                        viewModel = viewModel(key = "settings-$connection", factory = SettingsViewModel.factory(container)),
                        // The gear is only on the main screens.
                        back = ((current.from as? AppScreen.Main)?.tab ?: Tab.Home).label,
                        focusScreensaver = current.focusScreensaver,
                        onOpenServer = app::openServerSetup,
                        onOpenScreensaver = app::openScreensaverPicker,
                        onBack = { app.back() },
                    )
                    is AppScreen.ScreensaverPicker -> ScreensaverPickerScreen(
                        // The same ViewModel as Settings, so a choice shows there at once.
                        viewModel = viewModel(key = "settings-$connection", factory = SettingsViewModel.factory(container)),
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
                            photos = { mayTakeFocus ->
                                // The viewer uses the same ViewModel, so the grid knows where it got to.
                                PhotosScreen(
                                    viewModel(key = "photos-$connection", factory = PhotoGridViewModel.factory(container.libraryTimeline())),
                                    onOpenPhoto = { app.openPhotoViewer() },
                                    onStartSlideshow = app::openTimelineSlideshow,
                                    mayTakeFirstFocus = mayTakeFocus,
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
                        viewModel = albumGrid(container, connection, current.album),
                        onBack = { app.back() },
                        returning = current.returning,
                        onOpenPhoto = { app.openAlbumPhotoViewer(current.album, current.cameFrom) },
                        onStartSlideshow = { app.openAlbumPhotoSlideshow(current.album, it, current.cameFrom) },
                    )
                    is AppScreen.MemoryPhotos -> AlbumPhotosScreen(
                        albumName = current.title,
                        viewModel = memoryGrid(connection, current.memory),
                        onBack = { app.back() },
                        returning = current.returning,
                        onOpenPhoto = { app.openMemoryPhotoViewer(current.memory, current.title) },
                        onStartSlideshow = { app.openMemoryPhotoSlideshow(current.memory, current.title, it) },
                    )
                    is AppScreen.AlbumPhotoViewer -> PhotoViewerScreen(
                        viewModel = albumGrid(container, connection, current.album),
                        onBack = { app.back() },
                    )
                    is AppScreen.MemoryPhotoViewer -> PhotoViewerScreen(
                        viewModel = memoryGrid(connection, current.memory),
                        onBack = { app.back() },
                    )
                    is AppScreen.PhotoViewer -> PhotoViewerScreen(
                        viewModel = viewModel(key = "photos-$connection", factory = PhotoGridViewModel.factory(container.libraryTimeline())),
                        onBack = { app.back() },
                    )
                    is AppScreen.Slideshow -> SlideshowScreen(
                        // In the show store, emptied on leaving: a show starts fresh and its slides don't stay.
                        viewModel = viewModel(
                            viewModelStoreOwner = app.shows,
                            factory = SlideshowViewModel.factory(container, current.album, current.settings),
                        ),
                        settings = current.settings,
                        newPlayer = container::videoPlayer,
                        onBack = { app.back() },
                        source = if (current.cameFrom == CameFrom.HomeHero) SlideshowSource.Home else SlideshowSource.Album,
                    )
                    is AppScreen.AlbumPhotoSlideshow -> SlideshowScreen(
                        viewModel = viewModel(
                            key = "slideshow-$connection-${System.identityHashCode(current)}",
                            factory = SlideshowViewModel.fromAlbumPhoto(container, current.album, current.from, current.settings),
                        ),
                        settings = current.settings,
                        newPlayer = container::videoPlayer,
                        onBack = { app.back() },
                        source = SlideshowSource.Album,
                    )
                    is AppScreen.MemoryPhotoSlideshow -> SlideshowScreen(
                        viewModel = viewModel(
                            key = "slideshow-$connection-${System.identityHashCode(current)}",
                            factory = SlideshowViewModel.fromMemoryPhoto(current.memory, current.from),
                        ),
                        settings = TimelineSettings,
                        newPlayer = container::videoPlayer,
                        onBack = { app.back() },
                        source = SlideshowSource.Memory,
                    )
                    is AppScreen.MemorySlideshow -> SlideshowScreen(
                        viewModel = viewModel(
                            viewModelStoreOwner = app.shows,
                            factory = SlideshowViewModel.fromMemory(current.memory),
                        ),
                        settings = TimelineSettings,
                        newPlayer = container::videoPlayer,
                        onBack = { app.back() },
                        source = SlideshowSource.Home,
                    )
                    is AppScreen.ResultsSlideshow -> SlideshowScreen(
                        viewModel = viewModel(
                            viewModelStoreOwner = app.shows,
                            factory = SlideshowViewModel.factory(current.slides),
                        ),
                        settings = SlideshowSettings(),
                        newPlayer = container::videoPlayer,
                        onBack = { app.back() },
                        source = SlideshowSource.Search,
                    )
                    is AppScreen.TimelineSlideshow -> SlideshowScreen(
                        viewModel = viewModel(
                            viewModelStoreOwner = app.shows,
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

/** An album's grid; its viewer takes the same one, so the grid knows where it got to. */
@Composable
private fun albumGrid(container: AppContainer, connection: Int, album: Album): PhotoGridViewModel = viewModel(
    // The timeline keeps the order it was created with, so a new order needs a new grid.
    key = "album-photos-$connection-${album.id.value}-${album.newestFirst}",
    factory = PhotoGridViewModel.factory(container.albumTimeline(album)),
)

/** A memory's grid; its viewer takes the same one, so the grid knows where it got to. */
@Composable
private fun memoryGrid(connection: Int, memory: Memory): PhotoGridViewModel = viewModel(
    key = "memory-photos-$connection-${memory.hashCode()}",
    factory = PhotoGridViewModel.factory(memory.timeline()),
)
