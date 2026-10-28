package io.github.yasarmahomedabbas.immichtv.data.settings

import io.github.yasarmahomedabbas.immichtv.domain.server.ApiKey
import io.github.yasarmahomedabbas.immichtv.domain.server.ServerAddress
import io.github.yasarmahomedabbas.immichtv.domain.server.Session
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StoredSessionRepositoryTest {
    private class MemoryStorage : StringStorage {
        val values = mutableMapOf<String, String>()
        override fun get(key: String) = values[key]
        override fun put(values: Map<String, String?>) {
            for ((key, value) in values) if (value == null) this.values.remove(key) else this.values[key] = value
        }
    }

    /** Reverses and prefixes, so the stored value is never the plaintext; can be told to fail. */
    private class FakeCipher : SecretCipher {
        var broken = false
        var decrypts = 0
        override fun encrypt(plain: String) = "sealed:" + plain.reversed()
        override fun decrypt(sealed: String): String {
            decrypts++
            check(!broken) { "key lost" }
            return sealed.removePrefix("sealed:").reversed()
        }
    }

    private val storage = MemoryStorage()
    private val cipher = FakeCipher()
    private val repository = StoredSessionRepository(storage, cipher)
    private val session = Session(ServerAddress.parse("https://photos.example.com")!!, ApiKey("s3cr3t-key"))

    @Test
    fun `a saved session loads back`() = runTest {
        repository.save(session)
        assertEquals(session, repository.load())
    }

    @Test
    fun `the key is never stored in the clear`() = runTest {
        repository.save(session)

        val sealed = storage.values["api_key_sealed"]
        assertNotNull(sealed)
        assertFalse(sealed!!.contains("s3cr3t-key"))
        assertFalse(storage.values.values.any { it.contains("s3cr3t-key") })
        assertEquals("https://photos.example.com", storage.values["server_url"])
    }

    /** A new repository on the same storage, as after the app restarts: nothing cached. */
    private fun restarted() = StoredSessionRepository(storage, cipher)

    @Test
    fun `a saved session loads back after a restart`() = runTest {
        repository.save(session)
        assertEquals(session, restarted().load())
    }

    @Test
    fun `two loads decrypt once`() = runTest {
        repository.save(session)
        val fresh = restarted()

        assertEquals(session, fresh.load())
        assertEquals(session, fresh.load())
        assertEquals(1, cipher.decrypts)
    }

    @Test
    fun `a load after save returns the saved session without decrypting`() = runTest {
        val other = Session(ServerAddress.parse("http://nas:2283")!!, ApiKey("other"))
        repository.save(session)
        repository.load()
        repository.save(other)

        assertEquals(other, repository.load())
        assertEquals(0, cipher.decrypts)
    }

    @Test
    fun `nothing stored is no session`() = runTest {
        assertNull(repository.load())
    }

    @Test
    fun `a missing half is no session`() = runTest {
        storage.values["server_url"] = "https://photos.example.com"
        assertNull(restarted().load())
        storage.values.clear()
        storage.values["api_key_sealed"] = cipher.encrypt("k")
        assertNull(restarted().load())
    }

    @Test
    fun `a key that can't be decrypted is no session, and is remembered as none`() = runTest {
        repository.save(session)
        cipher.broken = true
        val fresh = restarted()

        assertNull(fresh.load())
        cipher.broken = false
        assertNull(fresh.load())
        assertEquals(1, cipher.decrypts)
    }

    @Test
    fun `a stored address that no longer parses is no session`() = runTest {
        storage.values["server_url"] = "ftp://nas"
        storage.values["api_key_sealed"] = cipher.encrypt("k")
        assertNull(repository.load())
    }
}
