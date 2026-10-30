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
import io.github.yasarmahomedabbas.immichtv.domain.home.Memory
import io.github.yasarmahomedabbas.immichtv.domain.server.GetSession
import io.github.yasarmahomedabbas.immichtv.domain.server.Session
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.GetSlideshowSettings
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.Slide
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.SlideshowSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The top menu's tabs, in the order it shows them. */
enum class Tab { Home, Albums, Photos, Search }

/**
 * Where an album's screens were opened from, which decides where Back goes: the Albums tab; a card
 * in Home's row, so the album screen goes back to Home; or Home's hero, which skips the album screen.
 */
enum class CameFrom { AlbumsTab, HomeRow, HomeHero }

/** Which screen the app shows. */
sealed interface AppScreen {
    data object Loading : AppScreen

    /**
     * [firstRun] has nowhere to go back to; otherwise it was opened from Settings with
     * [current], and Back returns to [from].
     */
    data class Setup(val firstRun: Boolean, val current: Session?, val from: AppScreen = Main(Tab.Home)) : AppScreen

    /** The gear's screen; Back returns to [from]. [focusScreensaver]: coming Back from the picker. */
    data class Settings(val from: AppScreen, val focusScreensaver: Boolean = false) : AppScreen

    /** What the screensaver plays; Back returns to [settings]. */
    data class ScreensaverPicker(val settings: Settings) : AppScreen

    /** The top menu with [tab]'s content under it. [focusResults]: coming Back from a slideshow of its results. */
    data class Main(val tab: Tab, val focusResults: Boolean = false) : AppScreen

    /** [focusBrowse]: coming Back from its photos, so focus goes to Browse photos. */
    data class Album(val id: AlbumId, val focusBrowse: Boolean = false, val cameFrom: CameFrom = CameFrom.AlbumsTab) : AppScreen

    /**
     * Browse photos: [album]'s photos by month. [returning]: coming Back from a photo or a show,
     * so the grid goes to that tile; otherwise it starts at the top.
     */
    data class AlbumPhotos(
        val album: DomainAlbum,
        val cameFrom: CameFrom = CameFrom.AlbumsTab,
        val returning: Boolean = false,
    ) : AppScreen

    /** One photo of [album] full-screen; which one is its grid's to say. */
    data class AlbumPhotoViewer(val album: DomainAlbum, val cameFrom: CameFrom) : AppScreen

    /** [album] playing from the photo [from] onward, in its grid's order, with [settings]. */
    data class AlbumPhotoSlideshow(
        val album: DomainAlbum,
        val from: PhotoId,
        val settings: SlideshowSettings,
        val cameFrom: CameFrom,
    ) : AppScreen

    /** [album] playing with [settings], as the album screen had them when it started. */
    data class Slideshow(
        val album: DomainAlbum,
        val settings: SlideshowSettings,
        val cameFrom: CameFrom = CameFrom.AlbumsTab,
    ) : AppScreen

    /** Home's memory playing. */
    data class MemorySlideshow(val memory: Memory) : AppScreen

    /** Home's memory as a grid under [title]. [returning]: as for [AlbumPhotos]. */
    data class MemoryPhotos(val memory: Memory, val title: String, val returning: Boolean = false) : AppScreen

    /** One photo of [memory] full-screen; which one is its grid's to say. */
    data class MemoryPhotoViewer(val memory: Memory, val title: String) : AppScreen

    /** [memory] playing from the photo [from] onward. */
    data class MemoryPhotoSlideshow(val memory: Memory, val title: String, val from: PhotoId) : AppScreen

    /** [slides], search results, playing at the default timing. */
    data class ResultsSlideshow(val slides: List<Slide>) : AppScreen

    /** One photo of the timeline full-screen; which one is the grid's to say. [fromHome]: Back goes to Home, not the Photos tab. */
    data class PhotoViewer(val fromHome: Boolean = false) : AppScreen

    /** The whole library playing, from the photo [from] onward. */
    data class TimelineSlideshow(val from: PhotoId) : AppScreen
}

