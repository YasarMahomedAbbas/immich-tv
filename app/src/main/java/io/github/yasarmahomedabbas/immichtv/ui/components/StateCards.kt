package io.github.yasarmahomedabbas.immichtv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.yasarmahomedabbas.immichtv.R
import io.github.yasarmahomedabbas.immichtv.domain.Problem

/** Nothing to show: what's missing, a hint, and the one thing to do about it (focused). */
@Composable
fun EmptyState(title: String, hint: String, actionLabel: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    StateCard(title, hint, actionLabel, onAction, modifier)
}

/** Loading failed: why, in the words for [problem], and Try again (focused). */
@Composable
fun FailedState(problem: Problem, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    StateCard(
        title = stringResource(R.string.failed_title),
        hint = stringResource(problem.message),
        actionLabel = stringResource(R.string.try_again),
        onAction = onRetry,
        modifier = modifier,
    )
}

@Composable
private fun StateCard(title: String, hint: String, actionLabel: String, onAction: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val actionFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { actionFocus.requestFocus() } }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .widthIn(max = 820.dp)
                .background(colors.surface, RoundedCornerShape(18.dp))
                .padding(48.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, color = colors.onSurface, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Text(hint, color = colors.onSurfaceVariant, fontSize = 22.sp, textAlign = TextAlign.Center)
            PrimaryButton(text = actionLabel, onClick = onAction, modifier = Modifier.focusRequester(actionFocus))
        }
    }
}

private val Problem.message: Int
    get() = when (this) {
        Problem.Unreachable -> R.string.problem_unreachable
        Problem.KeyRejected -> R.string.problem_key_rejected
        Problem.KeyLacksPermission -> R.string.problem_key_lacks_permission
        Problem.NotSignedIn -> R.string.problem_not_signed_in
    }
