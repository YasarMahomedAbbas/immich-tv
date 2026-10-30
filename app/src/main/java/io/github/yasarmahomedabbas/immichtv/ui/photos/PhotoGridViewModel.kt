package io.github.yasarmahomedabbas.immichtv.ui.photos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.yasarmahomedabbas.immichtv.domain.Problem
import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.domain.photos.Timeline
import io.github.yasarmahomedabbas.immichtv.domain.photos.TimelineMonth
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth

/** The months themselves: still coming, there, none at all, or failed. */
sealed interface GridLoad {
    data object Loading : GridLoad
    data object Loaded : GridLoad
    data object Empty : GridLoad
    data class Failed(val problem: Problem) : GridLoad
}

/** One month's photos, loaded when the grid reaches it. */
sealed interface MonthPhotos {
    data object NotLoaded : MonthPhotos
    data object Loading : MonthPhotos
    data class Loaded(val photos: List<Photo>) : MonthPhotos
    data class Failed(val problem: Problem) : MonthPhotos
}

data class MonthSection(val month: YearMonth, val count: Int, val photos: MonthPhotos = MonthPhotos.NotLoaded)

/** The month's tiles in order; null is a tile whose photo hasn't arrived. */
internal fun MonthSection.tiles(): List<Photo?> = when (photos) {
    is MonthPhotos.Loaded -> photos.photos
    else -> List(count) { null }
}

/**
 * @property focusedMonth the month the remote was last on in the grid, if it has been there.
 * @property viewing the photo open full-screen, or last open: where the grid goes back to.
 */
data class PhotoGridState(
    val load: GridLoad = GridLoad.Loading,
    val sections: List<MonthSection> = emptyList(),
    val focusedMonth: YearMonth? = null,
    val viewing: Photo? = null,
) {
    /**
     * The month the rail shows as selected. The focused month rather than the first visible item,
     * which lags behind a focus moving down the screen.
     */
    val selectedMonth: YearMonth?
        get() = focusedMonth?.takeIf { month -> sections.any { it.month == month } } ?: sections.firstOrNull()?.month
}

/** What moving through the timeline from the viewed photo meets. */
private sealed interface ViewerStep {
    data class Arrive(val photo: Photo) : ViewerStep
    data class Wait(val month: YearMonth) : ViewerStep
    data class TryAgain(val month: YearMonth) : ViewerStep
    data object End : ViewerStep
}

/**
 * One step ([by] is 1 or -1) from the viewed photo: its neighbour in the month, or past the month's
 * end, the nearest photo of the next month with any in that direction.
 */
private fun PhotoGridState.step(by: Int): ViewerStep {
    val id = viewing?.id ?: return ViewerStep.End
    val at = sections.indexOfFirst { section -> section.loaded().any { it.id == id } }
    if (at < 0) {
        // Opened from outside the grid (Home's Recently added), so its month may not be here yet.
        val missing = sections.firstOrNull { it.photos !is MonthPhotos.Loaded } ?: return ViewerStep.End
        return if (missing.photos is MonthPhotos.Failed) ViewerStep.TryAgain(missing.month) else ViewerStep.Wait(missing.month)
    }
    val photos = sections[at].loaded()
    photos.getOrNull(photos.indexOfFirst { it.id == id } + by)?.let { return ViewerStep.Arrive(it) }
    var next = at + by
    while (next in sections.indices) {
        val section = sections[next]
        when (val month = section.photos) {
            is MonthPhotos.Loaded -> if (month.photos.isNotEmpty()) {
                return ViewerStep.Arrive(if (by > 0) month.photos.first() else month.photos.last())
            }
            is MonthPhotos.Failed -> return ViewerStep.TryAgain(section.month)
            MonthPhotos.NotLoaded, MonthPhotos.Loading -> return ViewerStep.Wait(section.month)
        }
        next += by
    }
    return ViewerStep.End
}

private fun MonthSection.loaded(): List<Photo> = (photos as? MonthPhotos.Loaded)?.photos.orEmpty()

/** The photo one step ([by] is 1 or -1) from the viewed one, if it is already here. */
internal fun PhotoGridState.neighbour(by: Int): Photo? = (step(by) as? ViewerStep.Arrive)?.photo

/** How many grid items the section takes after its heading: its tiles, or one row when it failed. */
private fun MonthSection.itemsAfterHeading(): Int = if (photos is MonthPhotos.Failed) 1 else tiles().size

/** Where [month]'s heading is in the lazy grid, or null if it isn't in [sections]. */
internal fun gridIndexOfMonth(sections: List<MonthSection>, month: YearMonth): Int? {
    var index = 0
    for (section in sections) {
        if (section.month == month) return index
        index += 1 + section.itemsAfterHeading()
    }
    return null
}

/** Where [id]'s tile is in the lazy grid, or null if it isn't in a loaded month. */
internal fun gridIndexOfPhoto(sections: List<MonthSection>, id: PhotoId): Int? {
    var index = 0
    for (section in sections) {
        val at = section.tiles().indexOfFirst { it?.id == id }
        if (at >= 0 && section.photos !is MonthPhotos.Failed) return index + 1 + at
        index += 1 + section.itemsAfterHeading()
    }
    return null
}

/**
 * The sections for [new], in its order: a month already known keeps its photos, with the new count
 * if that changed; a month not seen isn't loaded; a month no longer there is dropped.
 */
internal fun mergeMonths(old: List<MonthSection>, new: List<TimelineMonth>): List<MonthSection> {
    val known = old.associateBy { it.month }
    return new.map { month -> known[month.month]?.copy(count = month.count) ?: MonthSection(month.month, month.count) }
}

