package com.example.myapplication.domain

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** A circle carved out of a region - e.g. 백두산, which 북한's restriction excludes. */
data class CircleExclusion(val lat: Double, val lng: Double, val radiusKm: Double)

/**
 * The boundary of one [com.example.myapplication.model.StayRegion], as rings of
 * `[lng, lat, lng, lat, ...]`. A point is inside when it falls inside an odd number of rings, which
 * handles both multi-part regions (islands) and holes without telling the two apart.
 */
class RegionShape(val name: String, private val rings: List<DoubleArray>, private val exclusions: List<CircleExclusion> = emptyList()) {

    private val minLng = rings.minOf { ring -> ring.filterIndexed { i, _ -> i % 2 == 0 }.min() }
    private val maxLng = rings.maxOf { ring -> ring.filterIndexed { i, _ -> i % 2 == 0 }.max() }
    private val minLat = rings.minOf { ring -> ring.filterIndexed { i, _ -> i % 2 == 1 }.min() }
    private val maxLat = rings.maxOf { ring -> ring.filterIndexed { i, _ -> i % 2 == 1 }.max() }

    fun contains(lat: Double, lng: Double): Boolean {
        if (lat < minLat || lat > maxLat || lng < minLng || lng > maxLng) return false
        if (isExcluded(lat, lng)) return false
        return rings.count { crosses(it, lat, lng) } % 2 == 1
    }

    private fun isExcluded(lat: Double, lng: Double) = exclusions.any { distanceKm(lat, lng, it.lat, it.lng) <= it.radiusKm }

    /**
     * Distance from the point to this region's nearest edge in km, or null when that's certainly
     * more than [withinKm] (judged from the bounding box first, so far-away regions are cheap).
     */
    fun distanceToEdgeKm(lat: Double, lng: Double, withinKm: Double): Double? {
        val marginLat = withinKm / KM_PER_DEGREE
        val marginLng = marginLat / cos(Math.toRadians(lat)).coerceAtLeast(0.01)
        if (lat < minLat - marginLat || lat > maxLat + marginLat || lng < minLng - marginLng || lng > maxLng + marginLng) return null
        if (isExcluded(lat, lng)) return null
        val nearest = rings.minOf { ring -> ringDistanceKm(ring, lat, lng) }
        return nearest.takeIf { it <= withinKm }
    }

    /** Point-to-segment distance on a local flat projection - accurate at the few-km scale it's used at. */
    private fun ringDistanceKm(ring: DoubleArray, lat: Double, lng: Double): Double {
        val kmPerLng = KM_PER_DEGREE * cos(Math.toRadians(lat))
        var best = Double.MAX_VALUE
        val points = ring.size / 2
        var j = points - 1
        for (i in 0 until points) {
            val ax = (ring[2 * j] - lng) * kmPerLng
            val ay = (ring[2 * j + 1] - lat) * KM_PER_DEGREE
            val bx = (ring[2 * i] - lng) * kmPerLng
            val by = (ring[2 * i + 1] - lat) * KM_PER_DEGREE
            val dx = bx - ax
            val dy = by - ay
            val lengthSquared = dx * dx + dy * dy
            val t = if (lengthSquared == 0.0) 0.0 else (-(ax * dx + ay * dy) / lengthSquared).coerceIn(0.0, 1.0)
            val px = ax + t * dx
            val py = ay + t * dy
            best = minOf(best, sqrt(px * px + py * py))
            j = i
        }
        return best
    }

    /** Ray casting: whether a ray east from the point crosses [ring] an odd number of times. */
    private fun crosses(ring: DoubleArray, lat: Double, lng: Double): Boolean {
        var inside = false
        val points = ring.size / 2
        var j = points - 1
        for (i in 0 until points) {
            val xi = ring[2 * i]
            val yi = ring[2 * i + 1]
            val xj = ring[2 * j]
            val yj = ring[2 * j + 1]
            if ((yi > lat) != (yj > lat) && lng < (xj - xi) * (lat - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }
}

private const val EARTH_RADIUS_KM = 6371.0
private const val KM_PER_DEGREE = 111.32

private fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
    return 2 * EARTH_RADIUS_KM * asin(sqrt(a))
}

/**
 * Finds which restricted region a coordinate is in, entirely from bundled boundaries - no geocoding
 * service, so a location never leaves the device. Boundaries are simplified (about 1km for
 * countries, 50m for 국내 시·군), so a point right on a border can land on either side.
 *
 * The 1:10m coastlines cut through some seaside towns (St Helier, beach resorts), so a point that's
 * in no region *and on nobody else's land* - i.e. apparently at sea - is put in the restricted
 * region whose coast is within [coastSnapKm]. A point on [unrestrictedLand] is never snapped, so a
 * town across a land border from a restricted region stays unrestricted.
 */
class RegionLocator(
    private val shapes: List<RegionShape>,
    private val unrestrictedLand: RegionShape? = null,
    private val coastSnapKm: Double = 3.0
) {

    val regionNames: Set<String> get() = shapes.mapTo(mutableSetOf()) { it.name }

    /** The region containing the point, or null when it's in none of them. */
    fun locate(lat: Double, lng: Double): String? {
        shapes.firstOrNull { it.contains(lat, lng) }?.let { return it.name }
        if (unrestrictedLand == null || unrestrictedLand.contains(lat, lng)) return null
        return shapes
            .mapNotNull { shape -> shape.distanceToEdgeKm(lat, lng, coastSnapKm)?.let { shape to it } }
            .minByOrNull { it.second }
            ?.first
            ?.name
    }
}
