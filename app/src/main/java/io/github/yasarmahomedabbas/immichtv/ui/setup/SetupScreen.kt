package io.github.yasarmahomedabbas.immichtv.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.yasarmahomedabbas.immichtv.R
import io.github.yasarmahomedabbas.immichtv.ui.components.HouseTextField
import io.github.yasarmahomedabbas.immichtv.ui.components.PrimaryButton
import io.github.yasarmahomedabbas.immichtv.ui.components.SecondaryButton
import io.github.yasarmahomedabbas.immichtv.ui.theme.ImmichTvTheme

/** Server address and API key, checked on Connect. [firstRun] Setup has no Back. */
@Composable
fun SetupScreen(viewModel: SetupViewModel, firstRun: Boolean, onConnected: () -> Unit, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) { viewModel.connected.collect { onConnected() } }
    BackHandler(enabled = !firstRun, onBack = onBack)
    SetupContent(
        state = state,
        onAddressChange = viewModel::onAddressChange,
        onApiKeyChange = viewModel::onApiKeyChange,
        onToggleKeyVisible = viewModel::toggleKeyVisible,
        onConnect = viewModel::connect,
    )
}

@Composable
private fun SetupContent(
    state: SetupState,
    onAddressChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onToggleKeyVisible: () -> Unit,
    onConnect: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val addressFocus = remember { FocusRequester() }
    val connectFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // Focus enters on what is left to do: the address on a fresh setup, else Connect.
        runCatching { if (state.address.isEmpty()) addressFocus.requestFocus() else connectFocus.requestFocus() }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(top = 48.dp, start = 96.dp, end = 96.dp, bottom = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 960.dp)
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(18.dp))
                .padding(48.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(stringResource(R.string.setup_title), color = colors.onSurface, fontSize = 32.sp)
            Text(stringResource(R.string.setup_hint), color = colors.onSurfaceVariant, fontSize = 22.sp)
            HouseTextField(
                value = state.address,
                onValueChange = onAddressChange,
                label = stringResource(R.string.setup_address),
                modifier = Modifier.fillMaxWidth().focusRequester(addressFocus),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                HouseTextField(
                    value = state.apiKey,
                    onValueChange = onApiKeyChange,
                    label = stringResource(R.string.setup_api_key),
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onConnect() }),
                    visualTransformation = if (state.keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                )
                SecondaryButton(
                    text = stringResource(if (state.keyVisible) R.string.setup_hide_key else R.string.setup_show_key),
                    onClick = onToggleKeyVisible,
                )
            }
            PrimaryButton(
                text = stringResource(if (state.connecting) R.string.setup_connecting else R.string.setup_connect),
                onClick = onConnect,
                modifier = Modifier.focusRequester(connectFocus),
                enabled = !state.connecting,
            )
            state.problem?.let {
                val text = if (it == SetupProblem.ServerTooOld) {
                    stringResource(it.message, state.serverVersion.orEmpty())
                } else {
                    stringResource(it.message)
                }
                Text(text, color = colors.onSurface, fontSize = 22.sp)
            }
        }
    }
}

private val SetupProblem.message: Int
    get() = when (this) {
        SetupProblem.InvalidAddress -> R.string.setup_problem_invalid_address
        SetupProblem.MissingKey -> R.string.setup_problem_missing_key
        SetupProblem.Unreachable -> R.string.setup_problem_unreachable
        SetupProblem.NotPhotoServer -> R.string.setup_problem_not_photo_server
        SetupProblem.ServerTooOld -> R.string.setup_problem_server_too_old
        SetupProblem.KeyRejected -> R.string.setup_problem_key_rejected
        SetupProblem.KeyLacksPermission -> R.string.setup_problem_key_lacks_permission
    }

@Composable
private fun SetupPreview(state: SetupState) {
    ImmichTvTheme { SetupContent(state, {}, {}, {}, {}) }
}

private val filled = SetupState(address = "http://192.168.1.10:2283", apiKey = "abcdef0123456789")

@Preview(device = Devices.TV_1080p)
@Composable
private fun SetupEmptyPreview() = SetupPreview(SetupState())

@Preview(device = Devices.TV_1080p)
@Composable
private fun SetupFilledPreview() = SetupPreview(filled)

@Preview(device = Devices.TV_1080p)
@Composable
private fun SetupConnectingPreview() = SetupPreview(filled.copy(connecting = true))

@Preview(device = Devices.TV_1080p)
@Composable
private fun SetupInvalidAddressPreview() = SetupPreview(SetupState(problem = SetupProblem.InvalidAddress))

@Preview(device = Devices.TV_1080p)
@Composable
private fun SetupMissingKeyPreview() = SetupPreview(filled.copy(apiKey = "", problem = SetupProblem.MissingKey))

@Preview(device = Devices.TV_1080p)
@Composable
private fun SetupUnreachablePreview() = SetupPreview(filled.copy(problem = SetupProblem.Unreachable))

@Preview(device = Devices.TV_1080p)
@Composable
private fun SetupNotPhotoServerPreview() = SetupPreview(filled.copy(problem = SetupProblem.NotPhotoServer))

@Preview(device = Devices.TV_1080p)
@Composable
private fun SetupServerTooOldPreview() =
    SetupPreview(filled.copy(problem = SetupProblem.ServerTooOld, serverVersion = "1.118.0"))

@Preview(device = Devices.TV_1080p)
@Composable
private fun SetupKeyRejectedPreview() = SetupPreview(filled.copy(problem = SetupProblem.KeyRejected))

@Preview(device = Devices.TV_1080p)
@Composable
private fun SetupKeyLacksPermissionPreview() = SetupPreview(filled.copy(keyVisible = true, problem = SetupProblem.KeyLacksPermission))
