package io.github.yasarmahomedabbas.immichtv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/** Placeholder until the first screens land: proves the app installs and opens on the TV. */
@Composable
fun HomeScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Text("Immich TV", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onBackground)
    }
}
