package io.github.yasarmahomedabbas.immichtv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.yasarmahomedabbas.immichtv.AppContainer
import io.github.yasarmahomedabbas.immichtv.domain.server.GetSession
import io.github.yasarmahomedabbas.immichtv.domain.server.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Which top-level screen the app shows. */
sealed interface AppScreen {
    data object Loading : AppScreen

    /** [firstRun] has nowhere to go back to; otherwise it was opened from Settings with [current]. */
    data class Setup(val firstRun: Boolean, val current: Session?) : AppScreen
    data object Home : AppScreen
}

/** Chooses Setup or Home at launch and moves between them. */
class AppViewModel(private val getSession: GetSession) : ViewModel() {
    private val _screen = MutableStateFlow<AppScreen>(AppScreen.Loading)
    val screen: StateFlow<AppScreen> = _screen.asStateFlow()

    init {
        viewModelScope.launch {
            _screen.value = if (getSession() == null) AppScreen.Setup(firstRun = true, current = null) else AppScreen.Home
        }
    }

    fun openSettings() {
        viewModelScope.launch {
            _screen.value = AppScreen.Setup(firstRun = false, current = getSession())
        }
    }

    fun connected() {
        _screen.value = AppScreen.Home
    }

    /** True when Back was handled here; false when the system should finish the app. */
    fun back(): Boolean {
        val current = _screen.value
        if (current is AppScreen.Setup && !current.firstRun) {
            _screen.value = AppScreen.Home
            return true
        }
        return false
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { AppViewModel(container.getSession) }
        }
    }
}
