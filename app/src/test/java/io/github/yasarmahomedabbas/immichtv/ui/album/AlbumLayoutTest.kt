package io.github.yasarmahomedabbas.immichtv.ui.album

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Test

/** The album screen doesn't scroll, so its parts have to add up to the canvas. Found on the TV. */
class AlbumLayoutTest {
    @Test
    fun `the strip and its focus outline end above the bottom of the screen`() {
        assertTrue("reaches $AlbumContentBottom", AlbumContentBottom <= 1080.dp)
    }

    @Test
    fun `every setting gets at least its minimum width beside the mosaic`() {
        assertTrue("each gets $SettingWidth", SettingWidth >= 230.dp)
    }
}
