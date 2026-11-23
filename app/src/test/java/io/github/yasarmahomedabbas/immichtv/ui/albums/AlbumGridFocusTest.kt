package io.github.yasarmahomedabbas.immichtv.ui.albums

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
import io.github.yasarmahomedabbas.immichtv.domain.albums.Album
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumFilter
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumId
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
import java.time.Instant

/**
 * Real keys through the real menu and grid. The clock is stepped by hand: the skeleton's endless
 * pulse would keep an automatic clock from ever going idle.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television", application = Application::class)
class AlbumGridFocusTest {
    @get:Rule
    val rule = createComposeRule()

    private val first = album("first")
    private val second = album("second")
    private val cardsFocused = mutableListOf<AlbumId>()

    private val loading = AlbumsState()
    private val failed = AlbumsState(AlbumsLoad.Failed(Problem.Unreachable))
    private val loaded = AlbumsState(AlbumsLoad.Loaded(listOf(first, second)))

    private fun album(id: String) = Album(
        id = AlbumId(id),
        name = id,
        itemCount = 3,
        cover = PhotoId("p-$id"),
        shared = false,
        ownedByMe = true,
        updatedAt = Instant.parse("2025-08-01T00:00:00Z"),
        startDate = null,
        endDate = null,
        newestFirst = true,
    )

    private fun open(initial: AlbumsState, startTab: Tab = Tab.Albums): MutableState<AlbumsState> {
        val state = mutableStateOf(initial)
        val tab = mutableStateOf(startTab)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            ImmichTvTheme {
                MainScreen(
                    tab.value,
                    { tab.value = it },
                    {},
                    home = {},
                    onVoiceKey = {},
                    search = {},
                    albums = { mayTakeFocus ->
                        AlbumsContent(
                            state = state.value,
                            onFilter = { state.value = state.value.copy(filter = it) },
                            onCycleSort = {},
                            onFocused = { cardsFocused += it },
                            onOpen = {},
                            onRetry = { state.value = state.value.copy(load = AlbumsLoad.Loading) },
                            mayTakeFirstFocus = mayTakeFocus,
                        )
                    },
                    photos = {},
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
    fun `no key pressed, the first card takes focus on arrival`() {
        val state = open(loading)
        rule.onNodeWithText("Albums").assertIsFocused()
        state.value = loaded
        settle()
        assertEquals(listOf(first.id), cardsFocused)
    }

    @Test
    fun `right during the skeleton, photos keeps focus`() {
        val state = open(loading)
        press(listOf(Key.DirectionRight))
        rule.onNodeWithText("Photos").assertIsFocused()
        state.value = loaded
        settle()
        rule.onNodeWithText("Photos").assertIsFocused()
        assertEquals(emptyList<AlbumId>(), cardsFocused)
    }

    @Test
    fun `left during the skeleton, home keeps focus`() {
        val state = open(loading)
        press(listOf(Key.DirectionLeft))
        rule.onNodeWithText("Home").assertIsFocused()
        state.value = loaded
        settle()
        rule.onNodeWithText("Home").assertIsFocused()
        assertEquals(emptyList<AlbumId>(), cardsFocused)
    }

    @Test
    fun `try again on a failed load, the first card takes focus on arrival`() {
        val state = open(failed)
        press(listOf(Key.DirectionDown))
        rule.onNodeWithText("Try again").assertIsFocused()
        press(listOf(Key.DirectionCenter))
        assertEquals("the retry didn't run", AlbumsLoad.Loading, state.value.load)
        assertEquals(emptyList<AlbumId>(), cardsFocused)
        state.value = loaded
        settle()
        assertEquals(listOf(first.id), cardsFocused)
    }

    @Test
    fun `entering the tab with albums loaded, the first card takes focus`() {
        open(loaded)
        assertEquals(listOf(first.id), cardsFocused)
    }

    @Test
    fun `switching to the tab with albums loaded, the first card takes focus`() {
        open(loaded, startTab = Tab.Photos)
        rule.onNodeWithText("Photos").assertIsFocused()
        press(listOf(Key.DirectionLeft))
        rule.onNodeWithText("Albums").assertIsFocused()
        assertEquals(emptyList<AlbumId>(), cardsFocused)
        press(listOf(Key.DirectionCenter))
        assertEquals(listOf(first.id), cardsFocused)
    }

    // Moved off the pill first, so the answer would be no: only a card that doesn't ask can take focus here.
    @Test
    fun `a remembered album takes focus without asking`() {
        val state = open(loading.copy(focused = second.id))
        press(listOf(Key.DirectionRight))
        rule.onNodeWithText("Photos").assertIsFocused()
        state.value = loaded.copy(focused = second.id)
        settle()
        assertEquals(listOf(second.id), cardsFocused)
    }

    @Test
    fun `a chip that brings the grid back with no remembered album keeps focus`() {
        open(loaded.copy(filter = AlbumFilter.SharedWithMe, load = AlbumsLoad.Loaded(listOf(first))))
        rule.onNodeWithText("Nothing here").assertExists()
        press(listOf(Key.DirectionDown))
        rule.onNodeWithText("Show all albums").assertIsFocused()
        press(listOf(Key.DirectionUp))
        rule.onNodeWithText("Shared with me").assertIsFocused()
        press(listOf(Key.DirectionLeft, Key.DirectionLeft))
        rule.onNodeWithText("All albums").assertIsFocused()
        press(listOf(Key.DirectionCenter))
        rule.onNodeWithText("first").assertExists()
        rule.onNodeWithText("All albums").assertIsFocused()
        assertEquals(emptyList<AlbumId>(), cardsFocused)
    }
}
