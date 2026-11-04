package com.example.myapplication.data

import com.example.myapplication.model.DiseaseRecord
import com.example.myapplication.model.DonationRecord
import com.example.myapplication.model.DonationType
import com.example.myapplication.model.DonorProfile
import com.example.myapplication.model.MedicationRecord
import com.example.myapplication.model.PlannedDonation
import com.example.myapplication.model.Sex
import com.example.myapplication.model.StayRecord
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

/** Names the file as one of this app's backups, so a stray JSON file isn't taken for one. */
const val BACKUP_FORMAT = "bdcalculator-backup"

/** Bumped when a change would make an older app misread the file; older files keep reading. */
const val BACKUP_VERSION = 1

/** The message says what's wrong in words the donor can act on; it's shown as is. */
class BackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Everything the donor entered: 마이페이지, 헌혈 기록, 헌혈 계획, the 약·감염병·체류 이력 and the
 * starred 헌혈 장소. Bundled data isn't included, and nor are per-device settings such as the theme
 * and notifications.
 */
data class AppBackup(
    val profile: DonorProfile,
    val donations: List<DonationRecord>,
    val plan: List<PlannedDonation>,
    val medications: List<MedicationRecord>,
    val diseases: List<DiseaseRecord>,
    val stays: List<StayRecord>,
    val favoritePlaces: Set<String>
)

/**
 * A backup read from a file.
 *
 * @param exportedAt When it was made, if the file says.
 * @param skipped Entries left out because they couldn't be read, or repeated an id or a planned day.
 */
data class BackupFile(val backup: AppBackup, val exportedAt: LocalDateTime?, val skipped: Int)

/** "헌혈 기록 12건 · 헌혈 계획 2건 · …", leaving out what the backup has none of. */
fun backupSummary(backup: AppBackup): String =
    listOf(
        "헌혈 기록" to backup.donations.size,
        "헌혈 계획" to backup.plan.size,
        "약 복용" to backup.medications.size,
        "질환·시술·접종" to backup.diseases.size,
        "체류" to backup.stays.size
    ).filter { it.second > 0 }.map { (label, count) -> "$label ${count}건" }
        .plus(listOfNotNull(backup.favoritePlaces.size.takeIf { it > 0 }?.let { "즐겨찾기 ${it}곳" }))
        .ifEmpty { listOf("저장된 기록 없음") }
        .joinToString(" · ")

/** "헌혈기록_백업_20261103.json": sorts by date in a folder and says what it is. */
fun backupFileName(date: LocalDate): String =
    "헌혈기록_백업_%04d%02d%02d.json".format(date.year, date.monthValue, date.dayOfMonth)

// The file's shape. All-nullable for the same reason as BloodCenterJson: Gson bypasses Kotlin's
// null checks, so a hand-edited or truncated file must not be able to smuggle a null in.

private class BackupJson(
    val format: String? = null,
    val version: Int? = null,
    val exportedAt: String? = null,
    val profile: ProfileJson? = null,
    val donations: List<DonationJson?>? = null,
    val plan: List<PlanJson?>? = null,
    val medications: List<MedicationJson?>? = null,
    val diseases: List<DiseaseJson?>? = null,
    val stays: List<StayJson?>? = null,
    val favoritePlaces: List<String?>? = null
)

private class ProfileJson(
    val name: String? = null,
    val birthDate: String? = null,
    val sex: String? = null,
    val weightKg: Double? = null,
    val heightCm: Double? = null,
    val donatedAge60To64: Boolean? = null,
    val preferredProvince: String? = null
)

private class DonationJson(
    val id: Long? = null,
    val type: String? = null,
    val date: String? = null,
    val birthDate: String? = null,
    val certNumber: String? = null,
    val name: String? = null,
    val sex: String? = null,
    val centerName: String? = null,
    val donatedVolumeMl: Int? = null
)

