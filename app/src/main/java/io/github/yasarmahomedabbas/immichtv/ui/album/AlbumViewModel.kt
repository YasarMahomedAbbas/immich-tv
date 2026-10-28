package io.github.yasarmahomedabbas.immichtv.ui.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.yasarmahomedabbas.immichtv.AppContainer
import io.github.yasarmahomedabbas.immichtv.domain.Problem
import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumId
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumPage
import io.github.yasarmahomedabbas.immichtv.domain.albums.LoadAlbum
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.GetSlideshowSettings
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.SaveSlideshowSettings
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.SlideshowSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed interface AlbumLoad {
    data object Loading : AlbumLoad
    data class Loaded(val page: AlbumPage) : AlbumLoad
    data class Failed(val problem: Problem) : AlbumLoad
}

/** The album screen. [settings] come from the device, so they show even when the server fails. */
data class AlbumState(val load: AlbumLoad = AlbumLoad.Loading, val settings: SlideshowSettings = SlideshowSettings())

class AlbumViewModel(
    private val id: AlbumId,
    private val loadAlbum: LoadAlbum,
    private val getSettings: GetSlideshowSettings,
    private val saveSettings: SaveSlideshowSettings,
) : ViewModel() {
    private val _state = MutableStateFlow(AlbumState())
    val state: StateFlow<AlbumState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = getSettings(id)
            _state.update { it.copy(settings = settings) }
        }
        load()
    }

    fun cycleSeconds() = change { it.nextSeconds() }

    fun cycleOrder() = change { it.nextOrder() }

    fun cycleVideos() = change { it.nextVideos() }

    fun toggleDateAndPlace() = change { it.toggleDateAndPlace() }

    fun retry() {
        if (_state.value.load == AlbumLoad.Loading) return
        load()
    }

    private fun change(step: (SlideshowSettings) -> SlideshowSettings) {
        val next = step(_state.value.settings)
        _state.update { it.copy(settings = next) }
        viewModelScope.launch { saveSettings(id, next) }
    }

    private fun load() {
        _state.update { it.copy(load = AlbumLoad.Loading) }
        viewModelScope.launch {
            val load = when (val result = loadAlbum(id)) {
                is ServerResult.Ok -> AlbumLoad.Loaded(result.value)
                is ServerResult.Failed -> AlbumLoad.Failed(result.problem)
            }
            _state.update { it.copy(load = load) }
        }
    }

    companion object {
        fun factory(container: AppContainer, id: AlbumId) = viewModelFactory {
            initializer {
                AlbumViewModel(id, container.loadAlbum, container.getSlideshowSettings, container.saveSlideshowSettings)
            }
        }
    }
}

/**
 * "214 photos · 12 videos · June – August 2025": the parts that are zero or unknown are left
 * out; the dates are months in [zone], with the year once when both ends share it.
 */
internal fun albumSummary(
    photos: Int,
    videos: Int,
    start: Instant?,
    end: Instant?,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String {
    val number = NumberFormat.getIntegerInstance(locale)
    val parts = buildList {
        if (photos > 0) add("${number.format(photos)} ${if (photos == 1) "photo" else "photos"}")
        if (videos > 0) add("${number.format(videos)} ${if (videos == 1) "video" else "videos"}")
        dateRange(start, end, zone, locale)?.let(::add)
    }
    return parts.joinToString(" · ")
}

private fun dateRange(start: Instant?, end: Instant?, zone: ZoneId, locale: Locale): String? {
    val a = (start ?: end)?.let { YearMonth.from(it.atZone(zone)) } ?: return null
    val b = (end ?: start)?.let { YearMonth.from(it.atZone(zone)) } ?: return null
    val first = minOf(a, b)
    val last = maxOf(a, b)
    val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
    val month = DateTimeFormatter.ofPattern("MMMM", locale)
    return when {
        first == last -> first.format(monthYear)
        first.year == last.year -> "${first.format(month)} – ${last.format(monthYear)}"
        else -> "${first.format(monthYear)} – ${last.format(monthYear)}"
    }
}
