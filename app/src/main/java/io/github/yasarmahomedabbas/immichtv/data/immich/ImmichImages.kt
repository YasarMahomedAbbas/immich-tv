package io.github.yasarmahomedabbas.immichtv.data.immich

import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.domain.server.ServerAddress

/**
 * `<server>/api/assets/<id>/thumbnail?size=preview`: the preview-size image every grid, strip and
 * the slideshow loads (never the original: the Chromecast has about 2 GB of RAM). The key goes
 * in the `x-api-key` header, never here.
 */
internal fun previewUrl(server: ServerAddress, photo: PhotoId): String =
    apiUrl(server, "assets").newBuilder()
        .addPathSegment(photo.value)
        .addPathSegment("thumbnail")
        .addQueryParameter("size", "preview")
        .build()
        .toString()

/** The memory and disk cache key for a photo's preview: the photo alone, so no key or server URL is in it. */
internal fun previewCacheKey(photo: PhotoId): String = "preview:${photo.value}"
