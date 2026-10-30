package io.github.yasarmahomedabbas.immichtv.ui.search

import io.github.yasarmahomedabbas.immichtv.domain.Problem
import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.domain.search.FakeSearchRepository
import io.github.yasarmahomedabbas.immichtv.domain.search.LoadPeople
import io.github.yasarmahomedabbas.immichtv.domain.search.LoadPhotosOf
import io.github.yasarmahomedabbas.immichtv.domain.search.Person
import io.github.yasarmahomedabbas.immichtv.domain.search.PersonId
import io.github.yasarmahomedabbas.immichtv.domain.search.SearchPhotos
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.Slide
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeSearchRepository()
    private val ada = Person(PersonId("1"), "Ada")

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun slide(id: String) = Slide(Photo(PhotoId(id), false), false, null, null)

    private fun viewModel() = SearchViewModel(SearchPhotos(repository), LoadPeople(repository), LoadPhotosOf(repository))

    @Test
    fun `nothing is asked to begin with`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(SearchState(), vm.state.value)
        assertTrue(repository.searches.isEmpty())
    }

    @Test
    fun `submit searches for what was typed, trimmed`() = runTest(dispatcher) {
        repository.results = ServerResult.Ok(listOf(slide("a")))
        val vm = viewModel()
        vm.edit(" snow ")
        assertTrue(repository.searches.isEmpty())

        vm.submit()
        assertEquals(SearchLoad.Loading, vm.state.value.load)
        assertEquals(SearchSubject.Description("snow"), vm.state.value.subject)
        advanceUntilIdle()

        assertEquals(SearchLoad.Loaded(listOf(slide("a"))), vm.state.value.load)
        assertEquals(listOf("snow" to 100), repository.searches)
    }

    @Test
    fun `a chip or voice searches and fills the field`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.submit("sunset")
        advanceUntilIdle()
        assertEquals("sunset", vm.state.value.text)
        assertEquals(listOf("sunset" to 100), repository.searches)
    }

    @Test
    fun `blank text asks nothing and leaves the results`() = runTest(dispatcher) {
        repository.results = ServerResult.Ok(listOf(slide("a")))
        val vm = viewModel()
        vm.submit("snow")
        advanceUntilIdle()

        vm.submit("   ")
        advanceUntilIdle()

        assertEquals(1, repository.searches.size)
        assertEquals(SearchLoad.Loaded(listOf(slide("a"))), vm.state.value.load)
    }

    @Test
    fun `no results are empty`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.submit("snow")
        advanceUntilIdle()
        assertEquals(SearchLoad.Empty, vm.state.value.load)
    }

    @Test
    fun `a newer search replaces one still on its way`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>().also { repository.gate = it }
        repository.results = ServerResult.Ok(listOf(slide("old")))
        val vm = viewModel()
        vm.submit("old")
        runCurrent()

        repository.gate = null
        repository.results = ServerResult.Ok(listOf(slide("new")))
        vm.submit("new")
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(SearchLoad.Loaded(listOf(slide("new"))), vm.state.value.load)
        assertEquals(SearchSubject.Description("new"), vm.state.value.subject)
    }

    @Test
    fun `a failure shows, and retry asks again`() = runTest(dispatcher) {
        repository.results = ServerResult.Failed(Problem.SearchUnavailable)
        val vm = viewModel()
        vm.submit("snow")
        advanceUntilIdle()
        assertEquals(SearchLoad.Failed(Problem.SearchUnavailable), vm.state.value.load)

        repository.results = ServerResult.Ok(listOf(slide("a")))
        vm.retry()
        assertEquals(SearchLoad.Loading, vm.state.value.load)
        advanceUntilIdle()
        assertEquals(SearchLoad.Loaded(listOf(slide("a"))), vm.state.value.load)
    }

    @Test
    fun `retry with nothing asked, or while loading, does nothing`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.retry()
        advanceUntilIdle()
        assertTrue(repository.searches.isEmpty())

        vm.submit("snow")
        vm.retry()
        advanceUntilIdle()
        assertEquals(1, repository.searches.size)
    }

    @Test
    fun `clear empties the field and the results, and an answer on its way is dropped`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>().also { repository.gate = it }
        repository.results = ServerResult.Ok(listOf(slide("a")))
        val vm = viewModel()
        vm.submit("snow")
        runCurrent()

        vm.clear()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals("", vm.state.value.text)
        assertNull(vm.state.value.subject)
        assertEquals(SearchLoad.Idle, vm.state.value.load)
    }

    @Test
    fun `choosing a person shows their photos under their name`() = runTest(dispatcher) {
        repository.personPhotos = ServerResult.Ok(listOf(slide("a")))
        val vm = viewModel()
        vm.edit("half typed")
        vm.choose(ada)
        assertEquals(SearchLoad.Loading, vm.state.value.load)
        advanceUntilIdle()

        assertEquals(SearchSubject.OfPerson(ada), vm.state.value.subject)
        assertEquals("", vm.state.value.text)
        assertEquals(SearchLoad.Loaded(listOf(slide("a"))), vm.state.value.load)
        assertEquals(listOf(ada.id to 1000), repository.personSearches)
    }

    @Test
    fun `text after a person replaces their photos`() = runTest(dispatcher) {
        repository.personPhotos = ServerResult.Ok(listOf(slide("person")))
        repository.results = ServerResult.Ok(listOf(slide("text")))
        val vm = viewModel()
        vm.choose(ada)
        advanceUntilIdle()

        vm.submit("snow")
        advanceUntilIdle()

        assertEquals(SearchSubject.Description("snow"), vm.state.value.subject)
        assertEquals(SearchLoad.Loaded(listOf(slide("text"))), vm.state.value.load)
    }

    @Test
    fun `retry repeats the person, not a search`() = runTest(dispatcher) {
        repository.personPhotos = ServerResult.Failed(Problem.Unreachable)
        val vm = viewModel()
        vm.choose(ada)
        advanceUntilIdle()

        repository.personPhotos = ServerResult.Ok(listOf(slide("a")))
        vm.retry()
        advanceUntilIdle()

        assertEquals(SearchLoad.Loaded(listOf(slide("a"))), vm.state.value.load)
        assertEquals(2, repository.personSearches.size)
        assertTrue(repository.searches.isEmpty())
    }

    @Test
    fun `people are read on refresh, named only`() = runTest(dispatcher) {
        repository.people = ServerResult.Ok(listOf(ada, Person(PersonId("2"), "")))
        val vm = viewModel()
        vm.refreshPeople()
        advanceUntilIdle()
        assertEquals(listOf(ada), vm.state.value.people)
    }

    @Test
    fun `people failing leaves the row out and search working`() = runTest(dispatcher) {
        repository.people = ServerResult.Failed(Problem.KeyLacksPermission)
        repository.results = ServerResult.Ok(listOf(slide("a")))
        val vm = viewModel()
        vm.refreshPeople()
        advanceUntilIdle()
        assertTrue(vm.state.value.people.isEmpty())
        assertNull(vm.state.value.subject)

        vm.submit("snow")
        advanceUntilIdle()
        assertEquals(SearchLoad.Loaded(listOf(slide("a"))), vm.state.value.load)
    }

    @Test
    fun `a refresh that fails takes away people read before, and doesn't touch results`() = runTest(dispatcher) {
        repository.people = ServerResult.Ok(listOf(ada))
        repository.results = ServerResult.Ok(listOf(slide("a")))
        val vm = viewModel()
        vm.refreshPeople()
        vm.submit("snow")
        advanceUntilIdle()

        repository.people = ServerResult.Failed(Problem.Unreachable)
        vm.refreshPeople()
        advanceUntilIdle()

        assertTrue(vm.state.value.people.isEmpty())
        assertEquals(SearchLoad.Loaded(listOf(slide("a"))), vm.state.value.load)
    }

    @Test
    fun `the suggestions are the agreed ones, in order`() {
        assertEquals(listOf("dog on the beach", "sunset", "snow", "birthday cake", "mountains"), SUGGESTIONS)
    }

    @Test
    fun `under the cap the count is of photos, at it the words say it may be more`() {
        val text = SearchSubject.Description("snow")
        val person = SearchSubject.OfPerson(ada)
        assertEquals(ResultCount.Photos(62), resultCount(text, 62, 100))
        assertEquals(ResultCount.BestMatches(100), resultCount(text, 100, 100))
        assertEquals(ResultCount.Photos(999), resultCount(person, 999, 1000))
        assertEquals(ResultCount.NewestPhotos(1000), resultCount(person, 1000, 1000))
    }
}
