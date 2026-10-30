package io.github.yasarmahomedabbas.immichtv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.MaterialTheme

/** The house focus: a 4dp amber outline 4dp off the edge of [shape]. The one focus signal. */
@Composable
fun houseFocusBorder(shape: Shape): Border =
    Border(BorderStroke(4.dp, MaterialTheme.colorScheme.border), inset = (-4).dp, shape = shape)
