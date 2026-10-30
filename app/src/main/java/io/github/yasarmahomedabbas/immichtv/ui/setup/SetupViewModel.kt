package io.github.yasarmahomedabbas.immichtv.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.yasarmahomedabbas.immichtv.AppContainer
import io.github.yasarmahomedabbas.immichtv.domain.server.ConnectResult
import io.github.yasarmahomedabbas.immichtv.domain.server.ConnectToServer
import io.github.yasarmahomedabbas.immichtv.domain.server.Session
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SetupState(
    val address: String = "",
    val apiKey: String = "",
    val keyVisible: Boolean = false,
    val connecting: Boolean = false,
    val problem: SetupProblem? = null,
    /** The version a too-old server runs, shown with [SetupProblem.ServerTooOld]. */
    val serverVersion: String? = null,
)

/** Why Connect didn't work; each has its own message on screen. */
enum class SetupProblem { InvalidAddress, MissingKey, Unreachable, NotPhotoServer, ServerTooOld, KeyRejected, KeyLacksPermission }

class SetupViewModel(private val connect: ConnectToServer, current: Session?) : ViewModel() {
    private val _state = MutableStateFlow(
        SetupState(address = current?.server?.url.orEmpty(), apiKey = current?.apiKey?.value.orEmpty()),
    )
    val state: StateFlow<SetupState> = _state.asStateFlow()

    private val _connected = Channel<Unit>(Channel.BUFFERED)

    /** Fires once when the server accepted the key and the session was saved. */
    val connected: Flow<Unit> = _connected.receiveAsFlow()

    fun onAddressChange(value: String) = _state.update { it.copy(address = value, problem = null, serverVersion = null) }

    fun onApiKeyChange(value: String) = _state.update { it.copy(apiKey = value, problem = null, serverVersion = null) }

    fun toggleKeyVisible() = _state.update { it.copy(keyVisible = !it.keyVisible) }

    fun connect() {
        val start = _state.value
        if (start.connecting) return
        _state.update { it.copy(connecting = true, problem = null, serverVersion = null) }
        viewModelScope.launch {
            val result = connect(start.address, start.apiKey)
            val problem = when (result) {
                is ConnectResult.Connected -> null
                ConnectResult.InvalidAddress -> SetupProblem.InvalidAddress
                ConnectResult.MissingKey -> SetupProblem.MissingKey
                ConnectResult.Unreachable -> SetupProblem.Unreachable
                ConnectResult.NotPhotoServer -> SetupProblem.NotPhotoServer
                is ConnectResult.ServerTooOld -> SetupProblem.ServerTooOld
                ConnectResult.KeyRejected -> SetupProblem.KeyRejected
                ConnectResult.KeyLacksPermission -> SetupProblem.KeyLacksPermission
            }
            val version = (result as? ConnectResult.ServerTooOld)?.version
            _state.update { it.copy(connecting = false, problem = problem, serverVersion = version) }
            if (problem == null) _connected.send(Unit)
        }
    }

    companion object {
        fun factory(container: AppContainer, current: Session?) = viewModelFactory {
            initializer { SetupViewModel(container.connectToServer, current) }
        }
    }
}
