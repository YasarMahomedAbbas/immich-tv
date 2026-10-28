package io.github.yasarmahomedabbas.immichtv.domain.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ApiKeyTest {
    @Test
    fun `toString never contains the key`() {
        val key = ApiKey("s3cr3t-key-value")
        assertFalse(key.toString().contains("s3cr3t"))
        val session = Session(ServerAddress.parse("nas:2283")!!, key)
        assertFalse(session.toString().contains("s3cr3t"))
    }

    @Test
    fun `keys are equal by value`() {
        assertEquals(ApiKey("a"), ApiKey("a"))
        assertEquals(ApiKey("a").hashCode(), ApiKey("a").hashCode())
        assertNotEquals(ApiKey("a"), ApiKey("b"))
        assertNotEquals(ApiKey("a"), "a")
    }
}
