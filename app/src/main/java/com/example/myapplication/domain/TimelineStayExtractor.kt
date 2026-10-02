package com.example.myapplication.domain

import com.example.myapplication.data.TimelineVisit
import com.example.myapplication.model.StayRecord
import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.math.roundToInt

/**
 * A continuous stay in one restricted region, merged from consecutive Timeline visits there.
 * [start] and [end] are local times at the place, which decide the stay's dates - and so whether
 * it spanned a night.
 */
data class TimelineStay(
    val regionName: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val visitCount: Int,
    /** A place from the stay, for display: the first visit with an address or name. */
    val samplePlace: String?
) {
    val nights: Long get() = Duration.between(start.toLocalDate().atStartOfDay(), end.toLocalDate().atStartOfDay()).toDays()

    /**
     * The stay as 체류 이력. 일부 지역 countries are recorded as having visited the risk area: a
     * coordinate says which country, not whether the town was a risk area, so this errs toward
     * restricting.
     */
    fun toStayRecord(id: Long = 0): StayRecord = StayRecord(
        id = id,
        regionName = regionName,
        startDate = start.toLocalDate(),
        endDate = end.toLocalDate(),
        offshoreOnly = false,
        visitedRiskArea = true
    )
}

/**
 * Local time at the visit. Takeout's older files store UTC ("...Z") with no zone, so the offset is
 * estimated from the longitude (15° per hour) - close enough to tell which day it was everywhere
 * but near a time zone border. Exports that carry their own offset keep it.
 */
internal fun TimelineVisit.localTime(time: OffsetDateTime): LocalDateTime =
    if (time.offset == ZoneOffset.UTC) {
        time.withOffsetSameInstant(ZoneOffset.ofHours((lng / 15).roundToInt().coerceIn(-12, 14))).toLocalDateTime()
    } else {
        time.toLocalDateTime()
    }

/**
 * Groups Timeline visits into stays per restricted region. Visits in the same region merge into
 * one stay unless a visit somewhere else comes between them, or more than [maxGap] passes with no
 * visit at all - so a day trip, a trip home and a return the next day are two stays, not one
 * overnight stay. Visits outside every restricted region are dropped.
 */
fun extractStays(
    visits: List<TimelineVisit>,
    locate: (lat: Double, lng: Double) -> String?,
    maxGap: Duration = Duration.ofHours(12)
): List<TimelineStay> {
    val stays = mutableListOf<TimelineStay>()
    var current: TimelineStay? = null
    var currentEndInstant: OffsetDateTime? = null

    for (visit in visits.sortedBy { it.start.toInstant() }) {
        val region = locate(visit.lat, visit.lng)
        if (region == null) {
            current?.let(stays::add)
            current = null
            continue
        }
        val start = visit.localTime(visit.start)
        val end = visit.localTime(visit.end)
        val place = visit.placeName ?: visit.address
        val ongoing = current
        val lastEnd = currentEndInstant
        if (ongoing != null && lastEnd != null && ongoing.regionName == region &&
            Duration.between(lastEnd, visit.start) <= maxGap
        ) {
            current = ongoing.copy(
                end = maxOf(ongoing.end, end),
                visitCount = ongoing.visitCount + 1,
                samplePlace = ongoing.samplePlace ?: place
            )
            currentEndInstant = maxOf(lastEnd, visit.end, compareBy { it.toInstant() })
        } else {
            ongoing?.let(stays::add)
            current = TimelineStay(region, start, end, 1, place)
            currentEndInstant = visit.end
        }
    }
    current?.let(stays::add)
    return stays
}
