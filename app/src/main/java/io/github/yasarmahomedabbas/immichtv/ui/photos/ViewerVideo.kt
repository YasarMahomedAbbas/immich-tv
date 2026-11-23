package io.github.yasarmahomedabbas.immichtv.ui.photos

import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId

/** Where a video opened in the viewer is: what the screen shows and whether the player should run. */
internal enum class VideoPhase { Loading, Playing, Paused, Ended, Failed }

/**
 * The playback of the one video open in the viewer. The player's reports name the video they are
 * about, so one that arrives for another video (after a move) changes nothing. [run] goes up on each
 * replay, which tells the player to start the video again. [firstFrame] says the picture has
 * something to show: until then the still shows through.
 */
internal data class ViewerVideo(
    val id: PhotoId,
    val phase: VideoPhase = VideoPhase.Loading,
    val run: Int = 0,
    val firstFrame: Boolean = false,
) {
    val playWhenReady get() = phase == VideoPhase.Loading || phase == VideoPhase.Playing
    val showBadge get() = phase in BADGED
    val keepScreenOn get() = phase == VideoPhase.Playing

    fun firstFrame(from: PhotoId) = about(from) {
        copy(firstFrame = true, phase = if (phase == VideoPhase.Loading) VideoPhase.Playing else phase)
    }

    /**
     * The player's own reports only move a video that is meant to run: one that arrives just after
     * OK paused it (sent before the player heard of the pause) leaves it paused.
     */
    fun playing(from: PhotoId) = about(from) {
        if (firstFrame && playWhenReady) copy(phase = VideoPhase.Playing) else this
    }

    /** Buffering mid-play keeps the badge off; only before the first frame does it count as loading. */
    fun buffering(from: PhotoId) = about(from) {
        if (!firstFrame && playWhenReady) copy(phase = VideoPhase.Loading) else this
    }

    fun ended(from: PhotoId) = about(from) { copy(phase = VideoPhase.Ended) }

    fun failed(from: PhotoId) = about(from) { copy(phase = VideoPhase.Failed) }

    /** OK: pause, resume, or replay an ended video, keeping its last frame until the replay's first. */
    fun toggle() = when (phase) {
        VideoPhase.Playing -> copy(phase = VideoPhase.Paused)
        VideoPhase.Paused -> copy(phase = VideoPhase.Playing)
        VideoPhase.Ended -> copy(phase = VideoPhase.Loading, run = run + 1)
        VideoPhase.Loading -> copy(phase = VideoPhase.Paused)
        VideoPhase.Failed -> this
    }

    private inline fun about(from: PhotoId, change: ViewerVideo.() -> ViewerVideo) = if (from == id) change() else this

    private companion object {
        val BADGED = setOf(VideoPhase.Loading, VideoPhase.Paused, VideoPhase.Ended)
    }
}
