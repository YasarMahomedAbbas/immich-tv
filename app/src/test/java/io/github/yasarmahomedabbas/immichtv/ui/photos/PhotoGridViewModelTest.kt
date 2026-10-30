package io.github.yasarmahomedabbas.immichtv.ui.photos

import io.github.yasarmahomedabbas.immichtv.domain.Problem
import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.domain.photos.FakeTimeline
import io.github.yasarmahomedabbas.immichtv.domain.photos.TimelineMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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
}