private class PlanJson(val id: Long? = null, val type: String? = null, val date: String? = null)

private class MedicationJson(
    val id: Long? = null,
    val ingredientName: String? = null,
    val representativeBrands: String? = null,
    val intakeDate: String? = null,
    val restrictionDays: Int? = null,
    val restrictionText: String? = null
)

private class DiseaseJson(val id: Long? = null, val diseaseName: String? = null, val treatmentEndDate: String? = null)

private class StayJson(
    val id: Long? = null,
    val regionName: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val offshoreOnly: Boolean? = null,
    val visitedRiskArea: Boolean? = null
)

private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

/** The backup as the file's text: indented JSON with dates in ISO form ("2026-11-03"). */
fun encodeBackup(backup: AppBackup, exportedAt: LocalDateTime): String {
    val p = backup.profile
    val json = BackupJson(
        format = BACKUP_FORMAT,
        version = BACKUP_VERSION,
        exportedAt = exportedAt.withNano(0).toString(),
        profile = ProfileJson(
            name = p.name,
            birthDate = p.birthDate?.toString(),
            sex = p.sex?.name,
            weightKg = p.weightKg,
            heightCm = p.heightCm,
            donatedAge60To64 = p.donatedAge60To64,
            preferredProvince = p.preferredProvince
        ),
        donations = backup.donations.map {
            DonationJson(
                id = it.id,
                type = it.type.name,
                date = it.date.toString(),
                birthDate = it.birthDate?.toString(),
                certNumber = it.certNumber,
                name = it.name,
                sex = it.sex?.name,
                centerName = it.centerName,
                donatedVolumeMl = it.donatedVolumeMl
            )
        },
        plan = backup.plan.map { PlanJson(it.id, it.type.name, it.date.toString()) },
        medications = backup.medications.map {
            MedicationJson(it.id, it.ingredientName, it.representativeBrands, it.intakeDate.toString(), it.restrictionDays, it.restrictionText)
        },
        diseases = backup.diseases.map { DiseaseJson(it.id, it.diseaseName, it.treatmentEndDate.toString()) },
        stays = backup.stays.map {
            StayJson(it.id, it.regionName, it.startDate.toString(), it.endDate.toString(), it.offshoreOnly, it.visitedRiskArea)
        },
        favoritePlaces = backup.favoritePlaces.sorted()
    )
    return gson.toJson(json) + "\n"
}

private fun String?.date(): LocalDate? =
    this?.let { try { LocalDate.parse(it.trim()) } catch (e: DateTimeParseException) { null } }

