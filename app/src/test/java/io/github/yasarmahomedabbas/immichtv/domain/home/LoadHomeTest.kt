package io.github.yasarmahomedabbas.immichtv.domain.home

import io.github.yasarmahomedabbas.immichtv.domain.Problem
import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import io.github.yasarmahomedabbas.immichtv.domain.albums.FakeAlbumRepository
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.domain.albums.album
import io.github.yasarmahomedabbas.immichtv.domain.photos.FakeLibraryRepository
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.Slide
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class LoadHomeTest {
    private val today = LocalDate.of(2026, 10, 30)
    private val older = album(id = "old", updatedAt = "2025-01-01T00:00:00Z")
    private val newer = album(id = "new", updatedAt = "2025-06-01T00:00:00Z")
    private val albums = FakeAlbumRepository(albums = ServerResult.Ok(listOf(older, newer)))
    private val memories = FakeMemoryRepository()
    private val library = FakeLibraryRepository()
    private val load = LoadHome(albums, memories, library) { today }

    private fun slide(id: String, video: Boolean = false) = Slide(Photo(PhotoId(id), video), false, null, null)

    private fun memory(year: Int, vararg slides: Slide) = Memory(LocalDate.of(year, 10, 30), slides.toList())

    private suspend fun page() = (load() as ServerResult.Ok).value

    @Test
    fun `a memory beats an album, and it asks for today`() = runTest {
        val memory = memory(2025, slide("a"))
        memories.memories = ServerResult.Ok(listOf(memory))

        assertEquals(HomeHero.FromMemory(memory, yearsAgo = 1), page().hero)
        assertEquals(listOf(today), memories.days)
    }

    @Test
    fun `the newest year's memory wins, and says how long ago`() = runTest {
        val newest = memory(2023, slide("b"))
        memories.memories = ServerResult.Ok(listOf(memory(2019, slide("a")), newest, memory(2021, slide("c"))))

        assertEquals(HomeHero.FromMemory(newest, yearsAgo = 3), page().hero)
    }

    @Test
    fun `a memory keeps only its photos`() = runTest {
        memories.memories = ServerResult.Ok(listOf(memory(2025, slide("a"), slide("v", video = true), slide("b"))))

        val hero = page().hero as HomeHero.FromMemory
        assertEquals(listOf("a", "b"), hero.memory.photos.map { it.id.value })
    }

    @Test
    fun `a memory of only videos is skipped, for an older one or the album`() = runTest {
        val photos = memory(2020, slide("a"))
        memories.memories = ServerResult.Ok(listOf(memory(2025, slide("v", video = true)), photos))
        assertEquals(HomeHero.FromMemory(photos, yearsAgo = 6), page().hero)

        memories.memories = ServerResult.Ok(listOf(memory(2025, slide("v", video = true))))
        assertEquals(HomeHero.FromAlbum(newer), page().hero)
    }

    @Test
    fun `memories failing for any reason falls back to the album`() = runTest {
        for (problem in Problem.entries) {
            memories.memories = ServerResult.Failed(problem)
            assertEquals(HomeHero.FromAlbum(newer), page().hero)
        }
    }

    @Test
    fun `the fallback is the most recently updated album with something in it`() = runTest {
        val empty = album(id = "empty", itemCount = 0, updatedAt = "2026-01-01T00:00:00Z")
        albums.albums = ServerResult.Ok(listOf(older, empty, newer))

        assertEquals(HomeHero.FromAlbum(newer), page().hero)
        assertEquals(listOf("empty", "new", "old"), page().albums.map { it.id.value })
    }

    @Test
    fun `albums failing fails the page, even with a memory`() = runTest {
        memories.memories = ServerResult.Ok(listOf(memory(2025, slide("a"))))
        albums.albums = ServerResult.Failed(Problem.KeyRejected)

        assertEquals(ServerResult.Failed(Problem.KeyRejected), load())
    }

    @Test
    fun `recent photos failing fails the page`() = runTest {
        library.recent = ServerResult.Failed(Problem.Unreachable)

        assertEquals(ServerResult.Failed(Problem.Unreachable), load())
    }

    @Test
    fun `no album and no memory gives no hero`() = runTest {
        albums.albums = ServerResult.Ok(emptyList())

        assertNull(page().hero)
    }

    @Test
    fun `the albums row is recently updated first, up to its limit`() = runTest {
        albums.albums = ServerResult.Ok(List(12) { album(id = "a$it", updatedAt = "2025-01-%02dT00:00:00Z".format(it + 1)) })

        assertEquals(List(LoadHome.ALBUM_ROW) { "a${11 - it}" }, page().albums.map { it.id.value })
    }

    @Test
    fun `the recent row asks for its limit and shows what comes`() = runTest {
        val photos = listOf(Photo(PhotoId("r"), false))
        library.recent = ServerResult.Ok(photos)

        assertEquals(photos, page().recent)
        assertEquals(listOf(LoadHome.RECENT_ROW), library.recentLimits)
    }
}
