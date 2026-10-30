package io.github.yasarmahomedabbas.immichtv.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.yasarmahomedabbas.immichtv.AppContainer
import io.github.yasarmahomedabbas.immichtv.domain.Problem
import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import io.github.yasarmahomedabbas.immichtv.domain.search.LoadPeople
import io.github.yasarmahomedabbas.immichtv.domain.search.LoadPhotosOf
import io.github.yasarmahomedabbas.immichtv.domain.search.Person
import io.github.yasarmahomedabbas.immichtv.domain.search.SearchPhotos
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.Slide
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the results are of. */
sealed interface SearchSubject {
    data class Description(val text: String) : SearchSubject
    data class OfPerson(val person: Person) : SearchSubject
}

sealed interface SearchLoad {
    /** Nothing asked yet. */
    data object Idle : SearchLoad
    data object Loading : SearchLoad
    data object Empty : SearchLoad
    data class Loaded(val slides: List<Slide>) : SearchLoad
    data class Failed(val problem: Problem) : SearchLoad
}

/** The Search tab: what is typed, what was asked for and its results, and the People row. */
data class SearchState(
    val text: String = "",
    val subject: SearchSubject? = null,
    val load: SearchLoad = SearchLoad.Idle,
    val people: List<Person> = emptyList(),
)

/** Everyday subjects to try, in the order shown. Not resources: they are queries to the server. */
internal val SUGGESTIONS = listOf("dog on the beach", "sunset", "snow", "birthday cake", "mountains")

class SearchViewModel(
    private val searchPhotos: SearchPhotos,
    private val loadPeople: LoadPeople,
    private val loadPhotosOf: LoadPhotosOf,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()
    private var request: Job? = null
    private var peopleRequest: Job? = null

    fun edit(text: String) = _state.update { it.copy(text = text) }

    fun submit() = submit(_state.value.text)

    /** Searches for [text], from a chip or the voice prompt too. Blank text asks nothing. */
    fun submit(text: String) {
        val description = text.trim()
        if (description.isEmpty()) return
        _state.update { it.copy(text = description) }
        ask(SearchSubject.Description(description))
    }

    fun choose(person: Person) {
        _state.update { it.copy(text = "") }
        ask(SearchSubject.OfPerson(person))
    }

    fun retry() {
        val state = _state.value
        if (state.load == SearchLoad.Loading) return
        state.subject?.let(::ask)
    }

    fun clear() {
        request?.cancel()
        _state.update { it.copy(text = "", subject = null, load = SearchLoad.Idle) }
    }

    /** Reads the People row again; any failure leaves it out, and search goes on working. */
    fun refreshPeople() {
        if (peopleRequest?.isActive == true) return
        peopleRequest = viewModelScope.launch {
            val people = (loadPeople() as? ServerResult.Ok)?.value.orEmpty()
            _state.update { it.copy(people = people) }
        }
    }

    /** Replaces whatever is on its way: only the newest question gets to answer. */
    private fun ask(subject: SearchSubject) {
        request?.cancel()
        _state.update { it.copy(subject = subject, load = SearchLoad.Loading) }
        request = viewModelScope.launch {
            val result = when (subject) {
                is SearchSubject.Description -> searchPhotos(subject.text)
                is SearchSubject.OfPerson -> loadPhotosOf(subject.person.id)
            }
            val load = when (result) {
                is ServerResult.Ok -> if (result.value.isEmpty()) SearchLoad.Empty else SearchLoad.Loaded(result.value)
                is ServerResult.Failed -> SearchLoad.Failed(result.problem)
            }
            _state.update { it.copy(load = load) }
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { SearchViewModel(container.searchPhotos, container.loadPeople, container.loadPhotosOf) }
        }
    }
}

/** How to word the number of results. Immich gives no real total, so at the cap it can't say "all". */
internal sealed interface ResultCount {
    val count: Int

    data class Photos(override val count: Int) : ResultCount
    data class BestMatches(override val count: Int) : ResultCount
    data class NewestPhotos(override val count: Int) : ResultCount
}

internal fun resultCount(subject: SearchSubject, count: Int, limit: Int): ResultCount = when {
    count < limit -> ResultCount.Photos(count)
    subject is SearchSubject.Description -> ResultCount.BestMatches(count)
    else -> ResultCount.NewestPhotos(count)
}
