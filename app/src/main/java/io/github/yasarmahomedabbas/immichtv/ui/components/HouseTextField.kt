package io.github.yasarmahomedabbas.immichtv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.yasarmahomedabbas.immichtv.ui.theme.Focus

private val FieldCorner = 18.dp

/**
 * A labelled single-line text field for the remote: `raised` fill (it sits on `surface`
 * panels), 18dp corners, the house amber outline when focused. Centre (OK) on the remote opens
 * the on-screen keyboard.
 */
@Composable
fun HouseTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val colors = MaterialTheme.colorScheme
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                val ok = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                if (ok && event.type == KeyEventType.KeyUp) {
                    keyboard?.show()
                    true
                } else {
                    false
                }
            }
            .focusOutline(focused, FieldCorner),
        singleLine = true,
        textStyle = TextStyle(color = colors.onSurface, fontSize = 22.sp),
        cursorBrush = SolidColor(colors.onSurface),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        decorationBox = { inner ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceVariant, RoundedCornerShape(FieldCorner))
                    .padding(horizontal = 24.dp, vertical = 14.dp),
            ) {
                Text(label, color = colors.onSurfaceVariant, fontSize = 18.sp)
                Spacer(Modifier.height(4.dp))
                inner()
            }
        },
    )
}

/** The house focus: a 4dp amber outline drawn 4dp outside the item's edge, when [focused]. */
fun Modifier.focusOutline(focused: Boolean, corner: Dp): Modifier = drawBehind {
    if (!focused) return@drawBehind
    val stroke = 4.dp.toPx()
    // The stroke is centred on the path, so the path runs 4dp gap + half the stroke outside.
    val out = 4.dp.toPx() + stroke / 2
    drawRoundRect(
        color = Focus,
        topLeft = Offset(-out, -out),
        size = Size(size.width + 2 * out, size.height + 2 * out),
        cornerRadius = CornerRadius(corner.toPx() + out),
        style = Stroke(stroke),
    )
}
