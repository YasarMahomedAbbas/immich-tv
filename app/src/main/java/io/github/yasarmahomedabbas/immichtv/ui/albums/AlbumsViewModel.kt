package io.github.yasarmahomedabbas.immichtv.ui.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.yasarmahomedabbas.immichtv.AppContainer
import io.github.yasarmahomedabbas.immichtv.domain.Problem
import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import io.github.yasarmahomedabbas.immichtv.domain.albums.Album
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumFilter
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumId
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumSort
import io.github.yasarmahomedabbas.immichtv.domain.albums.LoadAlbums
import io.github.yasarmahomedabbas.immichtv.domain.albums.filterAndSort
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AlbumsLoad {
    data object Loading : AlbumsLoad
    data class Loaded(val all: List<Album>) : AlbumsLoad
    data class Failed(val problem: Problem) : AlbumsLoad
}

/**
 * The Albums tab. [focused] is the album the remote was last on, so coming Back from an album
 * puts focus where it was.
 */
data class AlbumsState(
    val load: AlbumsLoad = AlbumsLoad.Loading,
    val filter: AlbumFilter = AlbumFilter.All,
    val sort: AlbumSort = AlbumSort.RecentlyUpdated,
    val focused: AlbumId? = null,
) {
    /** The albums the filter keeps, in the chosen order; empty until loaded. */
    val visible: List<Album>
        get() = (load as? AlbumsLoad.Loaded)?.all?.filterAndSort(filter, sort).orEmpty()
}

class AlbumsViewModel(private val loadAlbums: LoadAlbums) : ViewModel() {
    private val _state = MutableStateFlow(AlbumsState())
    val state: StateFlow<AlbumsState> = _state.asStateFlow()
    private var refreshJob: Job? = null

    init {
        load()
    }

    fun setFilter(filter: AlbumFilter) = _state.update { it.copy(filter = filter) }

    /** Recently updated → A–Z → most photos → recently updated. */
    fun cycleSort() = _state.update {
        val all = AlbumSort.entries
        it.copy(sort = all[(it.sort.ordinal + 1) % all.size])
    }

    fun onFocused(album: AlbumId) = _state.update { it.copy(focused = album) }

    fun retry() {
        if (_state.value.load == AlbumsLoad.Loading) return
        refreshJob?.cancel()
        load()
    }

    /**
     * Reads the albums again without the skeleton, so focus stays where it is. Equal albums change
     * nothing on screen; a failure leaves the screen as it was, and the next refresh tries again.
     */
    fun refresh() {
        if (_state.value.load == AlbumsLoad.Loading || refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            val result = loadAlbums()
            if (result is ServerResult.Ok) _state.update { it.copy(load = AlbumsLoad.Loaded(result.value)) }
        }
    }

    private fun load() {
        _state.update { it.copy(load = AlbumsLoad.Loading) }
        viewModelScope.launch {
            val load = when (val result = loadAlbums()) {
                is ServerResult.Ok -> AlbumsLoad.Loaded(result.value)
                is ServerResult.Failed -> AlbumsLoad.Failed(result.problem)
            }
            _state.update { it.copy(load = load) }
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { AlbumsViewModel(container.loadAlbums) }
        }
    }
}
