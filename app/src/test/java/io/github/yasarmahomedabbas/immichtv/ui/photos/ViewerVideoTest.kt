package io.github.yasarmahomedabbas.immichtv.ui.photos

import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerVideoTest {
    private val id = PhotoId("v1")
    private val other = PhotoId("v2")
    private val opened = ViewerVideo(id)
    private val playing = opened.firstFrame(id)

    @Test
    fun `opening starts in loading`() {
        assertEquals(VideoPhase.Loading, opened.phase)
        assertEquals(0, opened.run)
        assertFalse(opened.firstFrame)
        assertTrue(opened.playWhenReady)
    }

    @Test
    fun `the first frame starts playing`() {
        assertEquals(VideoPhase.Playing, playing.phase)
        assertTrue(playing.firstFrame)
    }

    @Test
    fun `OK pauses and OK again resumes`() {
        val paused = playing.toggle()
        assertEquals(VideoPhase.Paused, paused.phase)
        assertFalse(paused.playWhenReady)
        val resumed = paused.toggle()
        assertEquals(VideoPhase.Playing, resumed.phase)
        assertTrue(resumed.playWhenReady)
    }

    @Test
    fun `OK while loading pauses`() {
        assertEquals(VideoPhase.Paused, opened.toggle().phase)
    }

    @Test
    fun `an ended video replays on OK, keeping its last frame`() {
        val ended = playing.ended(id)
        assertEquals(VideoPhase.Ended, ended.phase)
        assertFalse(ended.playWhenReady)
        val replay = ended.toggle()
        assertEquals(VideoPhase.Loading, replay.phase)
        assertEquals(1, replay.run)
        assertTrue(replay.firstFrame)
        assertTrue(replay.playWhenReady)
        assertEquals(VideoPhase.Playing, replay.playing(id).phase)
    }

    @Test
    fun `buffering mid-play stays playing, before the first frame it is loading`() {
        assertEquals(VideoPhase.Playing, playing.buffering(id).phase)
        assertEquals(VideoPhase.Loading, opened.buffering(id).phase)
        assertEquals(VideoPhase.Loading, opened.playing(id).phase)
    }

    @Test
    fun `a late playing or buffering report doesn't undo a pause`() {
        val paused = playing.toggle()
        assertSame(paused, paused.playing(id))
        val pausedWhileLoading = opened.toggle()
        assertSame(pausedWhileLoading, pausedWhileLoading.buffering(id))
        val ended = playing.ended(id)
        assertSame(ended, ended.playing(id))
    }

    @Test
    fun `an error fails, and OK does nothing`() {
        val failed = playing.failed(id)
        assertEquals(VideoPhase.Failed, failed.phase)
        assertSame(failed, failed.toggle())
        assertFalse(failed.playWhenReady)
    }

    @Test
    fun `a report for another video changes nothing`() {
        assertSame(opened, opened.firstFrame(other))
        assertSame(playing, playing.playing(other))
        assertSame(opened, opened.buffering(other))
        assertSame(playing, playing.ended(other))
        assertSame(playing, playing.failed(other))
    }

    @Test
    fun `the badge shows while loading, paused or ended`() {
        assertTrue(opened.showBadge)
        assertTrue(playing.toggle().showBadge)
        assertTrue(playing.ended(id).showBadge)
        assertFalse(playing.showBadge)
        assertFalse(playing.failed(id).showBadge)
    }

    @Test
    fun `the screen stays on only while playing`() {
        assertTrue(playing.keepScreenOn)
        assertFalse(opened.keepScreenOn)
        assertFalse(playing.toggle().keepScreenOn)
        assertFalse(playing.ended(id).keepScreenOn)
        assertFalse(playing.failed(id).keepScreenOn)
    }
}
