package io.github.yasarmahomedabbas.immichtv.domain.search

import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import io.github.yasarmahomedabbas.immichtv.domain.map
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.Slide

@JvmInline
value class PersonId(val value: String)

/** Someone the server recognises in photos. */
data class Person(val id: PersonId, val name: String)

/** A person's face image: what a screen hands the image loader. */
data class PersonFace(val person: PersonId)

/** Finding photos by what is in them, or by who. */
interface SearchRepository {
    /** The closest matches to [description], best first, at most [limit]. */
    suspend fun search(description: String, limit: Int): ServerResult<List<Slide>>

    suspend fun people(): ServerResult<List<Person>>

    /** Photos of [person], newest first, at most [limit]. */
    suspend fun photosOf(person: PersonId, limit: Int): ServerResult<List<Slide>>
}

/** The best matches for a description. A blank one matches nothing and never reaches the server. */
class SearchPhotos(private val repository: SearchRepository) {
    suspend operator fun invoke(description: String): ServerResult<List<Slide>> {
        val text = description.trim()
        if (text.isEmpty()) return ServerResult.Ok(emptyList())
        return repository.search(text, RESULT_LIMIT)
    }

    companion object {
        const val RESULT_LIMIT = 100
    }
}

/** The people to offer: only those someone has named. */
class LoadPeople(private val repository: SearchRepository) {
    suspend operator fun invoke(): ServerResult<List<Person>> =
        repository.people().map { people -> people.filter { it.name.isNotBlank() } }
}

/** A person's newest photos. */
class LoadPhotosOf(private val repository: SearchRepository) {
    suspend operator fun invoke(person: PersonId): ServerResult<List<Slide>> =
        repository.photosOf(person, PERSON_LIMIT)

    companion object {
        const val PERSON_LIMIT = 1000
    }
}