/**
 * A grid of any [Timeline]'s photos, a month at a time: the months, then the first month's
 * photos, then each later month when the grid asks for it. Album photos give it one album; the
 * Photos tab gives it the whole library.
 */
class PhotoGridViewModel(private val timeline: Timeline) : ViewModel() {
    private val _state = MutableStateFlow(PhotoGridState())
    val state: StateFlow<PhotoGridState> = _state.asStateFlow()
    private var refreshJob: Job? = null

    /** The latest press that reached a month still on its way, and which month. */
    private var pendingMove: Pair<YearMonth, Int>? = null

    init {
        loadMonths()
    }

    /** The remote is on a tile of [month]: the rail follows it. */
    fun monthFocused(month: YearMonth) = _state.update { it.copy(focusedMonth = month) }

    /** Opens [photo] full-screen; it is also where the grid goes back to. */
    fun open(photo: Photo) {
        pendingMove = null
        _state.update { it.copy(viewing = photo) }
    }

    /** The viewer has gone: a press still waiting for a month is dropped, so the grid's place stays put. */
    fun viewerClosed() {
        pendingMove = null
    }

    /**
     * Moves the open photo through the timeline ([by] is 1 or -1), with no wrap at either end. A month
     * not here yet is fetched, and the move finishes when it arrives; a later press replaces it. A
     * month that failed is tried again, staying put, so a later press can move into it.
     */
    fun viewerMove(by: Int) {
        pendingMove = null
        when (val step = _state.value.step(by)) {
            is ViewerStep.Arrive -> _state.update { it.copy(viewing = step.photo) }
            is ViewerStep.Wait -> {
                pendingMove = step.month to by
                loadMonth(step.month)
            }
            is ViewerStep.TryAgain -> retryMonth(step.month)
            ViewerStep.End -> Unit
        }
    }

    /** Loads [month]'s photos unless they are loaded or on their way. */
    fun loadMonth(month: YearMonth) {
        val section = _state.value.sections.firstOrNull { it.month == month } ?: return
        if (section.photos != MonthPhotos.NotLoaded) return
        fetch(month)
    }

    /** Tries a month that failed again. */
    fun retryMonth(month: YearMonth) {
        val section = _state.value.sections.firstOrNull { it.month == month } ?: return
        if (section.photos is MonthPhotos.Failed) fetch(month)
    }

    /** Tries the months again after they failed. */
    fun retry() {
        if (_state.value.load !is GridLoad.Failed) return
        refreshJob?.cancel()
        loadMonths()
    }

    /**
     * Reads the months again without the skeleton, and in place: only a loaded month whose count
     * changed reads its photos again, keeping the old ones until they come. A failure leaves the
     * grid as it was; the next refresh tries again.
     */
    fun refresh() {
        if (_state.value.load == GridLoad.Loading || refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            val months = when (val result = timeline.months()) {
                is ServerResult.Failed -> return@launch
                is ServerResult.Ok -> result.value
            }
            val old = _state.value.sections
            val sections = mergeMonths(old, months)
            _state.update { it.copy(load = if (sections.isEmpty()) GridLoad.Empty else GridLoad.Loaded, sections = sections) }
            // A grid that was empty or failed has nothing loaded yet, and nothing asks for its first month.
            sections.firstOrNull()?.takeIf { it.photos == MonthPhotos.NotLoaded }?.let { fetch(it.month) }
            val counts = old.filter { it.photos is MonthPhotos.Loaded }.associate { it.month to it.count }
            for (section in sections) {
                val shownCount = counts[section.month]
                if (shownCount != null && shownCount != section.count) reread(section.month, shownCount)
            }
        }
    }

    /** On failure the month goes back to [shownCount], so it still reads as changed next refresh. */
    private suspend fun reread(month: YearMonth, shownCount: Int) {
        when (val result = timeline.photos(month)) {
            is ServerResult.Ok -> setPhotos(month, MonthPhotos.Loaded(result.value))
            is ServerResult.Failed -> _state.update { state ->
                state.copy(sections = state.sections.map { if (it.month == month) it.copy(count = shownCount) else it })
            }
        }
    }

    private fun loadMonths() {
        _state.value = PhotoGridState(GridLoad.Loading)
        viewModelScope.launch {
            when (val result = timeline.months()) {
                is ServerResult.Failed -> _state.value = PhotoGridState(GridLoad.Failed(result.problem))
                is ServerResult.Ok -> {
                    val sections = result.value.map { MonthSection(it.month, it.count) }
                    _state.value = PhotoGridState(if (sections.isEmpty()) GridLoad.Empty else GridLoad.Loaded, sections)
                    sections.firstOrNull()?.let { fetch(it.month) }
                }
            }
        }
    }

    private fun fetch(month: YearMonth) {
        setPhotos(month, MonthPhotos.Loading)
        viewModelScope.launch {
            val photos = when (val result = timeline.photos(month)) {
                is ServerResult.Ok -> MonthPhotos.Loaded(result.value)
                is ServerResult.Failed -> MonthPhotos.Failed(result.problem)
            }
            setPhotos(month, photos)
            val (waitingFor, by) = pendingMove ?: return@launch
            if (waitingFor == month) {
                pendingMove = null
                if (photos is MonthPhotos.Loaded) viewerMove(by)
            }
        }
    }

    private fun setPhotos(month: YearMonth, photos: MonthPhotos) = _state.update { state ->
        state.copy(sections = state.sections.map { if (it.month == month) it.copy(photos = photos) else it })
    }

    companion object {
        fun factory(timeline: Timeline) = viewModelFactory {
            initializer { PhotoGridViewModel(timeline) }
        }
    }
}
