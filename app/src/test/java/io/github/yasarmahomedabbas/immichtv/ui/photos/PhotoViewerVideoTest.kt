package io.github.yasarmahomedabbas.immichtv.ui.photos

import android.app.Application
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent.ACTION_DOWN
import android.view.KeyEvent.ACTION_UP
import android.view.KeyEvent.KEYCODE_BACK
import android.view.KeyEvent.KEYCODE_DPAD_CENTER
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.test.pressKey
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.ui.theme.ImmichTvTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The viewer's player through real composition and keys. The player is a fake: the app's ExoPlayer
 * can only resolve a video through the data layer's source factory, and nothing is decoded here
 * anyway. The fake records what the viewer asks of it.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television", application = Application::class)
class PhotoViewerVideoTest {
    @get:Rule
    val rule = createComposeRule()

    private val players = mutableListOf<FakePlayer>()
    private val items = listOf(
        Photo(PhotoId("v1"), isVideo = true),
        Photo(PhotoId("v2"), isVideo = true),
        Photo(PhotoId("p3"), isVideo = false),
    )

    private fun open(start: Int) {
        var index by mutableStateOf(start)
        rule.setContent {
            ImmichTvTheme {
                PhotoViewerContent(
                    photo = items[index],
                    newPlayer = { FakePlayer().also(players::add) },
                    onMove = { index = (index + it).coerceIn(items.indices) },
                )
            }
        }
        rule.waitForIdle()
    }

    private fun press(key: Key) {
        rule.onRoot().performKeyInput { pressKey(key) }
        rule.waitForIdle()
    }

    private fun send(action: Int, code: Int, repeat: Int = 0): Boolean {
        val now = SystemClock.uptimeMillis()
        val consumed = rule.onRoot().performKeyPress(KeyEvent(android.view.KeyEvent(now, now, action, code, repeat)))
        rule.waitForIdle()
        return consumed
    }

    @Test
    fun `a video composes one player`() {
        open(0)
        assertEquals(1, players.size)
        assertEquals(listOf("v1"), players[0].mediaIds)
        assertTrue(players[0].prepared)
    }

    @Test
    fun `a photo composes no player`() {
        open(2)
        assertEquals(0, players.size)
    }

    @Test
    fun `video to video keeps the player, video to photo releases it`() {
        open(0)
        press(Key.DirectionRight)
        assertEquals(1, players.size)
        assertFalse(players[0].released)
        assertEquals(listOf("v1", "v2"), players[0].mediaIds)
        press(Key.DirectionRight)
        assertEquals(1, players.size)
        assertTrue(players[0].released)
    }

    @Test
    fun `only a fresh OK press toggles`() {
        open(0)
        val player = players.single()
        assertTrue(player.wantsToPlay)
        send(ACTION_DOWN, KEYCODE_DPAD_CENTER, repeat = 1)
        assertTrue(player.wantsToPlay)
        send(ACTION_UP, KEYCODE_DPAD_CENTER)
        assertTrue(player.wantsToPlay)
        send(ACTION_DOWN, KEYCODE_DPAD_CENTER)
        assertFalse(player.wantsToPlay)
        send(ACTION_UP, KEYCODE_DPAD_CENTER)
        send(ACTION_DOWN, KEYCODE_DPAD_CENTER)
        assertTrue(player.wantsToPlay)
    }

    @Test
    fun `back is left to the activity`() {
        open(0)
        assertFalse(send(ACTION_DOWN, KEYCODE_BACK))
        assertFalse(send(ACTION_UP, KEYCODE_BACK))
    }

    @Test
    fun `OK on a photo is left alone`() {
        open(2)
        assertFalse(send(ACTION_DOWN, KEYCODE_DPAD_CENTER))
    }

    /** Takes what the viewer asks for and reports nothing back on its own. */
    private class FakePlayer : SimpleBasePlayer(Looper.getMainLooper()) {
        var wantsToPlay = false
        var prepared = false
        var released = false
        val mediaIds = mutableListOf<String>()

        override fun getState(): State = State.Builder()
            .setAvailableCommands(
                Player.Commands.Builder()
                    .addAll(
                        Player.COMMAND_PLAY_PAUSE,
                        Player.COMMAND_PREPARE,
                        Player.COMMAND_SET_MEDIA_ITEM,
                        Player.COMMAND_SET_VIDEO_SURFACE,
                        Player.COMMAND_RELEASE,
                    )
                    .build(),
            )
            .setPlayWhenReady(wantsToPlay, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .build()

        override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
            wantsToPlay = playWhenReady
            return Futures.immediateVoidFuture()
        }

        override fun handlePrepare(): ListenableFuture<*> {
            prepared = true
            return Futures.immediateVoidFuture()
        }

        override fun handleSetMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<*> {
            mediaIds += mediaItems.map { it.mediaId }
            return Futures.immediateVoidFuture()
        }

        override fun handleSetVideoOutput(videoOutput: Any): ListenableFuture<*> = Futures.immediateVoidFuture()

        override fun handleRelease(): ListenableFuture<*> {
            released = true
            return Futures.immediateVoidFuture()
        }
    }
}
