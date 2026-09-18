package com.example.myapplication.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * One recorded intake of a 헌혈 제한 의약품.
 *
 * [restrictionDays] is copied from the drug catalog at the time the record is saved rather than
 * looked up on read, so a later edit to `prohibited_drugs.json` cannot silently move a date the
 * user was already shown. -1 carries the same meaning as in [RestrictedDrug]: a lifetime ban.
 */
@Entity(tableName = "medication_history")
data class MedicationRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ingredientName: String,
    val representativeBrands: String,
    val intakeDate: LocalDate,
    val restrictionDays: Int,
    val restrictionText: String
)

val MedicationRecord.isPermanentlyRestricted: Boolean get() = restrictionDays < 0

/**
 * The first date donation is allowed again on account of this intake, or null when the drug
 * carries a lifetime ban and no such date exists.
 */
val MedicationRecord.eligibleFrom: LocalDate?
    get() = if (isPermanentlyRestricted) null else intakeDate.plusDays(restrictionDays.toLong())

/** Builds a history entry from a catalog drug and the date the user says they took it. */
fun RestrictedDrug.toMedicationRecord(intakeDate: LocalDate): MedicationRecord = MedicationRecord(
    ingredientName = ingredientName,
    representativeBrands = representativeBrands,
    intakeDate = intakeDate,
    restrictionDays = restrictionDays,
    restrictionText = restrictionText
)