/** Chooses Setup or Home at launch and moves between screens; Back walks up toward Home. */
class AppViewModel(
    private val getSession: GetSession,
    private val getSlideshowSettings: GetSlideshowSettings,
) : ViewModel() {
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

    fun openAlbum(id: AlbumId, cameFrom: CameFrom = CameFrom.AlbumsTab) {
        moveTo(AppScreen.Album(id, cameFrom = cameFrom))
    }

    fun openAlbumPhotos(album: DomainAlbum, cameFrom: CameFrom = CameFrom.AlbumsTab) {
        moveTo(AppScreen.AlbumPhotos(album, cameFrom))
    }

    fun openSlideshow(album: DomainAlbum, settings: SlideshowSettings, cameFrom: CameFrom = CameFrom.AlbumsTab) {
        moveTo(AppScreen.Slideshow(album, settings, cameFrom))
    }

    fun openMemorySlideshow(memory: Memory) {
        moveTo(AppScreen.MemorySlideshow(memory))
    }

    fun openMemoryPhotos(memory: Memory, title: String) {
        moveTo(AppScreen.MemoryPhotos(memory, title))
    }

    fun openAlbumPhotoViewer(album: DomainAlbum, cameFrom: CameFrom = CameFrom.AlbumsTab) {
        _screen.value = AppScreen.AlbumPhotoViewer(album, cameFrom)
    }

    /** With the album's saved settings; if Back left the grid while they loaded, it stays left. */
    fun openAlbumPhotoSlideshow(album: DomainAlbum, from: PhotoId, cameFrom: CameFrom = CameFrom.AlbumsTab) {
        val grid = _screen.value
        viewModelScope.launch {
            val settings = getSlideshowSettings(album.id)
            if (_screen.value === grid) _screen.value = AppScreen.AlbumPhotoSlideshow(album, from, settings, cameFrom)
        }
    }

    fun openMemoryPhotoViewer(memory: Memory, title: String) {
        _screen.value = AppScreen.MemoryPhotoViewer(memory, title)
    }

    fun openMemoryPhotoSlideshow(memory: Memory, title: String, from: PhotoId) {
        _screen.value = AppScreen.MemoryPhotoSlideshow(memory, title, from)
    }

    fun openResultsSlideshow(slides: List<Slide>) {
        moveTo(AppScreen.ResultsSlideshow(slides))
    }

    fun openPhotoViewer(fromHome: Boolean = false) {
        moveTo(AppScreen.PhotoViewer(fromHome))
    }

    fun openTimelineSlideshow(from: PhotoId) {
        moveTo(AppScreen.TimelineSlideshow(from))
    }

    fun openSettings() {
        val from = _screen.value
        if (from !is AppScreen.Settings) _screen.value = AppScreen.Settings(from)
    }

    /** From Settings, whose Server row this is. */
    fun openServerSetup() {
        val from = _screen.value
        viewModelScope.launch {
            moveTo(AppScreen.Setup(firstRun = false, current = getSession(), from = from))
        }
    }

    /** From Settings, whose Screensaver row this is. */
    fun openScreensaverPicker() {
        val settings = _screen.value as? AppScreen.Settings ?: return
        _screen.value = AppScreen.ScreensaverPicker(settings)
    }

    fun connected() {
        _connection.update { it + 1 }
        moveTo(HOME)
    }

    /** True when Back was handled here; false when the system should finish the app. */
    fun back(): Boolean {
        val next = when (val current = _screen.value) {
            is AppScreen.Setup -> if (current.firstRun) null else current.from
            is AppScreen.Settings -> current.from
            is AppScreen.ScreensaverPicker -> current.settings.copy(focusScreensaver = true)
            is AppScreen.Album -> if (current.cameFrom == CameFrom.HomeRow) HOME else AppScreen.Main(Tab.Albums)
            is AppScreen.AlbumPhotos -> if (current.cameFrom == CameFrom.HomeHero) {
                HOME
            } else {
                AppScreen.Album(current.album.id, focusBrowse = true, cameFrom = current.cameFrom)
            }
            is AppScreen.AlbumPhotoViewer -> AppScreen.AlbumPhotos(current.album, current.cameFrom, returning = true)
            is AppScreen.AlbumPhotoSlideshow -> AppScreen.AlbumPhotos(current.album, current.cameFrom, returning = true)
            is AppScreen.MemoryPhotoViewer -> AppScreen.MemoryPhotos(current.memory, current.title, returning = true)
            is AppScreen.MemoryPhotoSlideshow -> AppScreen.MemoryPhotos(current.memory, current.title, returning = true)
            is AppScreen.Slideshow -> if (current.cameFrom == CameFrom.HomeHero) {
                HOME
            } else {
                AppScreen.Album(current.album.id, cameFrom = current.cameFrom)
            }
            is AppScreen.MemorySlideshow, is AppScreen.MemoryPhotos -> HOME
            is AppScreen.ResultsSlideshow -> AppScreen.Main(Tab.Search, focusResults = true)
            is AppScreen.PhotoViewer -> if (current.fromHome) HOME else AppScreen.Main(Tab.Photos)
            is AppScreen.TimelineSlideshow -> AppScreen.Main(Tab.Photos)
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
            initializer { AppViewModel(container.getSession, container.getSlideshowSettings) }
        }
    }
}