private fun String?.cleaned(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private fun String?.sex(): Sex? = this?.let { name -> Sex.entries.firstOrNull { it.name == name } }

private fun String?.donationType(): DonationType? = this?.let { name -> DonationType.entries.firstOrNull { it.name == name } }

/**
 * Reads a file [encodeBackup] wrote. A file that isn't one of this app's backups, or comes from a
 * newer version of it, is refused with a [BackupFormatException]. Within a good file, an entry that
 * can't be read - a missing date, an unknown type - is skipped and counted, as is one repeating an
 * id already read, or a planned day already taken (the plan holds one donation a day).
 */
fun decodeBackup(text: String): BackupFile {
    val raw = try {
        gson.fromJson(text, BackupJson::class.java)
    } catch (e: JsonParseException) {
        throw BackupFormatException("백업 파일을 읽을 수 없어요. 파일이 손상됐거나 백업 파일이 아니에요.", e)
    }
    if (raw?.format != BACKUP_FORMAT) throw BackupFormatException("이 앱에서 내보낸 백업 파일이 아니에요.")
    val version = raw.version ?: throw BackupFormatException("백업 파일의 버전 정보가 없어요.")
    if (version > BACKUP_VERSION) {
        throw BackupFormatException("더 새로운 버전의 앱에서 만든 백업이에요. 앱을 업데이트한 뒤 불러와 주세요.")
    }

    var skipped = 0

    /** [entries] read by [read], dropping (and counting) unreadable ones and repeated [key]s. */
    fun <J : Any, T> readAll(entries: List<J?>?, key: (T) -> Any?, read: (J) -> T?): List<T> {
        val seen = mutableSetOf<Any>()
        return entries.orEmpty().mapNotNull { json ->
            val value = json?.let(read)
            val k = value?.let(key)
            when {
                value == null -> null.also { skipped++ }
                k != null && !seen.add(k) -> null.also { skipped++ }
                else -> value
            }
        }
    }

    // Room fills in an id of 0, so only real ids are checked for repeats.
    fun roomId(id: Long): Any? = id.takeIf { it != 0L }

    val profile = raw.profile.let { p ->
        DonorProfile(
            weightKg = p?.weightKg?.takeIf { it > 0 },
            heightCm = p?.heightCm?.takeIf { it > 0 },
            sex = p?.sex.sex(),
            name = p?.name.cleaned(),
            birthDate = p?.birthDate.date(),
            donatedAge60To64 = p?.donatedAge60To64 ?: false,
            preferredProvince = p?.preferredProvince.cleaned()
        )
    }
    val donations = readAll(raw.donations, key = { it.id }) { j: DonationJson ->
        DonationRecord(
            id = j.id ?: return@readAll null,
            type = j.type.donationType() ?: return@readAll null,
            date = j.date.date() ?: return@readAll null,
            birthDate = j.birthDate.date(),
            certNumber = j.certNumber.cleaned(),
            name = j.name.cleaned(),
            sex = j.sex.sex(),
            centerName = j.centerName.cleaned(),
            donatedVolumeMl = j.donatedVolumeMl?.takeIf { it > 0 }
        )
    }
    val plan = readAll(raw.plan, key = { it.date }) { j: PlanJson ->
        PlannedDonation(
            id = j.id ?: return@readAll null,
            type = j.type.donationType() ?: return@readAll null,
            date = j.date.date() ?: return@readAll null
        )
    }
    val medications = readAll(raw.medications, key = { roomId(it.id) }) { j: MedicationJson ->
        MedicationRecord(
            id = j.id ?: 0,
            ingredientName = j.ingredientName.cleaned() ?: return@readAll null,
            representativeBrands = j.representativeBrands?.trim().orEmpty(),
            intakeDate = j.intakeDate.date() ?: return@readAll null,
            restrictionDays = j.restrictionDays ?: return@readAll null,
            restrictionText = j.restrictionText?.trim().orEmpty()
        )
    }
    val diseases = readAll(raw.diseases, key = { roomId(it.id) }) { j: DiseaseJson ->
        DiseaseRecord(
            id = j.id ?: 0,
            diseaseName = j.diseaseName.cleaned() ?: return@readAll null,
            treatmentEndDate = j.treatmentEndDate.date() ?: return@readAll null
        )
    }
    val stays = readAll(raw.stays, key = { roomId(it.id) }) { j: StayJson ->
        val start = j.startDate.date() ?: return@readAll null
        val end = j.endDate.date() ?: return@readAll null
        if (end.isBefore(start)) return@readAll null
        StayRecord(
            id = j.id ?: 0,
            regionName = j.regionName.cleaned() ?: return@readAll null,
            startDate = start,
            endDate = end,
            offshoreOnly = j.offshoreOnly ?: false,
            visitedRiskArea = j.visitedRiskArea ?: true
        )
    }
    val favorites = raw.favoritePlaces.orEmpty().mapNotNull { it.cleaned() }.toSet()

    val exportedAt = raw.exportedAt?.let { try { LocalDateTime.parse(it) } catch (e: DateTimeParseException) { null } }
    return BackupFile(
        AppBackup(profile, donations, plan, medications, diseases, stays, favorites),
        exportedAt = exportedAt,
        skipped = skipped
    )
}
