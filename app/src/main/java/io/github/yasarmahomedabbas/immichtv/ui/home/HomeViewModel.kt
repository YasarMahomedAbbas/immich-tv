package io.github.yasarmahomedabbas.immichtv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.yasarmahomedabbas.immichtv.AppContainer
import io.github.yasarmahomedabbas.immichtv.domain.Problem
import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import io.github.yasarmahomedabbas.immichtv.domain.albums.Album
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumId
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.domain.home.HomePage
import io.github.yasarmahomedabbas.immichtv.domain.home.LoadHome
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.GetSlideshowSettings
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.SlideshowSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface HomeLoad {
    data object Loading : HomeLoad
    data class Loaded(val page: HomePage) : HomeLoad
    data class Failed(val problem: Problem) : HomeLoad
}

/** The things on Home that take focus. */
sealed interface HomeFocus {
    data object Play : HomeFocus
    data object ViewPhotos : HomeFocus
    data object SeeAll : HomeFocus
    data class AlbumCard(val id: AlbumId) : HomeFocus
    data class Recent(val id: PhotoId) : HomeFocus
}

/** [focused] is where the remote was last, so coming Back to Home puts focus where it was. */
data class HomeState(val load: HomeLoad = HomeLoad.Loading, val focused: HomeFocus = HomeFocus.Play)

/**
 * Where Up goes when geometry would get it wrong. See all sits at the far right, so going by geometry
 * no card in the row reaches it: a card's Up goes to it instead, and it goes on to the hero, not the
 * gear above it. Null leaves it to geometry. Found on the TV.
 */
internal fun HomePage.upFrom(focus: HomeFocus): HomeFocus? = when (focus) {
    is HomeFocus.AlbumCard -> HomeFocus.SeeAll
    HomeFocus.SeeAll -> if (hero != null) HomeFocus.ViewPhotos else null
    else -> null
}

/** The last item of a row, where Right stops instead of leaking up to the top menu's gear. */
internal fun HomePage.endsRow(focus: HomeFocus): Boolean = when (focus) {
    is HomeFocus.AlbumCard -> albums.lastOrNull()?.id == focus.id
    is HomeFocus.Recent -> recent.lastOrNull()?.id == focus.id
    else -> false
}

/** Where focus goes on this page: [focused] while it is still there, else Play, else the first thing shown; null on an empty page. */
internal fun HomePage.focusTarget(focused: HomeFocus): HomeFocus? {
    val there = when (focused) {
        HomeFocus.Play, HomeFocus.ViewPhotos -> hero != null
        HomeFocus.SeeAll -> albums.isNotEmpty()
        is HomeFocus.AlbumCard -> albums.any { it.id == focused.id }
        is HomeFocus.Recent -> recent.any { it.id == focused.id }
    }
    return when {
        there -> focused
        hero != null -> HomeFocus.Play
        albums.isNotEmpty() -> HomeFocus.AlbumCard(albums.first().id)
        recent.isNotEmpty() -> HomeFocus.Recent(recent.first().id)
        else -> null
    }
}

class HomeViewModel(
    private val loadHome: LoadHome,
    private val getSlideshowSettings: GetSlideshowSettings,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()
    private var refreshJob: Job? = null

    init {
        load()
    }

    fun onFocused(focus: HomeFocus) = _state.update { it.copy(focused = focus) }

    fun retry() {
        if (_state.value.load == HomeLoad.Loading) return
        refreshJob?.cancel()
        load()
    }

    /**
     * Reads the page again without the skeleton, so focus stays where it is. An equal page changes
     * nothing on screen; a failure leaves the screen as it was, and the next refresh tries again.
     */
    fun refresh() {
        if (_state.value.load == HomeLoad.Loading || refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            val result = loadHome()
            if (result is ServerResult.Ok) _state.update { it.copy(load = HomeLoad.Loaded(result.value)) }
        }
    }

    /** The saved settings the hero's album plays with. */
    suspend fun heroAlbumSettings(album: Album): SlideshowSettings = getSlideshowSettings(album.id)

    private fun load() {
        _state.update { it.copy(load = HomeLoad.Loading) }
        viewModelScope.launch {
            val load = when (val result = loadHome()) {
                is ServerResult.Ok -> HomeLoad.Loaded(result.value)
                is ServerResult.Failed -> HomeLoad.Failed(result.problem)
            }
            _state.update { it.copy(load = load) }
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { HomeViewModel(container.loadHome, container.getSlideshowSettings) }
        }
    }
}
