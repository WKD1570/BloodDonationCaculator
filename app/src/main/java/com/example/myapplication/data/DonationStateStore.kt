package com.example.myapplication.data

import android.content.Context
import androidx.core.content.edit
import com.example.myapplication.model.DonationRecord
import com.example.myapplication.model.DonationType
import com.example.myapplication.model.DonorProfile
import com.example.myapplication.model.Sex
import com.example.myapplication.model.ThemeMode
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.LocalDate

private const val PREFS_NAME = "donation_state"
private const val KEY_PROFILE_WEIGHT = "profile_weight"
private const val KEY_PROFILE_HEIGHT = "profile_height"
private const val KEY_PROFILE_SEX = "profile_sex"
private const val KEY_PROFILE_NAME = "profile_name"
private const val KEY_PROFILE_BIRTHDATE = "profile_birthdate"
private const val KEY_PROFILE_DONATED_60_64 = "profile_donated_60_64"
private const val KEY_DONATION_RECORDS = "donation_records"
private const val KEY_THEME_MODE = "theme_mode"

/**
 * Reads the saved theme preference, falling back to [ThemeMode.SYSTEM] both when nothing has been
 * chosen yet and when the stored value no longer names a known mode.
 */
fun loadThemeMode(context: Context): ThemeMode {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val stored = prefs.getString(KEY_THEME_MODE, null) ?: return ThemeMode.SYSTEM
    return runCatching { ThemeMode.valueOf(stored) }.getOrDefault(ThemeMode.SYSTEM)
}

fun saveThemeMode(context: Context, mode: ThemeMode) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putString(KEY_THEME_MODE, mode.name) }
}

fun loadDonorProfile(context: Context): DonorProfile {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return DonorProfile(
        weightKg = prefs.getFloat(KEY_PROFILE_WEIGHT, -1f).takeIf { it >= 0f }?.toDouble(),
        heightCm = prefs.getFloat(KEY_PROFILE_HEIGHT, -1f).takeIf { it >= 0f }?.toDouble(),
        sex = prefs.getString(KEY_PROFILE_SEX, null)?.let { runCatching { Sex.valueOf(it) }.getOrNull() },
        name = prefs.getString(KEY_PROFILE_NAME, null)?.takeIf { it.isNotBlank() },
        birthDate = prefs.getString(KEY_PROFILE_BIRTHDATE, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        donatedAge60To64 = prefs.getBoolean(KEY_PROFILE_DONATED_60_64, false)
    )
}

fun saveDonorProfile(context: Context, profile: DonorProfile) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit {
        putFloat(KEY_PROFILE_WEIGHT, profile.weightKg?.toFloat() ?: -1f)
        putFloat(KEY_PROFILE_HEIGHT, profile.heightCm?.toFloat() ?: -1f)
        if (profile.sex != null) putString(KEY_PROFILE_SEX, profile.sex.name) else remove(KEY_PROFILE_SEX)
        if (!profile.name.isNullOrBlank()) putString(KEY_PROFILE_NAME, profile.name) else remove(KEY_PROFILE_NAME)
        if (profile.birthDate != null) putString(KEY_PROFILE_BIRTHDATE, profile.birthDate.toString()) else remove(KEY_PROFILE_BIRTHDATE)
        putBoolean(KEY_PROFILE_DONATED_60_64, profile.donatedAge60To64)
    }
}

private fun urlEncode(value: String?): String = if (value.isNullOrEmpty()) "" else URLEncoder.encode(value, "UTF-8")
private fun urlDecode(token: String): String? = token.takeIf { it.isNotEmpty() }?.let { URLDecoder.decode(it, "UTF-8") }

/**
 * Loads saved donation records. The first time this runs after upgrading from the old
 * count-per-type format, it migrates any dated entries into records (using [profile] for the
 * name/birth date/sex every record now requires) and persists the result under
 * [KEY_DONATION_RECORDS], so this migration only ever happens once.
 */
