package io.github.yasarmahomedabbas.immichtv.data.immich

import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.domain.home.Memory
import io.github.yasarmahomedabbas.immichtv.domain.home.MemoryRepository
import io.github.yasarmahomedabbas.immichtv.domain.server.SessionStore
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.Slide
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import java.time.LocalDate

/**
 * "On this day" memories from Immich v1.133+: `GET /api/memories`. `for` is date-only because v3.3.5
 * answers 400 to a full timestamp, and `type` keeps out the birthdays newer servers also have. A key
 * needs `memory.read` from v3 on, or the call is refused.
 */
internal class ImmichMemoryRepository(client: OkHttpClient, sessions: SessionStore) : MemoryRepository {
    private val api = ImmichApi(client, sessions)

    override suspend fun memoriesOn(day: LocalDate): ServerResult<List<Memory>> = api.signedIn { session ->
        val query = listOf("for" to day.toString(), "type" to "on_this_day")
        when (val memories = api.get<List<MemoryDto>>(session, "memories", query)) {
            is ServerResult.Ok -> mapAll(memories.value) { it.toMemory() }
            is ServerResult.Failed -> memories
        }
    }
}

/** The part of `MemoryResponseDto` the app reads; `data` differs between versions, and isn't read. */
@Serializable
private data class MemoryDto(val memoryAt: String, val assets: List<MemoryAssetDto>) {
    fun toMemory() = Memory(calendarDay(memoryAt), assets.mapNotNull { it.toSlide() })
}

/**
 * The part of `AssetResponseDto` the app reads. `width` and `height` are absent in v1.133 and nullable
 * in v3. v3 filters a memory's assets to the timeline itself, v1.133 doesn't, so it is done here too.
 */
@Serializable
private data class MemoryAssetDto(
    val id: String,
    val type: String,
    val visibility: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val localDateTime: String? = null,
    val exifInfo: ExifDto? = null,
) {
    fun toSlide(): Slide? {
        if (visibility != null && visibility != "timeline") return null
        if (type != "IMAGE" && type != "VIDEO") return null
        return Slide(
            photo = Photo(PhotoId(id), isVideo = type == "VIDEO"),
            portrait = width != null && height != null && height > width,
            takenOn = takenOn(localDateTime, fileCreatedAt = null, offsetHours = null),
            place = place(exifInfo?.city, exifInfo?.country),
        )
    }
}

@Serializable
private data class ExifDto(val city: String? = null, val country: String? = null)
