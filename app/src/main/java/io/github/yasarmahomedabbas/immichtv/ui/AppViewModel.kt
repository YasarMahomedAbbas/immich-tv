package io.github.yasarmahomedabbas.immichtv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.yasarmahomedabbas.immichtv.AppContainer
import io.github.yasarmahomedabbas.immichtv.domain.albums.Album as DomainAlbum
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumId
import io.github.yasarmahomedabbas.immichtv.domain.server.GetSession
import io.github.yasarmahomedabbas.immichtv.domain.server.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The top menu's tabs, in the order it shows them. */
enum class Tab { Home, Albums, Photos, Search }

/** Which screen the app shows. */
sealed interface AppScreen {
    data object Loading : AppScreen

    /**
     * [firstRun] has nowhere to go back to; otherwise it was opened from Settings with
     * [current], and Back returns to [from].
     */
    data class Setup(val firstRun: Boolean, val current: Session?, val from: AppScreen = Main(Tab.Home)) : AppScreen

    /** The top menu with [tab]'s content under it. */
    data class Main(val tab: Tab) : AppScreen

    /** [focusBrowse]: coming Back from its photos, so focus goes to Browse photos. */
    data class Album(val id: AlbumId, val focusBrowse: Boolean = false) : AppScreen

    /** Browse photos: [album]'s photos by month. */
    data class AlbumPhotos(val album: DomainAlbum) : AppScreen
}

/** Chooses Setup or Home at launch and moves between screens; Back walks up toward Home. */
class AppViewModel(private val getSession: GetSession) : ViewModel() {
    private val _screen = MutableStateFlow<AppScreen>(AppScreen.Loading)
    val screen: StateFlow<AppScreen> = _screen.asStateFlow()

    init {
        viewModelScope.launch {
            _screen.value = if (getSession() == null) AppScreen.Setup(firstRun = true, current = null) else HOME
        }
    }

    fun selectTab(tab: Tab) {
        _screen.value = AppScreen.Main(tab)
    }

    fun openAlbum(id: AlbumId) {
        _screen.value = AppScreen.Album(id)
    }

    fun openAlbumPhotos(album: DomainAlbum) {
        _screen.value = AppScreen.AlbumPhotos(album)
    }

    fun openSettings() {
        val from = _screen.value
        viewModelScope.launch {
            _screen.value = AppScreen.Setup(firstRun = false, current = getSession(), from = from)
        }
    }

    fun connected() {
        _screen.value = HOME
    }

    /** True when Back was handled here; false when the system should finish the app. */
    fun back(): Boolean {
        val next = when (val current = _screen.value) {
            is AppScreen.Setup -> if (current.firstRun) null else current.from
            is AppScreen.Album -> AppScreen.Main(Tab.Albums)
            is AppScreen.AlbumPhotos -> AppScreen.Album(current.album.id, focusBrowse = true)
            is AppScreen.Main -> if (current.tab == Tab.Home) null else HOME
            AppScreen.Loading -> null
        } ?: return false
        _screen.value = next
        return true
    }

    companion object {
        private val HOME = AppScreen.Main(Tab.Home)

        fun factory(container: AppContainer) = viewModelFactory {
            initializer { AppViewModel(container.getSession) }
        }
    }
}
