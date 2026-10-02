package com.example.myapplication.data

import android.content.Context
import com.example.myapplication.domain.CircleExclusion
import com.example.myapplication.domain.RegionLocator
import com.example.myapplication.domain.RegionShape
import com.google.gson.Gson
import com.google.gson.JsonParseException
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Built by `tools/build_region_shapes.py` from open boundary data. */
const val REGION_SHAPES_ASSET = "restricted_region_shapes.json"

class RegionShapeDataException(message: String, cause: Throwable? = null) : Exception(message, cause)

private class RegionShapesJson(
    val regions: List<RegionJson>? = null,
    /** Rings of every country that isn't a restricted region. */
    val unrestrictedLand: List<DoubleArray>? = null
)

private class RegionJson(
    val name: String? = null,
    val rings: List<DoubleArray>? = null,
    /** Each `[lat, lng, radiusKm]`. */
    val exclusions: List<DoubleArray>? = null
)

/** Parses the bundled boundaries. Kept free of Android types so JVM unit tests can use it directly. */
fun parseRegionShapes(input: InputStream): RegionLocator {
    val raw = try {
        Gson().fromJson(input.reader(Charsets.UTF_8), RegionShapesJson::class.java)
    } catch (e: JsonParseException) {
        throw RegionShapeDataException("$REGION_SHAPES_ASSET is not valid JSON", e)
    }
    val shapes = raw?.regions.orEmpty().mapNotNull { region ->
        val name = region.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val rings = region.rings.orEmpty().filter { it.size >= 6 && it.size % 2 == 0 }
        if (rings.isEmpty()) return@mapNotNull null
        val exclusions = region.exclusions.orEmpty().filter { it.size == 3 }.map { CircleExclusion(it[0], it[1], it[2]) }
        RegionShape(name, rings, exclusions)
    }
    if (shapes.isEmpty()) throw RegionShapeDataException("$REGION_SHAPES_ASSET contained no regions")
    val land = raw?.unrestrictedLand.orEmpty().filter { it.size >= 6 && it.size % 2 == 0 }
        .takeIf { it.isNotEmpty() }
        ?.let { RegionShape("", it) }
    return RegionLocator(shapes, land)
}

/** Reads and parses the bundled boundaries, caching them for the process lifetime. */
class RegionShapeRepository(context: Context) {
    private val appContext = context.applicationContext

    @Volatile
    private var cached: RegionLocator? = null

    suspend fun loadLocator(): RegionLocator = withContext(Dispatchers.IO) {
        cached ?: try {
            appContext.assets.open(REGION_SHAPES_ASSET).use(::parseRegionShapes)
        } catch (e: IOException) {
            throw RegionShapeDataException("Could not read asset $REGION_SHAPES_ASSET", e)
        }.also { cached = it }
    }
}
