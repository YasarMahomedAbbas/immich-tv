package io.github.yasarmahomedabbas.immichtv.domain.albums

import io.github.yasarmahomedabbas.immichtv.domain.Problem
import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumsTest {
    private val mine = album(id = "m", name = "beach", itemCount = 5, updatedAt = "2025-01-01T00:00:00Z")
    private val mine2 = album(id = "m2", name = "Alps", itemCount = 50, updatedAt = "2025-03-01T00:00:00Z")
    private val shared = album(id = "s", name = "Wedding", itemCount = 20, ownedByMe = false, updatedAt = "2025-02-01T00:00:00Z")
    private val all = listOf(mine, shared, mine2)

    private fun ids(albums: List<Album>) = albums.map { it.id.value }

    @Test
    fun `all albums by recently updated`() {
        assertEquals(listOf("m2", "s", "m"), ids(all.filterAndSort(AlbumFilter.All, AlbumSort.RecentlyUpdated)))
    }

    @Test
    fun `all albums by name ignore case`() {
        assertEquals(listOf("m2", "m", "s"), ids(all.filterAndSort(AlbumFilter.All, AlbumSort.Name)))
    }

    @Test
    fun `all albums by most items`() {
        assertEquals(listOf("m2", "s", "m"), ids(all.filterAndSort(AlbumFilter.All, AlbumSort.MostItems)))
    }

    @Test
    fun `mine keeps only owned albums, in each sort`() {
        assertEquals(listOf("m2", "m"), ids(all.filterAndSort(AlbumFilter.Mine, AlbumSort.RecentlyUpdated)))
        assertEquals(listOf("m2", "m"), ids(all.filterAndSort(AlbumFilter.Mine, AlbumSort.Name)))
        assertEquals(listOf("m2", "m"), ids(all.filterAndSort(AlbumFilter.Mine, AlbumSort.MostItems)))
    }

    @Test
    fun `shared with me keeps only others' albums, in each sort`() {
        for (sort in AlbumSort.entries) {
            assertEquals(listOf("s"), ids(all.filterAndSort(AlbumFilter.SharedWithMe, sort)))
        }
    }

    @Test
    fun `ties go by name`() {
        val b = album(id = "b", name = "b", itemCount = 1, updatedAt = "2025-01-01T00:00:00Z")
        val a = album(id = "a", name = "A", itemCount = 1, updatedAt = "2025-01-01T00:00:00Z")
        assertEquals(listOf("a", "b"), ids(listOf(b, a).filterAndSort(AlbumFilter.All, AlbumSort.RecentlyUpdated)))
        assertEquals(listOf("a", "b"), ids(listOf(b, a).filterAndSort(AlbumFilter.All, AlbumSort.MostItems)))
    }

    @Test
    fun `an empty list stays empty`() {
        assertTrue(emptyList<Album>().filterAndSort(AlbumFilter.Mine, AlbumSort.Name).isEmpty())
    }

    @Test
    fun `load albums passes the repository's answer through`() = runTest {
        val repo = FakeAlbumRepository(albums = ServerResult.Failed(Problem.KeyRejected))
        assertEquals(ServerResult.Failed(Problem.KeyRejected), LoadAlbums(repo)())
    }

    @Test
    fun `load album asks for the strip's 30 items`() = runTest {
        val repo = FakeAlbumRepository()
        val result = LoadAlbum(repo)(AlbumId("a1"))

        assertEquals(ServerResult.Ok(AlbumPage(album(), AlbumContents(3, 1, emptyList()))), result)
        assertEquals(listOf(30), repo.contentsLimits)
    }

    @Test
    fun `load album passes a failed album through without asking for contents`() = runTest {
        val repo = FakeAlbumRepository().apply { albumFailure = Problem.KeyLacksPermission }
        assertEquals(ServerResult.Failed(Problem.KeyLacksPermission), LoadAlbum(repo)(AlbumId("a1")))
        assertTrue(repo.contentsLimits.isEmpty())
    }

    @Test
    fun `load album passes failed contents through`() = runTest {
        val repo = FakeAlbumRepository(contents = ServerResult.Failed(Problem.Unreachable))
        assertEquals(ServerResult.Failed(Problem.Unreachable), LoadAlbum(repo)(AlbumId("a1")))
    }

    private val knownPage = AlbumPage(album(), AlbumContents(3, 1, emptyList()))

    @Test
    fun `if changed, an unchanged album doesn't read its contents`() = runTest {
        val repo = FakeAlbumRepository(albums = ServerResult.Ok(listOf(album())))
        val result = LoadAlbum(repo).ifChanged(knownPage)

        assertTrue((result as ServerResult.Ok).value === knownPage)
        assertTrue(repo.contentsLimits.isEmpty())
    }

    @Test
    fun `if changed, a new item count reads the contents`() = runTest {
        val changed = album(itemCount = 11)
        val contents = AlbumContents(4, 1, emptyList())
        val repo = FakeAlbumRepository(albums = ServerResult.Ok(listOf(changed)), contents = ServerResult.Ok(contents))

        assertEquals(ServerResult.Ok(AlbumPage(changed, contents)), LoadAlbum(repo).ifChanged(knownPage))
        assertEquals(listOf(30), repo.contentsLimits)
    }

    @Test
    fun `if changed, a new updated time reads the contents`() = runTest {
        val changed = album(updatedAt = "2025-09-01T00:00:00Z")
        val repo = FakeAlbumRepository(albums = ServerResult.Ok(listOf(changed)))

        assertEquals(ServerResult.Ok(AlbumPage(changed, knownPage.contents)), LoadAlbum(repo).ifChanged(knownPage))
        assertEquals(listOf(30), repo.contentsLimits)
    }

    @Test
    fun `if changed, a failed album is failed`() = runTest {
        val repo = FakeAlbumRepository().apply { albumFailure = Problem.Unreachable }
        assertEquals(ServerResult.Failed(Problem.Unreachable), LoadAlbum(repo).ifChanged(knownPage))
        assertTrue(repo.contentsLimits.isEmpty())
    }

    @Test
    fun `if changed, failed contents are failed`() = runTest {
        val repo = FakeAlbumRepository(
            albums = ServerResult.Ok(listOf(album(itemCount = 11))),
            contents = ServerResult.Failed(Problem.Unreachable),
        )
        assertEquals(ServerResult.Failed(Problem.Unreachable), LoadAlbum(repo).ifChanged(knownPage))
    }

    @Test
    fun `album timeline asks the repository for that album's`() {
        val repo = FakeAlbumRepository()
        val timeline = AlbumTimeline(repo)(album())
        assertTrue(timeline === repo.timelines[AlbumId("a1")])
    }
}
