package io.github.yasarmahomedabbas.immichtv.ui.search

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key

/**
 * Opens the system's speech prompt and hands [onHeard] what it heard; cancelling it does nothing.
 * The prompt needs no microphone permission. A device with no speech service does nothing either,
 * rather than crash.
 */
@Composable
fun rememberVoiceInput(onHeard: (String) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        firstHeard(result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS))?.let(onHeard)
    }
    return {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        try {
            launcher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            // Nothing to listen with: the mic does nothing.
        }
    }
}

/** The best guess the prompt gave, if it heard anything. */
internal fun firstHeard(results: List<String>?): String? =
    results?.firstOrNull { it.isNotBlank() }?.trim()

/** The keys a remote's mic button can arrive as. */
internal fun isVoiceKey(key: Key): Boolean = key == Key.Search || key == Key.VoiceAssist || key == Key.Assist
