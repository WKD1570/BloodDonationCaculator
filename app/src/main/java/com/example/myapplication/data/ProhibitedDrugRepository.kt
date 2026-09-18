package com.example.myapplication.data

import android.content.Context
import com.example.myapplication.model.RestrictedDrug
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val PROHIBITED_DRUGS_ASSET = "prohibited_drugs.json"

/** Raised when the bundled asset is missing or cannot be turned into a usable drug list. */
class ProhibitedDrugDataException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * Gson builds Kotlin objects through Unsafe rather than the constructor, so a field missing from
 * the JSON would leave a non-null `String` property holding null and only blow up later, far from
 * the parse. Deserializing into this all-nullable shape keeps that failure local: [toModel] is the
 * single place that decides what a malformed entry means.
 */
private data class ProhibitedDrugJson(
    val category: String? = null,
    val ingredientName: String? = null,
    val representativeBrands: String? = null,
    val restrictionDays: Int? = null,
    val restrictionText: String? = null
)

/**
 * Returns null for an entry that cannot be acted on - one with no ingredient name to search for,
 * or no restriction period to count from. Everything else is optional display text and defaults
 * to blank rather than dropping an otherwise valid row.
 */
private fun ProhibitedDrugJson.toModel(): RestrictedDrug? {
    val ingredient = ingredientName?.trim().orEmpty()
    if (ingredient.isEmpty()) return null
    val days = restrictionDays ?: return null
    return RestrictedDrug(
        category = category?.trim().orEmpty(),
        ingredientName = ingredient,
        representativeBrands = representativeBrands?.trim().orEmpty(),
        restrictionDays = days,
        restrictionText = restrictionText?.trim().orEmpty()
    )
}

private val gson = Gson()
private val listType = object : TypeToken<List<ProhibitedDrugJson>>() {}.type

/**
 * Parses the `prohibited_drugs.json` payload. Kept free of Android types so it can be exercised
 * directly from JVM unit tests. Malformed individual entries are skipped; a payload that is not a
 * JSON array, or that yields no usable entry at all, throws [ProhibitedDrugDataException].
 */
fun parseProhibitedDrugs(json: String): List<RestrictedDrug> {
    val raw: List<ProhibitedDrugJson?> = try {
        gson.fromJson(json, listType)
    } catch (e: JsonSyntaxException) {
        throw ProhibitedDrugDataException("$PROHIBITED_DRUGS_ASSET is not valid JSON", e)
    } ?: throw ProhibitedDrugDataException("$PROHIBITED_DRUGS_ASSET is empty")

    val drugs = raw.filterNotNull().mapNotNull { it.toModel() }
    if (drugs.isEmpty()) {
        throw ProhibitedDrugDataException("$PROHIBITED_DRUGS_ASSET contained no usable entries")
    }
    return drugs
}

/** Reads and parses the bundled drug list, caching the result for the process lifetime. */
class ProhibitedDrugRepository(
    context: Context,
    private val assetName: String = PROHIBITED_DRUGS_ASSET
) {
    // Hold the application context: the repository outlives the composable that created it.
    private val appContext = context.applicationContext

    @Volatile
    private var cached: List<RestrictedDrug>? = null

    suspend fun loadDrugs(): List<RestrictedDrug> = withContext(Dispatchers.IO) {
        cached ?: readAsset().let(::parseProhibitedDrugs).also { cached = it }
    }

    private fun readAsset(): String = try {
        appContext.assets.open(assetName).bufferedReader().use { it.readText() }
    } catch (e: IOException) {
        throw ProhibitedDrugDataException("Could not read asset $assetName", e)
    }
}
