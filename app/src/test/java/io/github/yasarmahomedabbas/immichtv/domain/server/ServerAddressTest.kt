package io.github.yasarmahomedabbas.immichtv.domain.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerAddressTest {
    private fun url(input: String) = ServerAddress.parse(input)?.url

    @Test
    fun `adds http when no scheme is given`() {
        assertEquals("http://192.168.1.10:2283", url("192.168.1.10:2283"))
    }

    @Test
    fun `keeps https`() {
        assertEquals("https://photos.example.com", url("https://photos.example.com"))
    }

    @Test
    fun `trims whitespace and trailing slashes`() {
        assertEquals("http://nas:2283", url("  http://nas:2283//  "))
    }

    @Test
    fun `keeps a path`() {
        assertEquals("https://example.com/immich", url("https://example.com/immich/"))
    }

    @Test
    fun `blank is not an address`() {
        assertNull(url(""))
        assertNull(url("   "))
    }

    @Test
    fun `only http and https are accepted`() {
        assertNull(url("ftp://nas:2283"))
        assertNull(url("file:///etc/hosts"))
    }

    @Test
    fun `an address needs a host`() {
        assertNull(url("http://"))
        assertNull(url("http:///photos"))
        assertNull(url("http://bad host"))
    }
}
