package io.github.yasarmahomedabbas.immichtv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.yasarmahomedabbas.immichtv.R
import io.github.yasarmahomedabbas.immichtv.ui.components.SecondaryButton
import io.github.yasarmahomedabbas.immichtv.ui.theme.ImmichTvTheme

/**
 * Placeholder until the first screens land. Settings sits top-right, in the slot the top menu
 * will give the gear beside the clock.
 */
@Composable
fun HomeScreen(onOpenSettings: () -> Unit) {
    val settingsFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { settingsFocus.requestFocus() } }
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Text("Immich TV", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onBackground)
        SecondaryButton(
            text = stringResource(R.string.settings),
            onClick = onOpenSettings,
            icon = painterResource(R.drawable.ic_settings),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 96.dp)
                .focusRequester(settingsFocus),
        )
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun HomePreview() {
    ImmichTvTheme { HomeScreen(onOpenSettings = {}) }
}
