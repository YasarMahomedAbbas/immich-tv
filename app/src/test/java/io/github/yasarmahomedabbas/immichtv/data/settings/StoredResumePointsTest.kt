package io.github.yasarmahomedabbas.immichtv.data.settings

import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumId
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.ResumePoint
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoredResumePointsTest {
    private val storage = MemoryStorage()
    private val store = StoredResumePoints(storage)
    private val summer = AlbumId("summer")
    private val wedding = AlbumId("wedding")

    @Test
    fun `a spot with a seed loads back`() = runTest {
        val point = ResumePoint(PhotoId("p7"), 6, shuffleSeed = -42L)
        store.save(summer, point)
        assertEquals(point, store.load(summer))
        assertEquals("6|-42|p7", storage.values["resume.summer"])
    }

    @Test
    fun `a spot without a seed loads back`() = runTest {
        val point = ResumePoint(PhotoId("p7"), 0, shuffleSeed = null)
        store.save(summer, point)
        assertEquals(point, store.load(summer))
        assertEquals("0||p7", storage.values["resume.summer"])
    }

    @Test
    fun `a photo id with a bar in it survives`() = runTest {
        val point = ResumePoint(PhotoId("a|b|c"), 3, shuffleSeed = 9L)
        store.save(summer, point)
        assertEquals(point, store.load(summer))
    }

    @Test
    fun `two albums are kept apart`() = runTest {
        store.save(summer, ResumePoint(PhotoId("s"), 1, null))
        store.save(wedding, ResumePoint(PhotoId("w"), 2, 5L))
        assertEquals(ResumePoint(PhotoId("s"), 1, null), store.load(summer))
        assertEquals(ResumePoint(PhotoId("w"), 2, 5L), store.load(wedding))
    }

    @Test
    fun `an album with nothing saved has no spot`() = runTest {
        assertNull(store.load(summer))
    }

    @Test
    fun `an unreadable value is no spot`() = runTest {
        for (bad in listOf("", "3", "3|", "3||", "x||p", "-1||p", "3|seed|p", "3|1.5|p", "|1|p")) {
            storage.values["resume.summer"] = bad
            assertNull(bad, store.load(summer))
        }
    }
}
