package io.github.yasarmahomedabbas.immichtv.data.immich

import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.Place
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.Slide
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import kotlin.math.roundToLong

/** `TimeBucketsResponseDto`: a month (`timeBucket`, its first day) and how many items it has. */
@Serializable
internal data class BucketDto(val timeBucket: String, val count: Int)

/**
 * The columns of the columnar `TimeBucketAssetResponseDto` the app reads. Beyond `id` and `isImage`,
 * a column that is absent, or an item in it that is null, reads as unknown for that item: it costs a
 * slide its date or place, and can't fail the months the grids read with this too.
 */
@Serializable
internal data class BucketAssetsDto(
    val id: List<String>,
    val isImage: List<Boolean>,
    val ratio: List<Double?> = emptyList(),
    val city: List<String?> = emptyList(),
    val country: List<String?> = emptyList(),
    val fileCreatedAt: List<String?> = emptyList(),
    val localOffsetHours: List<Double?> = emptyList(),
    val localDateTime: List<String?> = emptyList(),
) {
    fun toPhotos(): List<Photo> = id.zip(isImage) { id, image -> Photo(PhotoId(id), isVideo = !image) }

    fun toSlides(): List<Slide> = toPhotos().mapIndexed { i, photo ->
        Slide(
            photo = photo,
            portrait = ratio.getOrNull(i)?.let { it < 1 } ?: false,
            takenOn = takenOn(localDateTime.getOrNull(i), fileCreatedAt.getOrNull(i), localOffsetHours.getOrNull(i)),
            place = place(city.getOrNull(i), country.getOrNull(i)),
        )
    }
}

/**
 * The calendar day where the photo was taken. v1.133 and v1.134 send it as `localDateTime`; from
 * v1.135 that column is gone, and it is `fileCreatedAt` (UTC, with or without a `Z`) moved by
 * `localOffsetHours`, which may be fractional. One that can't be read is unknown, never a failed show.
 */
private fun takenOn(localDateTime: String?, fileCreatedAt: String?, offsetHours: Double?): LocalDate? = try {
    when {
        localDateTime != null -> LocalDate.parse(localDateTime.take(10))
        fileCreatedAt != null -> LocalDateTime.parse(fileCreatedAt.removeSuffix("Z"))
            .plusSeconds(((offsetHours ?: 0.0) * SECONDS_PER_HOUR).roundToLong())
            .toLocalDate()
        else -> null
    }
} catch (_: DateTimeParseException) {
    null
}

private const val SECONDS_PER_HOUR = 3600

internal fun place(city: String?, country: String?): Place? {
    val knownCity = city?.takeIf { it.isNotBlank() }
    val knownCountry = country?.takeIf { it.isNotBlank() }
    return if (knownCity == null && knownCountry == null) null else Place(knownCity, knownCountry)
}
