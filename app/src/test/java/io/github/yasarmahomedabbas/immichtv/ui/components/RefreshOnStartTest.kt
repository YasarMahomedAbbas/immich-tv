package io.github.yasarmahomedabbas.immichtv.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshOnStartTest {
    @Test
    fun `nothing focused yet has nothing to lose`() {
        assertFalse(lostFocus(null, listOf("a")))
    }

    @Test
    fun `a focused item still there isn't lost`() {
        assertFalse(lostFocus("a", listOf("a", "b")))
    }

    @Test
    fun `a focused item that's gone is lost`() {
        assertTrue(lostFocus("a", listOf("b")))
        assertTrue(lostFocus("a", emptyList()))
    }
}
