package io.github.yasarmahomedabbas.immichtv.ui.album

import io.github.yasarmahomedabbas.immichtv.R
import io.github.yasarmahomedabbas.immichtv.ui.CameFrom
import org.junit.Assert.assertEquals
import org.junit.Test

/** The back link has to name where Back goes: an album opened from Home's row goes back to Home. */
class AlbumBackLabelTest {
    @Test
    fun `an album from the Albums tab says Albums`() {
        assertEquals(R.string.album_back, albumBackLabel(CameFrom.AlbumsTab))
    }

    @Test
    fun `an album from Home's row says Home`() {
        assertEquals(R.string.album_back_home, albumBackLabel(CameFrom.HomeRow))
    }
}
