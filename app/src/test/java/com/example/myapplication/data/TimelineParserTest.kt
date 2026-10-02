package com.example.myapplication.data

import java.io.ByteArrayOutputStream
import java.time.OffsetDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineParserTest {

    private fun parse(json: String) = parseTimeline(json.byteInputStream())

    @Test
    fun `reads placeVisit from the Takeout Semantic Location History format`() {
        val result = parse(
            """
            {"timelineObjects": [
              {"activitySegment": {"distance": 1200}},
              {"placeVisit": {
                "location": {"latitudeE7": 377599000, "longitudeE7": 1267802000, "address": "경기도 파주시 시청로 50", "name": "파주시청"},
                "duration": {"startTimestamp": "2024-07-01T09:00:00Z", "endTimestamp": "2024-07-02T01:30:00.123Z"}
              }},
              {"placeVisit": {
                "location": {"latitudeE7": 375665000, "longitudeE7": 1269780000},
                "duration": {"startTimestampMs": "1719882000000", "endTimestampMs": "1719885600000"}
              }}
            ]}
            """
        )
        assertEquals(2, result.visits.size)
        val paju = result.visits[0]
        assertEquals(37.7599, paju.lat, 1e-9)
        assertEquals(126.7802, paju.lng, 1e-9)
        assertEquals("경기도 파주시 시청로 50", paju.address)
        assertEquals("파주시청", paju.placeName)
        assertEquals(OffsetDateTime.parse("2024-07-01T09:00:00Z"), paju.start)
        assertEquals(1719882000000, result.visits[1].start.toInstant().toEpochMilli())
    }

    @Test
    fun `reads visits from the Android on-device export`() {
        val result = parse(
            """
            {"semanticSegments": [
              {"startTime": "2024-07-01T18:00:00.000+09:00", "endTime": "2024-07-02T09:00:00.000+09:00",
               "visit": {"hierarchyLevel": 0, "probability": 0.9,
                 "topCandidate": {"placeId": "x", "semanticType": "UNKNOWN", "placeLocation": {"latLng": "37.7599°, 126.7802°"}}}},
              {"startTime": "2024-07-02T09:00:00.000+09:00", "endTime": "2024-07-02T10:00:00.000+09:00",
               "activity": {"start": {"latLng": "37.7°, 126.7°"}}}
            ],
            "rawSignals": [{"position": {"LatLng": "37.7°, 126.7°"}}],
            "userLocationProfile": {}}
            """
        )
        assertEquals(1, result.visits.size)
        assertEquals(126.7802, result.visits.single().lng, 1e-9)
        assertEquals(OffsetDateTime.parse("2024-07-01T18:00:00+09:00"), result.visits.single().start)
    }

    @Test
    fun `reads visits from the iOS export`() {
        val result = parse(
            """
            [{"startTime": "2024-03-01T10:00:00.000+07:00", "endTime": "2024-03-05T10:00:00.000+07:00",
              "visit": {"hierarchyLevel": "0", "topCandidate": {"probability": "0.8", "placeLocation": "geo:13.756300,100.501800"}}}]
            """
        )
        assertEquals(13.7563, result.visits.single().lat, 1e-9)
    }

    @Test
    fun `a visit without a location or times is counted as skipped`() {
        val result = parse("""{"timelineObjects": [{"placeVisit": {"location": {}, "duration": {}}}]}""")
        assertTrue(result.visits.isEmpty())
        assertEquals(1, result.skipped)
    }

    @Test
    fun `raw location history is rejected with an explanation`() {
        val error = assertThrows(TimelineFormatException::class.java) { parse("""{"locations": [{"latitudeE7": 1}]}""") }
        assertTrue(error.message!!.contains("Records.json"))
    }

    @Test
    fun `malformed JSON is a format error`() {
        assertThrows(TimelineFormatException::class.java) { parse("""{"timelineObjects": [ {""") }
    }

    @Test
    fun `a Takeout zip is read entry by entry, skipping non-timeline JSON`() {
        val bytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("Takeout/위치 기록/Semantic Location History/2024/2024_JULY.json"))
                zip.write(
                    """{"timelineObjects":[{"placeVisit":{"location":{"latitudeE7":377599000,"longitudeE7":1267802000},
                    "duration":{"startTimestamp":"2024-07-01T09:00:00Z","endTimestamp":"2024-07-01T12:00:00Z"}}}]}""".toByteArray()
                )
                zip.putNextEntry(ZipEntry("Takeout/위치 기록/Settings.json"))
                zip.write("""{"deviceSettings": []}""".toByteArray())
                zip.putNextEntry(ZipEntry("Takeout/archive_browser.html"))
                zip.write("<html></html>".toByteArray())
            }
        }.toByteArray()

        val result = readTimelineFile("takeout.zip") { bytes.inputStream() }

        assertEquals(1, result.visits.size)
        assertEquals(1, result.filesRead)
        assertEquals(listOf("Settings.json"), result.failures.map { it.first })
    }
}
