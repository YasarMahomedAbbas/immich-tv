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
import io.github.yasarmahomedabbas.immichtv.ui.setup.SetupScreen
import io.github.yasarmahomedabbas.immichtv.ui.setup.SetupViewModel
import io.github.yasarmahomedabbas.immichtv.ui.theme.ImmichTvTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as ImmichTvApp).container
        setContent {
            ImmichTvTheme {
                val app: AppViewModel = viewModel(factory = AppViewModel.factory(container))
                val screen by app.screen.collectAsState()
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
                    ) {
                        // Scoped to the activity, so the list and the focused album survive opening one.
                        AlbumsScreen(viewModel(factory = AlbumsViewModel.factory(container)), onOpenAlbum = app::openAlbum)
                    }
                    is AppScreen.Album -> AlbumScreen(
                        viewModel = viewModel(
                            key = "album-${current.id.value}",
                            factory = AlbumViewModel.factory(container, current.id),
                        ),
                        onBack = { app.back() },
                        onBrowsePhotos = app::openAlbumPhotos,
                        focusBrowse = current.focusBrowse,
                    )
                    is AppScreen.AlbumPhotos -> AlbumPhotosScreen(
                        albumName = current.album.name,
                        viewModel = viewModel(
                            key = "album-photos-${current.album.id.value}",
                            factory = PhotoGridViewModel.factory(container.albumTimeline(current.album)),
                        ),
                        onBack = { app.back() },
                    )
                }
            }
        }
    }
}
