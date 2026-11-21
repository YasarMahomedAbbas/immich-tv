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
import io.github.yasarmahomedabbas.immichtv.domain.Problem
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.ui.photos.GridLoad
import io.github.yasarmahomedabbas.immichtv.ui.photos.MonthPhotos
import io.github.yasarmahomedabbas.immichtv.ui.photos.MonthSection
import io.github.yasarmahomedabbas.immichtv.ui.photos.PhotoGridState
import io.github.yasarmahomedabbas.immichtv.ui.photos.tileKey
import io.github.yasarmahomedabbas.immichtv.ui.theme.ImmichTvTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import java.time.YearMonth

/** Real keys on Browse photos: Up from the grid's top row does what Back does, and nowhere else. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television", application = Application::class)
class AlbumPhotosFocusTest {
    @get:Rule
    val rule = createComposeRule()

    private val february = YearMonth.of(2025, 2)
    private val january = YearMonth.of(2025, 1)
    private var backs = 0

    private fun month(month: YearMonth, count: Int) =
        MonthSection(month, count, MonthPhotos.Loaded(List(count) { Photo(PhotoId("$month-$it"), isVideo = false) }))

    private fun open(vararg sections: MonthSection) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            ImmichTvTheme {
                AlbumPhotosContent(
                    albumName = "Summer",
                    state = PhotoGridState(GridLoad.Loaded, sections.toList()),
                    onBack = { backs++ },
                    onNeedMonth = {},
                    onRetryMonth = {},
                    onRetry = {},
                    onOpen = {},
                    onHold = {},
                    restoreTo = null,
                )
            }
        }
        settle()
    }

    // The grid paces moves by the system clock, which the compose clock doesn't move.
    private fun settle() {
        Snapshot.sendApplyNotifications()
        repeat(10) { rule.mainClock.advanceTimeBy(50) }
        ShadowSystemClock.advanceBy(Duration.ofMillis(500))
    }

    private fun press(keys: List<Key>) {
        for (key in keys) {
            rule.onRoot().performKeyInput { pressKey(key) }
            settle()
        }
    }

    private fun focusedTag(): String? = rule.onNode(isFocused()).fetchSemanticsNode().config
        .getOrElseNullable(SemanticsProperties.TestTag) { null }

    @Test
    fun `down from the row lands on the first photo`() {
        open(month(february, 14))
        rule.onNodeWithTag(tileKey(february, 0)).assertIsFocused()
    }

    @Test
    fun `up from any tile of the top row goes back`() {
        open(month(february, 14))
        for (k in 0..5) {
            rule.onNodeWithTag(tileKey(february, k)).assertIsFocused()
            press(listOf(Key.DirectionUp))
            assertEquals("up from tile $k", k + 1, backs)
            press(listOf(Key.DirectionRight))
        }
    }

    @Test
    fun `up from the second row of the first month stays in the grid`() {
        open(month(february, 14))
        press(listOf(Key.DirectionDown))
        assertEquals(tileKey(february, 6), focusedTag())
        press(listOf(Key.DirectionUp))
        assertEquals(0, backs)
        rule.onNodeWithTag(tileKey(february, 0)).assertIsFocused()
    }

    @Test
    fun `up from a later month's top row moves to the month above`() {
        open(month(february, 14), month(january, 8))
        press(listOf(Key.DirectionDown, Key.DirectionDown, Key.DirectionDown))
        val onSecond = focusedTag()
        assertTrue("not on january's first row: $onSecond", onSecond != null && onSecond.startsWith("t-$january-"))
        press(listOf(Key.DirectionUp))
        assertEquals(0, backs)
        val above = focusedTag()
        assertTrue("not back on february: $above", above != null && above.startsWith("t-$february-"))
    }

    @Test
    fun `up from a failed first month's try again goes back`() {
        open(MonthSection(february, 8, MonthPhotos.Failed(Problem.Unreachable)))
        press(listOf(Key.DirectionDown))
        rule.onNodeWithText("Try again").assertIsFocused()
        press(listOf(Key.DirectionUp))
        assertEquals(1, backs)
    }
}
