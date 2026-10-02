package com.example.myapplication.domain

import com.example.myapplication.data.TimelineVisit
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineStayExtractorTest {

    // Paju is any point north of 37.7; everything else is unrestricted.
    private val locate = { lat: Double, _: Double -> if (lat > 37.7) "경기 파주시" else null }

    private fun visit(lat: Double, start: String, end: String) =
        TimelineVisit(lat, 126.78, OffsetDateTime.parse(start), OffsetDateTime.parse(end))

    @Test
    fun `consecutive visits in a region merge into one overnight stay`() {
        val stays = extractStays(
            listOf(
                visit(37.76, "2024-07-01T14:00+09:00", "2024-07-01T18:00+09:00"),
                visit(37.75, "2024-07-01T19:00+09:00", "2024-07-02T08:00+09:00"),
                visit(37.76, "2024-07-02T09:00+09:00", "2024-07-02T11:00+09:00")
            ),
            locate
        )
        val stay = stays.single()
        assertEquals(LocalDateTime.of(2024, 7, 1, 14, 0), stay.start)
        assertEquals(LocalDateTime.of(2024, 7, 2, 11, 0), stay.end)
        assertEquals(3, stay.visitCount)
        assertEquals(1, stay.nights)
    }

    @Test
    fun `a visit elsewhere splits two day trips`() {
        val stays = extractStays(
            listOf(
                visit(37.76, "2024-07-01T10:00+09:00", "2024-07-01T17:00+09:00"),
                visit(37.56, "2024-07-01T18:00+09:00", "2024-07-02T08:00+09:00"), // home in Seoul
                visit(37.76, "2024-07-02T10:00+09:00", "2024-07-02T17:00+09:00")
            ),
            locate
        )
        assertEquals(2, stays.size)
        stays.forEach { assertEquals(0, it.nights) }
    }

    @Test
    fun `a long gap with no visits splits stays`() {
        val stays = extractStays(
            listOf(
                visit(37.76, "2024-07-01T10:00+09:00", "2024-07-01T17:00+09:00"),
                visit(37.76, "2024-07-05T10:00+09:00", "2024-07-05T17:00+09:00")
            ),
            locate
        )
        assertEquals(2, stays.size)
    }

    @Test
    fun `UTC timestamps are read in the place's local time`() {
        // 2024-07-01T16:00Z is 2024-07-02 01:00 in Korea: the stay ran past midnight there.
        val stay = extractStays(listOf(visit(37.76, "2024-07-01T05:00:00Z", "2024-07-01T16:00:00Z")), locate).single()
        assertEquals(LocalDate.of(2024, 7, 1), stay.toStayRecord().startDate)
        assertEquals(LocalDate.of(2024, 7, 2), stay.toStayRecord().endDate)
    }
}
