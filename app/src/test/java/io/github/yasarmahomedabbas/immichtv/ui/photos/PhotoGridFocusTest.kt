package io.github.yasarmahomedabbas.immichtv.ui.photos

import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import io.github.yasarmahomedabbas.immichtv.domain.Problem
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.ui.Tab
import io.github.yasarmahomedabbas.immichtv.ui.main.MainScreen
import io.github.yasarmahomedabbas.immichtv.ui.theme.ImmichTvTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

/**
 * Real keys through the real menu and grid. The clock is stepped by hand: the skeleton's endless
 * pulse would keep an automatic clock from ever going idle.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television", application = Application::class)
class PhotoGridFocusTest {
    @get:Rule
    val rule = createComposeRule()

    private val january = YearMonth.of(2025, 1)
    private val tileMonths = mutableListOf<YearMonth>()

    private val loading = PhotoGridState()
    private val monthLoading = PhotoGridState(GridLoad.Loaded, listOf(MonthSection(january, 8, MonthPhotos.Loading)))
    private val monthFailed = PhotoGridState(GridLoad.Loaded, listOf(MonthSection(january, 8, MonthPhotos.Failed(Problem.Unreachable))))
    private val monthLoaded = PhotoGridState(
        GridLoad.Loaded,
        listOf(MonthSection(january, 8, MonthPhotos.Loaded(List(8) { Photo(PhotoId("p$it"), isVideo = false) }))),
    )

    private fun open(initial: PhotoGridState): MutableState<PhotoGridState> {
        val state = mutableStateOf(initial)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            ImmichTvTheme {
                MainScreen(
                    Tab.Photos,
                    {},
                    {},
                    home = {},
                    onVoiceKey = {},
                    search = {},
                    albums = {},
                    photos = { mayTakeFocus ->
                        PhotosContent(
                            state = state.value,
                            onNeedMonth = {},
                            onRetryMonth = { state.value = monthLoading },
                            onRetry = {},
                            onRefresh = {},
                            onMonthFocused = { tileMonths += it },
                            onOpen = {},
                            onHold = {},
                            mayTakeFirstFocus = mayTakeFocus,
                        )
                    },
                )
            }
        }
        settle()
        return state
    }

    // With the clock stepped by hand, nothing else tells Compose about a state this test has written.
    private fun settle() {
        Snapshot.sendApplyNotifications()
        repeat(10) { rule.mainClock.advanceTimeBy(50) }
    }

    private fun press(keys: List<Key>) {
        for (key in keys) {
            rule.onRoot().performKeyInput { pressKey(key) }
            settle()
        }
    }

    @Test
    fun `no key pressed, the first tile takes focus on arrival`() {
        val state = open(loading)
        rule.onNodeWithText("Photos").assertIsFocused()
        state.value = monthLoaded
        settle()
        assertEquals(listOf(january), tileMonths)
    }

    @Test
    fun `right during the skeleton, search keeps focus`() {
        val state = open(loading)
        press(listOf(Key.DirectionRight))
        rule.onNodeWithText("Search").assertIsFocused()
        state.value = monthLoaded
        settle()
        rule.onNodeWithText("Search").assertIsFocused()
        assertEquals(emptyList<YearMonth>(), tileMonths)
    }

    // A guard, not proof of the bug: the rail is only reached through the grid, and a tile having had focus already keeps the first-focus move away.
    @Test
    fun `on the rail during the first load, the rail keeps focus`() {
        val state = open(monthLoading)
        press(listOf(Key.DirectionDown, Key.DirectionLeft, Key.DirectionLeft))
        rule.onNodeWithText("January").assertIsFocused()
        tileMonths.clear()
        state.value = monthLoaded
        settle()
        rule.onNodeWithText("January").assertIsFocused()
        assertEquals(emptyList<YearMonth>(), tileMonths)
    }

    @Test
    fun `try again on a failed first month, the first tile takes focus on arrival`() {
        val state = open(monthFailed)
        press(listOf(Key.DirectionDown))
        rule.onNodeWithText("Try again").assertIsFocused()
        tileMonths.clear()
        press(listOf(Key.DirectionCenter))
        assertEquals("the retry didn't run", monthLoading, state.value)
        assertEquals(emptyList<YearMonth>(), tileMonths)
        state.value = monthLoaded
        settle()
        assertEquals(listOf(january), tileMonths)
    }

    @Test
    fun `on a tile of the loading month, the tile keeps its place`() {
        val state = open(monthLoading)
        press(listOf(Key.DirectionDown, Key.DirectionRight))
        assertEquals(listOf(january, january), tileMonths)
        state.value = monthLoaded
        settle()
        assertEquals("focus moved on arrival", listOf(january, january), tileMonths)
    }

    @Test
    fun `entering the tab with the first month loaded, the first tile takes focus`() {
        open(monthLoaded)
        assertEquals(listOf(january), tileMonths)
    }
}
