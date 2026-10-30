package io.github.yasarmahomedabbas.immichtv.ui.search

import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchFieldKeysTest {
    /** Seen on the TV: left to the text field, these moved its cursor and the remote couldn't reach the mic. */
    @Test
    fun `the D-pad's arrows leave the field`() {
        assertEquals(FocusDirection.Up, fieldFocusMove(Key.DirectionUp))
        assertEquals(FocusDirection.Down, fieldFocusMove(Key.DirectionDown))
        assertEquals(FocusDirection.Left, fieldFocusMove(Key.DirectionLeft))
        assertEquals(FocusDirection.Right, fieldFocusMove(Key.DirectionRight))
    }

    @Test
    fun `OK stays with the field, which opens the keyboard`() {
        assertNull(fieldFocusMove(Key.DirectionCenter))
        assertNull(fieldFocusMove(Key.Enter))
    }
}
