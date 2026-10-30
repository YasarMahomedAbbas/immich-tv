package io.github.yasarmahomedabbas.immichtv.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.yasarmahomedabbas.immichtv.R
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumId
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.ScreensaverSource
import io.github.yasarmahomedabbas.immichtv.ui.albums.AlbumsLoad
import io.github.yasarmahomedabbas.immichtv.ui.components.BackLink
import io.github.yasarmahomedabbas.immichtv.ui.components.SettingButton
import io.github.yasarmahomedabbas.immichtv.ui.theme.ImmichTvTheme

/** The gear's screen: the server, and what the screensaver plays. [back] names where Back goes. */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    @StringRes back: Int,
    focusScreensaver: Boolean,
    onOpenServer: () -> Unit,
    onOpenScreensaver: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    SettingsContent(state, back, focusScreensaver, onOpenServer, onOpenScreensaver, onBack)
}

@Composable
private fun SettingsContent(
    state: SettingsState,
    @StringRes back: Int,
    focusScreensaver: Boolean,
    onOpenServer: () -> Unit,
    onOpenScreensaver: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val serverFocus = remember { FocusRequester() }
    val screensaverFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { (if (focusScreensaver) screensaverFocus else serverFocus).requestFocus() } }
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(top = 48.dp, start = 96.dp, end = 96.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        BackLink(stringResource(back), onClick = onBack)
        Text(stringResource(R.string.settings), color = colors.onSurface, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
        // Nothing here is the screen's one action, so neither is a primary button (rule 4).
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            SettingButton(
                label = stringResource(R.string.settings_server),
                value = state.address ?: stringResource(R.string.settings_server_none),
                onClick = onOpenServer,
                modifier = Modifier.focusRequester(serverFocus),
            )
            SettingButton(
                label = stringResource(R.string.screensaver_title),
                value = sourceText(state.sourceValue),
                onClick = onOpenScreensaver,
                modifier = Modifier.focusRequester(screensaverFocus),
            )
        }
    }
}

@Composable
private fun sourceText(value: SourceValue): String = when (value) {
    SourceValue.Random -> stringResource(R.string.screensaver_random)
    is SourceValue.Named -> value.name
    SourceValue.UnnamedAlbum -> stringResource(R.string.screensaver_album)
    SourceValue.AlbumGone -> stringResource(R.string.screensaver_album_gone)
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun SettingsPreview() = ImmichTvTheme {
    SettingsContent(SettingsState(address = "http://nas:2283"), R.string.tab_home, false, {}, {}, {})
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun SettingsAlbumGonePreview() = ImmichTvTheme {
    SettingsContent(
        SettingsState(ScreensaverSource.Album(AlbumId("gone")), "http://nas:2283", AlbumsLoad.Loaded(emptyList())),
        R.string.tab_albums,
        true,
        {},
        {},
        {},
    )
}
