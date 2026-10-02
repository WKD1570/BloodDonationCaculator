package com.example.myapplication.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.util.zip.ZipInputStream

/** One place visit read from a Google Maps Timeline export. */
data class TimelineVisit(
    val lat: Double,
    val lng: Double,
    val start: OffsetDateTime,
    val end: OffsetDateTime,
    /** Only the older Takeout format carries these. */
    val address: String? = null,
    val placeName: String? = null
)

/** Raised for a file that isn't a Timeline export this parser understands. */
class TimelineFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Visits read from one file, plus how many visit entries were unusable (no location or times). */
data class TimelineParseResult(val visits: List<TimelineVisit>, val skipped: Int)

private fun JsonObject.obj(name: String): JsonObject? = get(name)?.takeIf { it.isJsonObject }?.asJsonObject

private fun JsonObject.string(name: String): String? =
    get(name)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }

private fun JsonObject.double(name: String): Double? =
    get(name)?.takeIf { it.isJsonPrimitive }?.asString?.toDoubleOrNull()

/** ISO-8601 with an offset or `Z` ("2024-01-01T09:00:00.000+09:00"), or epoch milliseconds. */
internal fun parseTimelineTime(value: String?): OffsetDateTime? {
    if (value.isNullOrBlank()) return null
    value.toLongOrNull()?.let { return Instant.ofEpochMilli(it).atOffset(ZoneOffset.UTC) }
    return try {
        OffsetDateTime.parse(value)
    } catch (e: DateTimeParseException) {
        null
    }
}

private val LAT_LNG_PATTERN = Regex("""(-?\d+(?:\.\d+)?)°?\s*,\s*(-?\d+(?:\.\d+)?)°?""")

/** "37.7599°, 126.7802°" (Android export) or "geo:37.7599,126.7802" (iOS export). */
internal fun parseLatLng(value: String?): Pair<Double, Double>? {
    val match = value?.let { LAT_LNG_PATTERN.find(it) } ?: return null
    val lat = match.groupValues[1].toDouble()
    val lng = match.groupValues[2].toDouble()
    return if (lat in -90.0..90.0 && lng in -180.0..180.0) lat to lng else null
}

/** Takeout "Semantic Location History": `timelineObjects[].placeVisit`. */
private fun legacyVisit(placeVisit: JsonObject): TimelineVisit? {
    val location = placeVisit.obj("location")
    val lat = (location?.double("latitudeE7") ?: placeVisit.double("centerLatE7"))?.div(1e7)
    val lng = (location?.double("longitudeE7") ?: placeVisit.double("centerLngE7"))?.div(1e7)
    val duration = placeVisit.obj("duration") ?: return null
    val start = parseTimelineTime(duration.string("startTimestamp") ?: duration.string("startTimestampMs"))
    val end = parseTimelineTime(duration.string("endTimestamp") ?: duration.string("endTimestampMs"))
    if (lat == null || lng == null || start == null || end == null || end.isBefore(start)) return null
    return TimelineVisit(lat, lng, start, end, location?.string("address"), location?.string("name"))
}

/** The on-device Timeline export: `semanticSegments[]` (Android) or a top-level array (iOS). */
private fun segmentVisit(segment: JsonObject): TimelineVisit? {
    val placeLocation = segment.obj("visit")?.obj("topCandidate")?.get("placeLocation") ?: return null
    val latLng = when {
        placeLocation.isJsonObject -> parseLatLng(placeLocation.asJsonObject.string("latLng"))
        placeLocation.isJsonPrimitive -> parseLatLng(placeLocation.asString)
        else -> null
    } ?: return null
    val start = parseTimelineTime(segment.string("startTime")) ?: return null
    val end = parseTimelineTime(segment.string("endTime")) ?: return null
    if (end.isBefore(start)) return null
    return TimelineVisit(latLng.first, latLng.second, start, end)
}

private class VisitCollector {
    val visits = mutableListOf<TimelineVisit>()
    var skipped = 0

    fun add(entry: JsonObject, read: (JsonObject) -> TimelineVisit?) {
        val visit = read(entry)
        if (visit != null) visits += visit else skipped++
    }
}

/**
 * Reads [reader]'s current array one element at a time, so a multi-hundred-MB export never has to
 * be held in memory as a whole tree.
 */
