package io.github.yasarmahomedabbas.immichtv.ui.album

import android.app.Application
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import io.github.yasarmahomedabbas.immichtv.R
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumContents
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumPage
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.domain.albums.album
import io.github.yasarmahomedabbas.immichtv.ui.theme.ImmichTvTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real keys on the album screen, between its buttons, settings and the "In this album" row. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television", application = Application::class)
class AlbumFocusTest {
    @get:Rule
    val rule = createComposeRule()

    private val items = List(30) { Photo(PhotoId("p$it"), isVideo = false) }
    private val loaded = AlbumState(AlbumLoad.Loaded(AlbumPage(album(), AlbumContents(30, 0, items))))
    private val downs = mutableListOf<PhotoId>()

    private fun open(focusBrowse: Boolean = false, focusTile: PhotoId? = null, fallbackTile: PhotoId? = null) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            ImmichTvTheme {
                AlbumContent(
                    state = loaded,
                    focusBrowse = focusBrowse,
                    focusTile = focusTile,
                    fallbackTile = fallbackTile,
                    backLabel = R.string.album_back,
                    onBack = {},
                    onStartSlideshow = { _, _ -> },
                    onBrowsePhotos = {},
                    onDownFromRow = { _, tile -> downs += tile },
                    onCycleSeconds = {},
                    onCycleOrder = {},
                    onCycleVideos = {},
                    onToggleDateAndPlace = {},
                    onRetry = {},
                )
            }
        }
        settle()
    }

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

    /** The test tag of the focused node, which is the photo id for a row tile, or null for anything else. */
    private fun focusedTag(): String? = rule.onNode(isFocused()).fetchSemanticsNode().config
        .getOrElseNullable(SemanticsProperties.TestTag) { null }

    @Test
    fun `down from the buttons reaches the row, and down from a row tile opens the grid from that tile`() {
        open()
        rule.onNodeWithText("Start slideshow").assertIsFocused()
        var guard = 0
        while (focusedTag() == null && guard++ < 6) press(listOf(Key.DirectionDown))
        val tile = focusedTag()
        assertTrue("the row wasn't reached", tile != null && tile.startsWith("p"))
        assertEquals("reaching the row opened the grid", emptyList<PhotoId>(), downs)

        press(listOf(Key.DirectionDown))
        assertEquals(listOf(PhotoId(tile!!)), downs)
    }

    @Test
    fun `down from the third tile opens the grid from it`() {
        open(focusTile = PhotoId("p0"))
        rule.onNodeWithTag("p0").assertIsFocused()
        press(listOf(Key.DirectionRight, Key.DirectionRight, Key.DirectionDown))
        assertEquals(listOf(PhotoId("p2")), downs)
    }

    @Test
    fun `left, right and up on the row don't open the grid`() {
        open(focusTile = PhotoId("p1"))
        press(listOf(Key.DirectionRight, Key.DirectionLeft))
        rule.onNodeWithTag("p1").assertIsFocused()
        press(listOf(Key.DirectionUp))
        assertEquals(emptyList<PhotoId>(), downs)
        assertEquals("up didn't leave the row", null, focusedTag())
        val settings = listOf("Each photo for", "Order", "Videos", "Show date & place")
        assertTrue(
            "up didn't land on a setting",
            settings.any { runCatching { rule.onNodeWithText(it, substring = true).assertIsFocused() }.isSuccess },
        )
    }

    @Test
    fun `coming back to a tile far along the row focuses it`() {
        open(focusTile = PhotoId("p20"))
        rule.onNodeWithTag("p20").assertIsFocused()
    }

    @Test
    fun `coming back to a tile no longer in the row lands on start slideshow`() {
        open(focusTile = PhotoId("gone"))
        rule.onNodeWithText("Start slideshow").assertIsFocused()
    }

    @Test
    fun `coming back with a tile not in the row falls back to the opened tile`() {
        open(focusTile = PhotoId("gone"), fallbackTile = PhotoId("p20"))
        rule.onNodeWithTag("p20").assertIsFocused()
    }

    @Test
    fun `the shown tile wins over the fallback`() {
        open(focusTile = PhotoId("p3"), fallbackTile = PhotoId("p2"))
        rule.onNodeWithTag("p3").assertIsFocused()
    }

    @Test
    fun `with neither in the row it lands on start slideshow`() {
        open(focusTile = PhotoId("gone"), fallbackTile = PhotoId("also-gone"))
        rule.onNodeWithText("Start slideshow").assertIsFocused()
    }

    @Test
    fun `coming back from browse photos still lands on browse photos`() {
        open(focusBrowse = true)
        rule.onNodeWithText("Browse photos").assertIsFocused()
    }
}
