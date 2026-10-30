package io.github.yasarmahomedabbas.immichtv.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import io.github.yasarmahomedabbas.immichtv.ui.Tab
import io.github.yasarmahomedabbas.immichtv.ui.components.TopMenu
import io.github.yasarmahomedabbas.immichtv.ui.components.label
import io.github.yasarmahomedabbas.immichtv.ui.search.isVoiceKey
import io.github.yasarmahomedabbas.immichtv.ui.theme.ImmichTvTheme

/**
 * The top menu over the selected tab's content. Focus enters on the selected pill; a tab's
 * content may then move it (the Albums grid does, once its cards are laid out).
 */
@Composable
fun MainScreen(
    tab: Tab,
    onSelectTab: (Tab) -> Unit,
    onOpenSettings: () -> Unit,
    onVoiceKey: () -> Unit,
    search: @Composable () -> Unit,
    albums: @Composable () -> Unit,
    photos: @Composable () -> Unit,
) {
    val menuFocus = remember { FocusRequester() }
    LaunchedEffect(tab) { runCatching { menuFocus.requestFocus() } }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 48.dp, start = 96.dp, end = 96.dp)
            .onPreviewKeyEvent { event ->
                // Wherever focus is on the tab: it opens on the up, and both are this button's.
                if (tab != Tab.Search || !isVoiceKey(event.key)) return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyUp) onVoiceKey()
                true
            },
    ) {
        TopMenu(selected = tab, onSelect = onSelectTab, onOpenSettings = onOpenSettings, selectedFocus = menuFocus)
        Spacer(Modifier.height(40.dp))
        Box(Modifier.weight(1f)) {
            when (tab) {
                Tab.Albums -> albums()
                Tab.Search -> search()
                Tab.Photos -> photos()
                else -> PlaceholderTab(stringResource(tab.label))
            }
        }
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun MainHomePreview() {
    ImmichTvTheme { MainScreen(Tab.Home, {}, {}, onVoiceKey = {}, search = {}, albums = {}, photos = {}) }
}
