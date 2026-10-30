package io.github.yasarmahomedabbas.immichtv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/** What the remote's keys do here, as (key, what it does) pairs, in a pill. Says so; never takes focus. */
@Composable
fun KeyHint(hints: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .background(colors.surface, CircleShape)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for ((key, action) in hints) {
            Box(Modifier.background(colors.primary, CircleShape).padding(horizontal = 12.dp, vertical = 2.dp)) {
                Text(key, color = colors.onPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            }
            Text(
                action,
                color = colors.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(end = 12.dp),
            )
        }
    }
}
