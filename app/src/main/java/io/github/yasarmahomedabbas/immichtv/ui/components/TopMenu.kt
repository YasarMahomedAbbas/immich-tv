package io.github.yasarmahomedabbas.immichtv.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.yasarmahomedabbas.immichtv.R
import io.github.yasarmahomedabbas.immichtv.ui.Tab
import io.github.yasarmahomedabbas.immichtv.ui.theme.ImmichTvTheme
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * The top of every main screen: the logo, the tab pills (OK selects), the clock and the
 * Settings gear. The logo is `fg`, not amber: amber is only focus. [selectedFocus] is attached to
 * the selected pill so a screen can put focus there when it opens.
 */
@Composable
fun TopMenu(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    selectedFocus: FocusRequester = remember { FocusRequester() },
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(R.drawable.ic_photo), contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(32.dp))
        Text(stringResource(R.string.app_name), color = colors.onSurface, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.width(36.dp))
        for (tab in Tab.entries) {
            TabPill(
                tab = tab,
                selected = tab == selected,
                onClick = { onSelect(tab) },
                modifier = if (tab == selected) Modifier.focusRequester(selectedFocus) else Modifier,
            )
        }
        Spacer(Modifier.weight(1f))
        Clock()
        Spacer(Modifier.width(12.dp))
        IconButton(
            icon = painterResource(R.drawable.ic_settings),
            contentDescription = stringResource(R.string.settings),
            onClick = onOpenSettings,
        )
    }
}

@Composable
private fun TabPill(tab: Tab, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val fill = if (selected) colors.primary else colors.background
    val text = if (selected) colors.onPrimary else colors.onSurfaceVariant
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = fill,
            contentColor = text,
            focusedContainerColor = fill,
            focusedContentColor = if (selected) text else colors.onSurface,
            pressedContainerColor = fill,
            pressedContentColor = text,
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = houseFocusBorder(CircleShape)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            Modifier.padding(PaddingValues(horizontal = 24.dp, vertical = 10.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (tab == Tab.Search) {
                Icon(painterResource(R.drawable.ic_search), contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(stringResource(tab.label), fontSize = 22.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold)
        }
    }
}

/** The clock's two looks: at rest in the top menu, or over a photo inside a panel. */
internal enum class ClockStyle { Menu, OverPhoto }

/** The time, in the device's 12- or 24-hour format, updated on the minute. */
@Composable
internal fun Clock(style: ClockStyle = ClockStyle.Menu) {
    val context = LocalContext.current
    val pattern = if (LocalInspectionMode.current || DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
    val formatter = remember(pattern) { DateTimeFormatter.ofPattern(pattern) }
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay((60 - now.second) * 1000L)
        }
    }
    val colors = MaterialTheme.colorScheme
    when (style) {
        ClockStyle.Menu -> Text(now.format(formatter), color = colors.onSurfaceVariant, fontSize = 24.sp)
        ClockStyle.OverPhoto -> Text(now.format(formatter), color = colors.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

val Tab.label: Int
    get() = when (this) {
        Tab.Home -> R.string.tab_home
        Tab.Albums -> R.string.tab_albums
        Tab.Photos -> R.string.tab_photos
        Tab.Search -> R.string.tab_search
    }

@Composable
private fun TopMenuPreview(tab: Tab) {
    ImmichTvTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.background).padding(top = 48.dp, start = 96.dp, end = 96.dp, bottom = 24.dp)) {
            TopMenu(selected = tab, onSelect = {}, onOpenSettings = {})
        }
    }
}

@Preview(widthDp = 1920, heightDp = 130)
@Composable
private fun TopMenuHomePreview() = TopMenuPreview(Tab.Home)

@Preview(widthDp = 1920, heightDp = 130)
@Composable
private fun TopMenuAlbumsPreview() = TopMenuPreview(Tab.Albums)

@Preview(widthDp = 1920, heightDp = 130)
@Composable
private fun TopMenuPhotosPreview() = TopMenuPreview(Tab.Photos)

@Preview(widthDp = 1920, heightDp = 130)
@Composable
private fun TopMenuSearchPreview() = TopMenuPreview(Tab.Search)
