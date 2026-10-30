package io.github.yasarmahomedabbas.immichtv.ui.photos

internal const val MIN_MOVE_GAP_MILLIS = 100L

/**
 * Whether a move on the grid may go ahead: it needs [minGapMillis] since the last accepted one, and a
 * frame since it, so the layout gets a turn. The time is the clock at handling, not the key's own, so
 * a stalled main thread doesn't accept its backlog.
 */
internal class MovePace(private val minGapMillis: Long = MIN_MOVE_GAP_MILLIS) {
    private var lastMoveMillis: Long? = null
    private var frameSinceMove = false

    /** True if a move at [nowMillis] may go ahead; accepting one starts the next wait. */
    fun accept(nowMillis: Long): Boolean {
        val last = lastMoveMillis
        if (last != null && (nowMillis - last < minGapMillis || !frameSinceMove)) return false
        lastMoveMillis = nowMillis
        frameSinceMove = false
        return true
    }

    /** A frame has run since the last accepted move. */
    fun frameDrawn() {
        frameSinceMove = true
    }
}
