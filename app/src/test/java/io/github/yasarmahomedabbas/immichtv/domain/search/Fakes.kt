package io.github.yasarmahomedabbas.immichtv.domain.search

import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.Slide
import kotlinx.coroutines.CompletableDeferred

/** An in-memory repository whose answers a test sets, recording what it was asked. */
class FakeSearchRepository(
    var results: ServerResult<List<Slide>> = ServerResult.Ok(emptyList()),
    var people: ServerResult<List<Person>> = ServerResult.Ok(emptyList()),
    var personPhotos: ServerResult<List<Slide>> = ServerResult.Ok(emptyList()),
) : SearchRepository {
    val searches = mutableListOf<Pair<String, Int>>()
    val personSearches = mutableListOf<Pair<PersonId, Int>>()
    var peopleCalls = 0

    /** While set, a search waits for it: a request still on its way. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun search(description: String, limit: Int): ServerResult<List<Slide>> {
        searches += description to limit
        val answer = results
        gate?.await()
        return answer
    }

    override suspend fun people(): ServerResult<List<Person>> {
        peopleCalls++
        return people
    }

    override suspend fun photosOf(person: PersonId, limit: Int): ServerResult<List<Slide>> {
        personSearches += person to limit
        val answer = personPhotos
        gate?.await()
        return answer
    }
}
