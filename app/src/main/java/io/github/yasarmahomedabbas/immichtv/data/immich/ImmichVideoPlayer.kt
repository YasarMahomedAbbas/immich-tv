package io.github.yasarmahomedabbas.immichtv.data.immich

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.domain.albums.Video
import io.github.yasarmahomedabbas.immichtv.domain.server.SessionStore
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.IOException

private const val VIDEO_SCHEME = "video"

/**
 * The player for a domain [Video], as [PreviewInterceptor] is the image loader for a `Preview`: a
 * screen sets a `MediaItem` whose media id is the video's id, and the address and key header are
 * only added when the stream opens, so neither is in a `MediaItem`. The request goes through the
 * shared [immichHttpClient], whose network interceptor strips the key from a redirect off the server.
 */
@OptIn(UnstableApi::class)
internal fun immichVideoPlayer(context: Context, client: OkHttpClient, sessions: SessionStore): ExoPlayer {
    // Called on the player's loader thread, where blocking is what the resolver is for.
    val resolver = ResolvingDataSource.Resolver { spec: DataSpec ->
        val session = runBlocking { sessions.load() } ?: throw IOException("Not signed in")
        val stream = videoStream(session, Video(PhotoId(spec.uri.schemeSpecificPart)))
        spec.withUri(Uri.parse(stream.url)).withAdditionalHeaders(stream.headers)
    }
    val progressive = ProgressiveMediaSource.Factory(ResolvingDataSource.Factory(OkHttpDataSource.Factory(client), resolver))
    return ExoPlayer.Builder(context).setMediaSourceFactory(VideoSourceFactory(progressive)).build()
}

/** Gives each item the `video:<id>` address the resolver reads. */
@OptIn(UnstableApi::class)
private class VideoSourceFactory(private val progressive: ProgressiveMediaSource.Factory) : MediaSource.Factory by progressive {
    override fun createMediaSource(mediaItem: MediaItem): MediaSource =
        progressive.createMediaSource(mediaItem.buildUpon().setUri("$VIDEO_SCHEME:${mediaItem.mediaId}").build())
}
