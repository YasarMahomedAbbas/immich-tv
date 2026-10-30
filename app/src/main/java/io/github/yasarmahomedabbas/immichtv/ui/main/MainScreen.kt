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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import io.github.yasarmahomedabbas.immichtv.ui.Tab
import io.github.yasarmahomedabbas.immichtv.ui.components.TopMenu
import io.github.yasarmahomedabbas.immichtv.ui.search.isVoiceKey
import io.github.yasarmahomedabbas.immichtv.ui.theme.ImmichTvTheme

/**
 * The top menu over the selected tab's content. Focus enters on the selected pill; a tab's
 * content may then move it (Home and the Albums grid do, once they are laid out). The Photos tab is
 * handed `mayTakeFocus` to ask first: yes only while the pill still holds focus or nothing on the
 * screen does, as when a pressed Try again has gone.
 */
@Composable
fun MainScreen(
    tab: Tab,
    onSelectTab: (Tab) -> Unit,
    onOpenSettings: () -> Unit,
    home: @Composable () -> Unit,
    onVoiceKey: () -> Unit,
    search: @Composable () -> Unit,
    albums: @Composable () -> Unit,
    photos: @Composable (mayTakeFocus: () -> Boolean) -> Unit,
) {
    val menuFocus = remember { FocusRequester() }
    var pillHoldsFocus by remember { mutableStateOf(false) }
    var screenHoldsFocus by remember { mutableStateOf(false) }
    LaunchedEffect(tab) { runCatching { menuFocus.requestFocus() } }
    Column(
        Modifier
            .fillMaxSize()
            .onFocusChanged { screenHoldsFocus = it.hasFocus }
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 48.dp, start = 96.dp, end = 96.dp)
            .onPreviewKeyEvent { event ->
                // Wherever focus is on the tab: it opens on the up, and both are this button's.
                if (tab != Tab.Search || !isVoiceKey(event.key)) return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyUp) onVoiceKey()
                true
            },
    ) {
        TopMenu(selected = tab, onSelect = onSelectTab, onOpenSettings = onOpenSettings, selectedFocus = menuFocus, onSelectedFocusChanged = { pillHoldsFocus = it })
        Spacer(Modifier.height(40.dp))
        Box(Modifier.weight(1f)) {
            when (tab) {
                Tab.Home -> home()
                Tab.Albums -> albums()
                Tab.Search -> search()
                Tab.Photos -> photos { pillHoldsFocus || !screenHoldsFocus }
            }
        }
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun MainHomePreview() {
    ImmichTvTheme { MainScreen(Tab.Home, {}, {}, onVoiceKey = {}, home = {}, search = {}, albums = {}, photos = {}) }
}
