package com.example.myapplication.data

import android.content.Context
import com.example.myapplication.model.BloodCenter
import com.example.myapplication.model.CenterMachines
import com.google.gson.Gson
import com.google.gson.JsonParseException
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Built by `tools/build_blood_centers.py` from 대한적십자사's 헌혈의 집 CSV and the 성분헌혈 machines CSV. */
const val BLOOD_CENTERS_ASSET = "blood_centers.json"

class BloodCenterDataException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The bundled list.
 *
 * @param asOf The date of the CSV it was built from, as "2026.07.01", when the file name has one.
 */
data class BloodCenterList(val centers: List<BloodCenter>, val asOf: String?)

private class BloodCentersJson(val source: String? = null, val centers: List<BloodCenterJson?>? = null)

/** All-nullable for the same reason as ProhibitedDrugJson: Gson bypasses Kotlin's null checks. */
private class BloodCenterJson(
    val bloodBank: String? = null,
    val name: String? = null,
    val type: String? = null,
    val address: String? = null,
    val phone: String? = null,
    val machines: MachinesJson? = null
)

private class MachinesJson(val plasma: Map<String, Int?>? = null, val platelet: Map<String, Int?>? = null)

/** Models the center has, in the order given; zero, missing and unnamed entries dropped. */
private fun Map<String, Int?>?.models(): Map<String, Int> = buildMap {
    this@models.orEmpty().forEach { (model, count) ->
        val name = model.trim()
        if (name.isNotEmpty() && count != null && count > 0) put(name, count)
    }
}

/** "대한적십자사_헌혈의집_정보_20260701.csv" -> 2026, 07, 01. */
private val SOURCE_DATE = Regex("(20\\d{2})(\\d{2})(\\d{2})")

/**
 * Parses the bundled list. Kept free of Android types so JVM unit tests can use it directly.
 * Entries without a name or address are skipped.
 */
fun parseBloodCenters(input: InputStream): BloodCenterList {
    val raw = try {
        Gson().fromJson(input.reader(Charsets.UTF_8), BloodCentersJson::class.java)
    } catch (e: JsonParseException) {
        throw BloodCenterDataException("$BLOOD_CENTERS_ASSET is not valid JSON", e)
    }
    val centers = raw?.centers.orEmpty().filterNotNull().mapNotNull { json ->
        val name = json.name?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val address = json.address?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        BloodCenter(
            bloodBank = json.bloodBank?.trim().orEmpty(),
            name = name,
            type = json.type?.trim().orEmpty(),
            address = address,
            phone = json.phone?.trim().orEmpty(),
            machines = json.machines?.let { CenterMachines(plasma = it.plasma.models(), platelet = it.platelet.models()) }
        )
    }
    if (centers.isEmpty()) throw BloodCenterDataException("$BLOOD_CENTERS_ASSET contained no centers")
    val asOf = raw?.source?.let { SOURCE_DATE.find(it) }?.destructured?.let { (y, m, d) -> "$y.$m.$d" }
    return BloodCenterList(centers, asOf)
}

/** Reads and parses the bundled list, caching it for the process lifetime. Entirely offline. */
class BloodCenterRepository(context: Context) {
    private val appContext = context.applicationContext

    @Volatile
    private var cached: BloodCenterList? = null

    suspend fun load(): BloodCenterList = withContext(Dispatchers.IO) {
        cached ?: try {
            appContext.assets.open(BLOOD_CENTERS_ASSET).use(::parseBloodCenters)
        } catch (e: IOException) {
            throw BloodCenterDataException("Could not read asset $BLOOD_CENTERS_ASSET", e)
        }.also { cached = it }
    }
}
