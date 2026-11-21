package io.github.yasarmahomedabbas.immichtv.ui.home

import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import io.github.yasarmahomedabbas.immichtv.domain.Problem
import io.github.yasarmahomedabbas.immichtv.domain.albums.Album
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumId
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.domain.home.HomeHero
import io.github.yasarmahomedabbas.immichtv.domain.home.HomePage
import io.github.yasarmahomedabbas.immichtv.domain.home.Memory
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.Slide
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
import java.time.LocalDate

/**
 * Real keys through the real menu and Home page. The clock is stepped by hand: the skeleton's
 * endless pulse would keep an automatic clock from ever going idle.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television", application = Application::class)
class HomeFocusTest {
    @get:Rule
    val rule = createComposeRule()

    private val r0 = Photo(PhotoId("r0"), isVideo = false)
    private val r1 = Photo(PhotoId("r1"), isVideo = false)
    private val memory = Memory(
        takenOn = LocalDate.of(2025, 10, 30),
        slides = listOf(Slide(Photo(PhotoId("m0"), isVideo = false), portrait = false, takenOn = null, place = null)),
    )

    private val lisbon = Album(
        id = AlbumId("lisbon"),
        name = "Lisbon",
        itemCount = 3,
        cover = PhotoId("p-lisbon"),
        shared = false,
        ownedByMe = true,
        updatedAt = Instant.parse("2025-08-01T00:00:00Z"),
        startDate = null,
        endDate = null,
        newestFirst = true,
    )

    private val heroPage = HomePage(HomeHero.FromMemory(memory, yearsAgo = 1), albums = emptyList(), recent = listOf(r0, r1))

    // With an albums row: the refresh path scrolls it first, and without one it never gets as far as focus.
    private val noHeroPage = HomePage(null, listOf(lisbon), listOf(r0, r1))

    // The pill, not the albums row's title of the same name.
    private fun pill(label: String): SemanticsNodeInteraction = rule.onNode(hasText(label) and hasClickAction())
    private val focused = mutableListOf<HomeFocus>()

    private fun open(initial: HomeState, startTab: Tab = Tab.Home): MutableState<HomeState> {
        val state = mutableStateOf(initial)
        val tab = mutableStateOf(startTab)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            ImmichTvTheme {
                MainScreen(
                    tab.value,
                    { tab.value = it },
                    {},
                    home = { mayTakeFocus ->
                        HomeContent(
                            state = state.value,
                            onFocused = {
                                focused += it
                                state.value = state.value.copy(focused = it)
                            },
                            onRetry = { state.value = state.value.copy(load = HomeLoad.Loading) },
                            actions = HomeActions({}, { _, _ -> }, {}, {}, {}, {}, {}),
                            mayTakeFirstFocus = mayTakeFocus,
                        )
                    },
                    onVoiceKey = {},
                    search = {},
                    albums = {},
                    photos = {},
                )
            }
        }
        settle()
        return state
    }

    private fun MutableState<HomeState>.arrive(page: HomePage) {
        value = value.copy(load = HomeLoad.Loaded(page))
        settle()
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
    fun `no key pressed, play takes focus on arrival`() {
        val state = open(HomeState())
        pill("Home").assertIsFocused()
        state.arrive(heroPage)
        assertEquals(listOf(HomeFocus.Play), focused)
    }

    @Test
    fun `right during the skeleton, albums keeps focus`() {
        val state = open(HomeState())
        press(listOf(Key.DirectionRight))
        pill("Albums").assertIsFocused()
        state.arrive(heroPage)
        pill("Albums").assertIsFocused()
        assertEquals(emptyList<HomeFocus>(), focused)
    }

    @Test
    fun `right then left back to home during the skeleton, play takes focus`() {
        val state = open(HomeState())
        press(listOf(Key.DirectionRight, Key.DirectionLeft))
        pill("Home").assertIsFocused()
        state.arrive(heroPage)
        assertEquals(listOf(HomeFocus.Play), focused)
    }

    // The remembered Play isn't on this page, so the refresh path would take focus on arrival too.
    @Test
    fun `right during the skeleton of a page without a hero, albums keeps focus`() {
        val state = open(HomeState())
        press(listOf(Key.DirectionRight))
        pill("Albums").assertIsFocused()
        state.arrive(noHeroPage)
        pill("Albums").assertIsFocused()
        assertEquals(emptyList<HomeFocus>(), focused)
    }

    @Test
    fun `no key pressed, a page without a hero focuses its first album`() {
        val state = open(HomeState())
        state.arrive(noHeroPage)
        assertEquals(listOf(HomeFocus.AlbumCard(lisbon.id)), focused)
    }

    @Test
    fun `try again on a failed load, play takes focus on arrival`() {
        val state = open(HomeState(HomeLoad.Failed(Problem.Unreachable)))
        press(listOf(Key.DirectionDown))
        rule.onNodeWithText("Try again").assertIsFocused()
        press(listOf(Key.DirectionCenter))
        assertEquals("the retry didn't run", HomeLoad.Loading, state.value.load)
        state.arrive(heroPage)
        assertEquals(listOf(HomeFocus.Play), focused)
    }

    @Test
    fun `entering the tab with the page loaded, play takes focus`() {
        open(HomeState(HomeLoad.Loaded(heroPage)))
        assertEquals(listOf(HomeFocus.Play), focused)
    }

    @Test
    fun `switching to home from albums with the page loaded, play takes focus`() {
        open(HomeState(HomeLoad.Loaded(heroPage)), startTab = Tab.Albums)
        pill("Albums").assertIsFocused()
        press(listOf(Key.DirectionLeft))
        pill("Home").assertIsFocused()
        assertEquals(emptyList<HomeFocus>(), focused)
        press(listOf(Key.DirectionCenter))
        assertEquals(listOf(HomeFocus.Play), focused)
    }

    @Test
    fun `a remembered recent photo takes focus, as on coming back`() {
        open(HomeState(HomeLoad.Loaded(heroPage), focused = HomeFocus.Recent(r1.id)))
        assertEquals(listOf(HomeFocus.Recent(r1.id)), focused)
    }
}
