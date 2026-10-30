package io.github.yasarmahomedabbas.immichtv.ui.search

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceInputTest {
    @Test
    fun `what was heard is the first guess, trimmed`() {
        assertEquals("dog on the beach", firstHeard(listOf(" dog on the beach ", "dog on a beach")))
    }

    @Test
    fun `hearing nothing gives nothing`() {
        assertNull(firstHeard(null))
        assertNull(firstHeard(emptyList()))
        assertNull(firstHeard(listOf("  ")))
    }

    @Test
    fun `the mic button's keys are voice keys, and the d-pad's aren't`() {
        assertTrue(isVoiceKey(Key.Search))
        assertTrue(isVoiceKey(Key.VoiceAssist))
        assertTrue(isVoiceKey(Key.Assist))
        assertFalse(isVoiceKey(Key.DirectionCenter))
        assertFalse(isVoiceKey(Key.Back))
    }
}
