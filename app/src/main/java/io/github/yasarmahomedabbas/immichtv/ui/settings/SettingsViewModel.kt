package io.github.yasarmahomedabbas.immichtv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.yasarmahomedabbas.immichtv.AppContainer
import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import io.github.yasarmahomedabbas.immichtv.domain.albums.Album
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumFilter
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumSort
import io.github.yasarmahomedabbas.immichtv.domain.albums.LoadAlbums
import io.github.yasarmahomedabbas.immichtv.domain.albums.filterAndSort
import io.github.yasarmahomedabbas.immichtv.domain.server.GetSession
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.GetScreensaverSource
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.SaveScreensaverSource
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.ScreensaverSource
import io.github.yasarmahomedabbas.immichtv.ui.albums.AlbumsLoad
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the Screensaver setting says it plays. */
sealed interface SourceValue {
    data object Random : SourceValue
    data class Named(val name: String) : SourceValue

    /** An album, but the albums aren't there to name it: still loading, or they failed. */
    data object UnnamedAlbum : SourceValue

    /** The albums are loaded and the stored one isn't among them. */
    data object AlbumGone : SourceValue
}

/**
 * The Settings screen and the screensaver's picker.
 *
 * @property address the server's, or null with no session.
 */
data class SettingsState(
    val source: ScreensaverSource = ScreensaverSource.Library,
    val address: String? = null,
    val albums: AlbumsLoad = AlbumsLoad.Loading,
) {
    /** The picker's albums, by name; empty until loaded. */
    val choices: List<Album>
        get() = (albums as? AlbumsLoad.Loaded)?.all?.filterAndSort(AlbumFilter.All, AlbumSort.Name).orEmpty()

    val sourceValue: SourceValue
        get() {
            val album = source as? ScreensaverSource.Album ?: return SourceValue.Random
            if (albums !is AlbumsLoad.Loaded) return SourceValue.UnnamedAlbum
            return choices.firstOrNull { it.id == album.id }?.let { SourceValue.Named(it.name) } ?: SourceValue.AlbumGone
        }

    /** Where focus enters the picker's rows, the random row being 0: the selected one, or the first if it's gone. */
    val selectedRow: Int
        get() {
            val album = source as? ScreensaverSource.Album ?: return 0
            return choices.indexOfFirst { it.id == album.id } + 1
        }

    /** The row to put focus on; null while the failed card holds it with its Try again. */
    val enterRow: Int?
        get() = if (albums is AlbumsLoad.Failed) null else selectedRow
}

class SettingsViewModel(
    private val getSource: GetScreensaverSource,
    private val saveSource: SaveScreensaverSource,
    private val getSession: GetSession,
    private val loadAlbums: LoadAlbums,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()
    private var loading: Job? = null

    init {
        load()
    }

    /**
     * Reads it all again. A list already showing stays while that happens, and stays if the read
     * fails, so focus in the picker isn't lost.
     */
    fun load() {
        if (loading?.isActive == true) return
        loading = viewModelScope.launch {
            val source = getSource()
            val address = getSession()?.server?.url
            _state.update {
                it.copy(source = source, address = address, albums = it.albums as? AlbumsLoad.Loaded ?: AlbumsLoad.Loading)
            }
            when (val result = loadAlbums()) {
                is ServerResult.Ok -> _state.update { it.copy(albums = AlbumsLoad.Loaded(result.value)) }
                is ServerResult.Failed -> _state.update {
                    if (it.albums is AlbumsLoad.Loaded) it else it.copy(albums = AlbumsLoad.Failed(result.problem))
                }
            }
        }
    }

    fun retry() = load()

    /** Shows at once, so going Back straight after it says the new choice. */
    fun choose(source: ScreensaverSource) {
        _state.update { it.copy(source = source) }
        viewModelScope.launch { saveSource(source) }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                SettingsViewModel(container.getScreensaverSource, container.saveScreensaverSource, container.getSession, container.loadAlbums)
            }
        }
    }
}