fun loadDonationRecords(context: Context, profile: DonorProfile): List<DonationRecord> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val raw = prefs.getString(KEY_DONATION_RECORDS, null)
    if (raw != null) return decodeRecords(raw)

    val migrated = migrateLegacyState(prefs, profile)
    saveDonationRecords(context, migrated)
    return migrated
}

fun saveDonationRecords(context: Context, records: List<DonationRecord>) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit {
        putString(KEY_DONATION_RECORDS, encodeRecords(records))
    }
}

private fun encodeRecords(records: List<DonationRecord>): String =
    records.joinToString("\n") { r ->
        listOf(
            r.id.toString(),
            r.type.name,
            r.birthDate?.toString() ?: "",
            r.date.toString(),
            urlEncode(r.certNumber),
            urlEncode(r.name),
            r.sex?.name ?: "",
            urlEncode(r.centerName),
            r.donatedVolumeMl?.toString() ?: ""
        ).joinToString("|")
    }

/**
 * Decodes one record per line. [donatedVolumeMl] (the 9th field) was added after this format
 * shipped, so lines saved before that only have 8 fields — those decode with donatedVolumeMl
 * left null rather than being rejected, via [List.getOrNull].
 */
private fun decodeRecords(raw: String): List<DonationRecord> =
    raw.split("\n").filter { it.isNotBlank() }.mapNotNull { line ->
        val p = line.split("|", limit = 9)
        if (p.size < 8) return@mapNotNull null
        val id = p[0].toLongOrNull() ?: return@mapNotNull null
        val type = runCatching { DonationType.valueOf(p[1]) }.getOrNull() ?: return@mapNotNull null
        val birthDate = p[2].takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val date = runCatching { LocalDate.parse(p[3]) }.getOrNull() ?: return@mapNotNull null
        DonationRecord(
            id = id,
            type = type,
            birthDate = birthDate,
            date = date,
            certNumber = urlDecode(p[4]),
            name = urlDecode(p[5]),
            sex = p[6].takeIf { it.isNotEmpty() }?.let { runCatching { Sex.valueOf(it) }.getOrNull() },
            centerName = urlDecode(p[7]),
            donatedVolumeMl = p.getOrNull(8)?.takeIf { it.isNotBlank() }?.toIntOrNull()
        )
    }

private data class LegacyTypeState(
    val count: Int,
    val dates: List<LocalDate?>,
    val certNumbers: List<String?>,
    val centerNames: List<String?>
)

private fun legacyKeyFor(type: DonationType) = "state_${type.name}"

private fun decodeLegacyTypeState(raw: String): LegacyTypeState {
    val parts = raw.split("|", limit = 4)
    val count = parts.getOrElse(0) { "0" }.toIntOrNull() ?: 0
    val datesPart = parts.getOrElse(1) { "" }
    val certsPart = parts.getOrElse(2) { "" }
    val centersPart = parts.getOrElse(3) { "" }

    fun splitOrEmpty(s: String) = if (s.isEmpty()) emptyList() else s.split(",")

    val dates = splitOrEmpty(datesPart).map { token ->
        token.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }
    val certNumbers = splitOrEmpty(certsPart).map { urlDecode(it) }
    val centerNames = splitOrEmpty(centersPart).map { urlDecode(it) }
    return LegacyTypeState(count, dates, certNumbers, centerNames)
}

private fun migrateLegacyState(
    prefs: android.content.SharedPreferences,
    profile: DonorProfile
): List<DonationRecord> {
    var nextId = System.currentTimeMillis()
    val records = mutableListOf<DonationRecord>()
    DonationType.entries.forEach { type ->
        val raw = prefs.getString(legacyKeyFor(type), null) ?: return@forEach
        val legacy = decodeLegacyTypeState(raw)
        legacy.dates.forEachIndexed { index, date ->
            if (date == null) return@forEachIndexed
            records.add(
                DonationRecord(
                    id = nextId++,
                    type = type,
                    birthDate = profile.birthDate,
                    date = date,
                    certNumber = legacy.certNumbers.getOrNull(index),
                    name = profile.name,
                    sex = profile.sex,
                    centerName = legacy.centerNames.getOrNull(index)
                )
            )
        }
    }
    return records
}