private fun JsonReader.forEachObject(action: (JsonObject) -> Unit) {
    beginArray()
    while (hasNext()) {
        val element: JsonElement = JsonParser.parseReader(this)
        if (element.isJsonObject) action(element.asJsonObject)
    }
    endArray()
}

/**
 * Parses one Google Maps Timeline export, streaming it. Understands the Takeout "Semantic Location
 * History" monthly files (`timelineObjects[].placeVisit`) and the on-device exports that replaced
 * them (Android `semanticSegments[]`, iOS top-level array). Other segments - travel between places,
 * raw signals - are skipped; only visits say where the user stayed.
 */
fun parseTimeline(input: InputStream): TimelineParseResult {
    val collector = VisitCollector()
    try {
        val reader = JsonReader(input.reader(Charsets.UTF_8))
        when (reader.peek()) {
            JsonToken.BEGIN_ARRAY -> reader.forEachObject { segment ->
                if (segment.has("visit")) collector.add(segment, ::segmentVisit)
            }

            JsonToken.BEGIN_OBJECT -> {
                var recognized = false
                var rawLocations = false
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "timelineObjects" -> {
                            recognized = true
                            reader.forEachObject { entry ->
                                entry.obj("placeVisit")?.let { placeVisit -> collector.add(placeVisit, ::legacyVisit) }
                            }
                        }

                        "semanticSegments" -> {
                            recognized = true
                            reader.forEachObject { segment ->
                                if (segment.has("visit")) collector.add(segment, ::segmentVisit)
                            }
                        }

                        "locations" -> {
                            rawLocations = true
                            reader.skipValue()
                        }

                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                if (!recognized) {
                    throw TimelineFormatException(
                        if (rawLocations) "위치 기록(Records.json)에는 방문 장소 정보가 없어요. 타임라인 파일을 선택해 주세요."
                        else "Google 지도 타임라인 파일이 아니에요."
                    )
                }
            }

            else -> throw TimelineFormatException("Google 지도 타임라인 파일이 아니에요.")
        }
    } catch (e: JsonParseException) {
        throw TimelineFormatException("JSON 형식이 올바르지 않아요.", e)
    } catch (e: IllegalStateException) {
        throw TimelineFormatException("JSON 형식이 올바르지 않아요.", e)
    } catch (e: IOException) {
        throw TimelineFormatException("파일을 읽지 못했어요.", e)
    }
    return TimelineParseResult(collector.visits, collector.skipped)
}

/** Lets a parser read one zip entry without closing the whole archive. */
private class NonClosingInputStream(input: InputStream) : FilterInputStream(input) {
    override fun close() = Unit
}

/** Visits from every file, and the files that couldn't be read with why. */
data class TimelineReadResult(
    val visits: List<TimelineVisit>,
    val skippedVisits: Int,
    val filesRead: Int,
    val failures: List<Pair<String, String>>
)

/**
 * Reads a selected file: a single JSON export, or a Takeout `.zip`, from which every `.json` entry
 * is parsed and anything else ignored. [name] is only used for failure messages and to tell zips
 * apart; zips are also recognised by their content.
 */
fun readTimelineFile(name: String, open: () -> InputStream): TimelineReadResult {
    val visits = mutableListOf<TimelineVisit>()
    val failures = mutableListOf<Pair<String, String>>()
    var skipped = 0
    var filesRead = 0

    val isZip = open().use { stream ->
        val header = ByteArray(4)
        stream.read(header) == 4 && header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte()
    }
    if (isZip) {
        ZipInputStream(open()).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                if (entry.isDirectory || !entry.name.endsWith(".json", ignoreCase = true)) return@forEach
                try {
                    val result = parseTimeline(NonClosingInputStream(zip))
                    visits += result.visits
                    skipped += result.skipped
                    filesRead++
                } catch (e: TimelineFormatException) {
                    // A Takeout zip holds other JSON too (settings, Records.json); not an error by itself.
                    failures += entry.name.substringAfterLast('/') to (e.message ?: "")
                }
            }
        }
        if (filesRead == 0 && failures.isEmpty()) failures += name to "압축 파일 안에 JSON 파일이 없어요."
    } else {
        try {
            open().use { stream ->
                val result = parseTimeline(stream)
                visits += result.visits
                skipped += result.skipped
                filesRead++
            }
        } catch (e: TimelineFormatException) {
            failures += name to (e.message ?: "")
        }
    }
    return TimelineReadResult(visits, skipped, filesRead, failures)
}
