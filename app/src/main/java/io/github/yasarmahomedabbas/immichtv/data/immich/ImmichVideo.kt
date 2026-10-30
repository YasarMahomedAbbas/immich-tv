package io.github.yasarmahomedabbas.immichtv.data.immich

import io.github.yasarmahomedabbas.immichtv.domain.albums.Video
import io.github.yasarmahomedabbas.immichtv.domain.server.Session

/** Where a video's bytes are and what to send for them. [toString] omits the headers, so the key can't reach a log. */
internal data class VideoStream(val url: String, val headers: Map<String, String>) {
    override fun toString(): String = "VideoStream(url=$url)"
}

/**
 * `<server>/api/assets/<id>/video/playback`, the same path from v1.133 to v3.3.5. The key goes in
 * the `x-api-key` header, never in the `key` query parameter the endpoint also accepts.
 */
internal fun videoStream(session: Session, video: Video): VideoStream = VideoStream(
    url = apiUrl(session.server, "assets").newBuilder()
        .addPathSegment(video.photo.value)
        .addPathSegment("video")
        .addPathSegment("playback")
        .build()
        .toString(),
    headers = mapOf(API_KEY_HEADER to session.apiKey.value),
)
