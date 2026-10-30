package io.github.yasarmahomedabbas.immichtv.domain.search

import io.github.yasarmahomedabbas.immichtv.domain.Problem
import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.Slide
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {
    private val repository = FakeSearchRepository()
    private val slide = Slide(Photo(PhotoId("a"), false), false, null, null)

    @Test
    fun `a search is trimmed and asks for the most it can show`() = runTest {
        repository.results = ServerResult.Ok(listOf(slide))
        assertEquals(ServerResult.Ok(listOf(slide)), SearchPhotos(repository)("  dog on the beach "))
        assertEquals(listOf("dog on the beach" to 100), repository.searches)
    }

    @Test
    fun `a blank search matches nothing and never reaches the server`() = runTest {
        assertEquals(ServerResult.Ok(emptyList<Slide>()), SearchPhotos(repository)("   "))
        assertTrue(repository.searches.isEmpty())
    }

    @Test
    fun `a failed search passes through`() = runTest {
        repository.results = ServerResult.Failed(Problem.SearchUnavailable)
        assertEquals(ServerResult.Failed(Problem.SearchUnavailable), SearchPhotos(repository)("snow"))
    }

    @Test
    fun `only named people are offered`() = runTest {
        val ada = Person(PersonId("1"), "Ada")
        repository.people = ServerResult.Ok(listOf(ada, Person(PersonId("2"), ""), Person(PersonId("3"), "  ")))
        assertEquals(ServerResult.Ok(listOf(ada)), LoadPeople(repository)())
    }

    @Test
    fun `failing to read people passes through`() = runTest {
        repository.people = ServerResult.Failed(Problem.KeyLacksPermission)
        assertEquals(ServerResult.Failed(Problem.KeyLacksPermission), LoadPeople(repository)())
    }

    @Test
    fun `a person's photos ask for the most it can show`() = runTest {
        repository.personPhotos = ServerResult.Ok(listOf(slide))
        assertEquals(ServerResult.Ok(listOf(slide)), LoadPhotosOf(repository)(PersonId("1")))
        assertEquals(listOf(PersonId("1") to 1000), repository.personSearches)
    }
}
