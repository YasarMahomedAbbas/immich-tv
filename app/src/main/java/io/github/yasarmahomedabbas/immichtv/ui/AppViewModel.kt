package io.github.yasarmahomedabbas.immichtv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.yasarmahomedabbas.immichtv.AppContainer
import io.github.yasarmahomedabbas.immichtv.domain.albums.Album as DomainAlbum
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumId
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.domain.server.GetSession
import io.github.yasarmahomedabbas.immichtv.domain.server.Session
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.Slide
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.SlideshowSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

    /** The top menu with [tab]'s content under it. [focusResults]: coming Back from a slideshow of its results. */
    data class Main(val tab: Tab, val focusResults: Boolean = false) : AppScreen

    /** [focusBrowse]: coming Back from its photos, so focus goes to Browse photos. */
    data class Album(val id: AlbumId, val focusBrowse: Boolean = false) : AppScreen

    /** Browse photos: [album]'s photos by month. */
    data class AlbumPhotos(val album: DomainAlbum) : AppScreen

    /** [album] playing with [settings], as the album screen had them when it started. */
    data class Slideshow(val album: DomainAlbum, val settings: SlideshowSettings) : AppScreen

    /** [slides], search results, playing at the default timing. */
    data class ResultsSlideshow(val slides: List<Slide>) : AppScreen

    /** One photo of the Photos tab full-screen; which one is the grid's to say. */
    data object PhotoViewer : AppScreen

    /** The whole library playing, from the photo [from] onward. */
    data class TimelineSlideshow(val from: PhotoId) : AppScreen
}

/** Chooses Setup or Home at launch and moves between screens; Back walks up toward Home. */
class AppViewModel(private val getSession: GetSession) : ViewModel() {
    private val _screen = MutableStateFlow<AppScreen>(AppScreen.Loading)
    val screen: StateFlow<AppScreen> = _screen.asStateFlow()

    private val _connection = MutableStateFlow(0)

    /**
     * Goes up each time Setup connects. Screens' ViewModels are kept for the whole activity, so
     * they are keyed by this: what they loaded from one server or key is gone after a change.
     * Shows are the exception: they live in [shows], and don't need this key.
     */
    val connection: StateFlow<Int> = _connection.asStateFlow()

    private val showStore = ViewModelStore()

    /**
     * Where a slideshow's ViewModel lives. What is here goes when the screen changes, so a show's
     * slides don't stay and the next one starts fresh, and survives the activity being recreated.
     */
    val shows: ViewModelStoreOwner = object : ViewModelStoreOwner {
        override val viewModelStore get() = showStore
    }

    init {
        viewModelScope.launch {
            _screen.value = if (getSession() == null) AppScreen.Setup(firstRun = true, current = null) else HOME
        }
    }

    fun selectTab(tab: Tab) {
        moveTo(AppScreen.Main(tab))
    }

    fun openAlbum(id: AlbumId) {
        moveTo(AppScreen.Album(id))
    }

    fun openAlbumPhotos(album: DomainAlbum) {
        moveTo(AppScreen.AlbumPhotos(album))
    }

    fun openSlideshow(album: DomainAlbum, settings: SlideshowSettings) {
        moveTo(AppScreen.Slideshow(album, settings))
    }

    fun openResultsSlideshow(slides: List<Slide>) {
        moveTo(AppScreen.ResultsSlideshow(slides))
    }

    fun openPhotoViewer() {
        moveTo(AppScreen.PhotoViewer)
    }

    fun openTimelineSlideshow(from: PhotoId) {
        moveTo(AppScreen.TimelineSlideshow(from))
    }

    fun openSettings() {
        val from = _screen.value
        viewModelScope.launch {
            moveTo(AppScreen.Setup(firstRun = false, current = getSession(), from = from))
        }
    }

    fun connected() {
        _connection.update { it + 1 }
        moveTo(HOME)
    }

    /** True when Back was handled here; false when the system should finish the app. */
    fun back(): Boolean {
        val next = when (val current = _screen.value) {
            is AppScreen.Setup -> if (current.firstRun) null else current.from
            is AppScreen.Album -> AppScreen.Main(Tab.Albums)
            is AppScreen.AlbumPhotos -> AppScreen.Album(current.album.id, focusBrowse = true)
            is AppScreen.Slideshow -> AppScreen.Album(current.album.id)
            is AppScreen.ResultsSlideshow -> AppScreen.Main(Tab.Search, focusResults = true)
            AppScreen.PhotoViewer, is AppScreen.TimelineSlideshow -> AppScreen.Main(Tab.Photos)
            is AppScreen.Main -> if (current.tab == Tab.Home) null else HOME
            AppScreen.Loading -> null
        } ?: return false
        moveTo(next)
        return true
    }

    override fun onCleared() {
        showStore.clear()
    }

    private fun moveTo(next: AppScreen) {
        showStore.clear()
        _screen.value = next
    }

    companion object {
        private val HOME = AppScreen.Main(Tab.Home)

        fun factory(container: AppContainer) = viewModelFactory {
            initializer { AppViewModel(container.getSession) }
        }
    }
}
