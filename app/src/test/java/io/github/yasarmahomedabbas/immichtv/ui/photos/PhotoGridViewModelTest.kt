package io.github.yasarmahomedabbas.immichtv.ui.photos

import io.github.yasarmahomedabbas.immichtv.domain.Problem
import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.domain.photos.FakeTimeline
import io.github.yasarmahomedabbas.immichtv.domain.photos.TimelineMonth
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoGridViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val august = YearMonth.of(2025, 8)
    private val july = YearMonth.of(2025, 7)
    private val augustPhotos = listOf(Photo(PhotoId("p1"), false), Photo(PhotoId("v1"), true))
    private val julyPhotos = listOf(Photo(PhotoId("p2"), false))
    private val timeline = FakeTimeline(
        months = ServerResult.Ok(listOf(TimelineMonth(august, 2), TimelineMonth(july, 1))),
        photos = mutableMapOf(august to ServerResult.Ok(augustPhotos), july to ServerResult.Ok(julyPhotos)),
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun PhotoGridViewModel.section(month: YearMonth) = state.value.sections.first { it.month == month }

    @Test
    fun `the months load, then the first month's photos`() = runTest(dispatcher) {
        val vm = PhotoGridViewModel(timeline)
        assertEquals(PhotoGridState(), vm.state.value)
        advanceUntilIdle()

        assertEquals(GridLoad.Loaded, vm.state.value.load)
        assertEquals(
            listOf(MonthSection(august, 2, MonthPhotos.Loaded(augustPhotos)), MonthSection(july, 1, MonthPhotos.NotLoaded)),
            vm.state.value.sections,
        )
        assertEquals(listOf(august), timeline.asked)
    }

    @Test
    fun `a later month loads when asked, once`() = runTest(dispatcher) {
        val vm = PhotoGridViewModel(timeline)
        advanceUntilIdle()

        vm.loadMonth(july)
        assertEquals(MonthPhotos.Loading, vm.section(july).photos)
        vm.loadMonth(july)
        advanceUntilIdle()
        vm.loadMonth(july)
        vm.loadMonth(august)
        advanceUntilIdle()

        assertEquals(MonthPhotos.Loaded(julyPhotos), vm.section(july).photos)
        assertEquals(listOf(august, july), timeline.asked)
    }

    @Test
    fun `a month that isn't in the timeline is ignored`() = runTest(dispatcher) {
        val vm = PhotoGridViewModel(timeline)
        advanceUntilIdle()
        vm.loadMonth(YearMonth.of(2020, 1))
        vm.retryMonth(YearMonth.of(2020, 1))
        advanceUntilIdle()
        assertEquals(listOf(august), timeline.asked)
    }

    @Test
    fun `a failed month shows, and only try again asks again`() = runTest(dispatcher) {
        timeline.photos[july] = ServerResult.Failed(Problem.Unreachable)
        val vm = PhotoGridViewModel(timeline)
        advanceUntilIdle()
        vm.loadMonth(july)
        advanceUntilIdle()
        assertEquals(MonthPhotos.Failed(Problem.Unreachable), vm.section(july).photos)

        vm.loadMonth(july)
        vm.retryMonth(august)
        advanceUntilIdle()
        assertEquals(listOf(august, july), timeline.asked)

        timeline.photos[july] = ServerResult.Ok(julyPhotos)
        vm.retryMonth(july)
        advanceUntilIdle()
        assertEquals(MonthPhotos.Loaded(julyPhotos), vm.section(july).photos)
    }

    @Test
    fun `no months is empty`() = runTest(dispatcher) {
        timeline.months = ServerResult.Ok(emptyList())
        val vm = PhotoGridViewModel(timeline)
        advanceUntilIdle()
        assertEquals(PhotoGridState(GridLoad.Empty), vm.state.value)
        assertEquals(emptyList<YearMonth>(), timeline.asked)
    }

    @Test
    fun `failed months show, and try again loads them`() = runTest(dispatcher) {
        val months = timeline.months
        timeline.months = ServerResult.Failed(Problem.KeyLacksPermission)
        val vm = PhotoGridViewModel(timeline)
        advanceUntilIdle()
        assertEquals(PhotoGridState(GridLoad.Failed(Problem.KeyLacksPermission)), vm.state.value)

        timeline.months = months
        vm.retry()
        assertEquals(GridLoad.Loading, vm.state.value.load)
        advanceUntilIdle()
        assertEquals(GridLoad.Loaded, vm.state.value.load)
        assertEquals(2, timeline.monthsCalls)
    }

    @Test
    fun `try again when the months loaded does nothing`() = runTest(dispatcher) {
        val vm = PhotoGridViewModel(timeline)
        advanceUntilIdle()
        vm.retry()
        advanceUntilIdle()
        assertEquals(1, timeline.monthsCalls)
    }

    /** Every state the screen sees from here on. */
    private fun TestScope.statesOf(vm: PhotoGridViewModel): List<PhotoGridState> {
        val seen = mutableListOf<PhotoGridState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { seen += it } }
        return seen
    }

    private fun months(vararg months: Pair<YearMonth, Int>) =
        ServerResult.Ok(months.map { (month, count) -> TimelineMonth(month, count) })

    @Test
    fun `a refresh with nothing changed leaves the same state and reads no photos`() = runTest(dispatcher) {
        val vm = PhotoGridViewModel(timeline)
        advanceUntilIdle()
        val before = vm.state.value
        val states = statesOf(vm)

        vm.refresh()
        advanceUntilIdle()

        assertSame(before, vm.state.value)
        assertEquals(1, states.size)
        assertEquals(2, timeline.monthsCalls)
        assertEquals(listOf(august), timeline.asked)
    }

    @Test
    fun `a changed loaded month is read again, keeping its photos meanwhile`() = runTest(dispatcher) {
        val vm = PhotoGridViewModel(timeline)
        advanceUntilIdle()
        vm.loadMonth(july)
        advanceUntilIdle()
        val states = statesOf(vm)

        val more = augustPhotos + Photo(PhotoId("p3"), false)
        timeline.months = months(august to 3, july to 1)
        timeline.photos[august] = ServerResult.Ok(more)
        vm.refresh()
        advanceUntilIdle()

        assertEquals(MonthSection(august, 3, MonthPhotos.Loaded(more)), vm.section(august))
        assertEquals(listOf(august, july, august), timeline.asked)
        assertEquals(MonthSection(august, 3, MonthPhotos.Loaded(augustPhotos)), states[1].sections[0])
        assertFalse(states.any { it.load == GridLoad.Loading || it.sections.any { s -> s.photos == MonthPhotos.Loading } })
    }

    @Test
    fun `a changed month not loaded only changes its count`() = runTest(dispatcher) {
        val vm = PhotoGridViewModel(timeline)
        advanceUntilIdle()

        timeline.months = months(august to 2, july to 5)
        vm.refresh()
        advanceUntilIdle()

        assertEquals(MonthSection(july, 5, MonthPhotos.NotLoaded), vm.section(july))
        assertEquals(listOf(august), timeline.asked)
    }

    @Test
    fun `a new month goes in its place, not loaded`() = runTest(dispatcher) {
        val june = YearMonth.of(2025, 6)
        val vm = PhotoGridViewModel(timeline)
        advanceUntilIdle()

        timeline.months = months(august to 2, YearMonth.of(2025, 7) to 1, june to 4)
        vm.refresh()
        advanceUntilIdle()

        assertEquals(listOf(august, july, june), vm.state.value.sections.map { it.month })
        assertEquals(MonthPhotos.NotLoaded, vm.section(june).photos)
        assertEquals(listOf(august), timeline.asked)
    }

    @Test
    fun `a month no longer there is dropped`() = runTest(dispatcher) {
        val vm = PhotoGridViewModel(timeline)
        advanceUntilIdle()

        timeline.months = months(august to 2)
        vm.refresh()
        advanceUntilIdle()

        assertEquals(listOf(august), vm.state.value.sections.map { it.month })
    }

    @Test
    fun `every month gone is empty`() = runTest(dispatcher) {
        val vm = PhotoGridViewModel(timeline)
        advanceUntilIdle()

        timeline.months = months()
        vm.refresh()
        advanceUntilIdle()

        assertEquals(PhotoGridState(GridLoad.Empty), vm.state.value)
    }

    @Test
    fun `a failed read again leaves the month as it was, and the next refresh tries again`() = runTest(dispatcher) {
        val vm = PhotoGridViewModel(timeline)
        advanceUntilIdle()

        timeline.months = months(august to 3, july to 1)
        timeline.photos[august] = ServerResult.Failed(Problem.Unreachable)
        vm.refresh()
        advanceUntilIdle()

        assertEquals(MonthSection(august, 2, MonthPhotos.Loaded(augustPhotos)), vm.section(august))

        val more = augustPhotos + Photo(PhotoId("p3"), false)
        timeline.photos[august] = ServerResult.Ok(more)
        vm.refresh()
        advanceUntilIdle()

        assertEquals(MonthSection(august, 3, MonthPhotos.Loaded(more)), vm.section(august))
    }

    @Test
    fun `failed months keep everything`() = runTest(dispatcher) {
        val vm = PhotoGridViewModel(timeline)
        advanceUntilIdle()
        val before = vm.state.value

        timeline.months = ServerResult.Failed(Problem.Unreachable)
        vm.refresh()
        advanceUntilIdle()

        assertSame(before, vm.state.value)
    }

    @Test
    fun `a failed grid recovers and fetches the first month, without loading`() = runTest(dispatcher) {
        val months = timeline.months
        timeline.months = ServerResult.Failed(Problem.Unreachable)
        val vm = PhotoGridViewModel(timeline)
        advanceUntilIdle()
        val states = statesOf(vm)

        timeline.months = months
        vm.refresh()
        advanceUntilIdle()

        assertEquals(GridLoad.Loaded, vm.state.value.load)
        assertEquals(MonthPhotos.Loaded(augustPhotos), vm.section(august).photos)
        assertEquals(listOf(august), timeline.asked)
        assertFalse(states.any { it.load == GridLoad.Loading })
    }

    @Test
    fun `an empty grid recovers and fetches the first month`() = runTest(dispatcher) {
        val months = timeline.months
        timeline.months = ServerResult.Ok(emptyList())
        val vm = PhotoGridViewModel(timeline)
        advanceUntilIdle()

        timeline.months = months
        vm.refresh()
        advanceUntilIdle()

        assertEquals(GridLoad.Loaded, vm.state.value.load)
        assertEquals(MonthPhotos.Loaded(augustPhotos), vm.section(august).photos)
    }

    @Test
    fun `a refresh during the first load is ignored`() = runTest(dispatcher) {
        val vm = PhotoGridViewModel(timeline)
        vm.refresh()
        advanceUntilIdle()
        assertEquals(1, timeline.monthsCalls)
    }

    @Test
    fun `a second refresh while one runs is ignored`() = runTest(dispatcher) {
        val vm = PhotoGridViewModel(timeline)
        advanceUntilIdle()

        val gate = CompletableDeferred<Unit>().also { timeline.gate = it }
        vm.refresh()
        runCurrent()
        vm.refresh()
        runCurrent()
        assertEquals(2, timeline.monthsCalls)
        gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `an older refresh can't overwrite what try again loaded`() = runTest(dispatcher) {
        val months = timeline.months
        timeline.months = ServerResult.Failed(Problem.Unreachable)
        val vm = PhotoGridViewModel(timeline)
        advanceUntilIdle()

        val gate = CompletableDeferred<Unit>().also { timeline.gate = it }
        vm.refresh()
        runCurrent()

        timeline.gate = null
        timeline.months = months
        vm.retry()
        advanceUntilIdle()
        val loaded = vm.state.value

        timeline.months = ServerResult.Ok(emptyList())
        gate.complete(Unit)
        advanceUntilIdle()
        assertSame(loaded, vm.state.value)
    }

    @Test
    fun `the rail's month is the first until the remote is on another`() = runTest(dispatcher) {
        val vm = PhotoGridViewModel(timeline)
        advanceUntilIdle()
        assertEquals(august, vm.state.value.selectedMonth)

        vm.monthFocused(july)
        assertEquals(july, vm.state.value.selectedMonth)
    }

    @Test
    fun `a refresh keeps the focused month, and one no longer there goes back to the first`() = runTest(dispatcher) {
        val vm = PhotoGridViewModel(timeline)
        advanceUntilIdle()
        vm.monthFocused(july)

        vm.refresh()
        advanceUntilIdle()
        assertEquals(july, vm.state.value.selectedMonth)

        timeline.months = months(august to 2)
        vm.refresh()
        advanceUntilIdle()
        assertEquals(august, vm.state.value.selectedMonth)
    }
}
