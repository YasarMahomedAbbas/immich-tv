package io.github.yasarmahomedabbas.immichtv.ui.components

import android.content.Context
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import coil3.size.Scale
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.domain.albums.Preview

// What a full-screen image is decoded for: the Chromecast's screen, however big the preview is.
private const val SCREEN_WIDTH_PX = 1920
private const val SCREEN_HEIGHT_PX = 1080

/** One request for a photo shown full-screen, for preloads and what is drawn alike, so they share the bitmap. */
fun fullScreenRequest(context: Context, id: PhotoId): ImageRequest =
    ImageRequest.Builder(context)
        .data(Preview(id))
        .size(SCREEN_WIDTH_PX, SCREEN_HEIGHT_PX)
        .scale(Scale.FILL)
        .precision(Precision.INEXACT)
        .crossfade(false)
        .build()
