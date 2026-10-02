package com.example.myapplication.domain

import androidx.compose.ui.graphics.Color
import com.example.myapplication.model.DonationRecord
import com.example.myapplication.model.DonationType
import com.example.myapplication.model.MedicationRecord
import com.example.myapplication.model.REGULAR_DONATION_TYPES
import com.example.myapplication.model.eligibleFrom
import com.example.myapplication.model.isPermanentlyRestricted
import java.time.LocalDate

/** The constraint saved medication history places on the next donation - "Date B". */
sealed interface MedicationConstraint {

    /** Nothing on record restricts donation. */
    data object None : MedicationConstraint

    /** Donation is blocked until [date], driven by [source]. */
    data class EligibleFrom(val date: LocalDate, val source: MedicationRecord) : MedicationConstraint

    /** [source] carries a lifetime ban, so there is no eligible date at all. */
    data class Permanent(val source: MedicationRecord) : MedicationConstraint
}

/**
 * Reduces the whole medication history to the single constraint that binds.
 *
 * The binding entry is the one whose restriction window ends last, which is deliberately *not*
 * the same as the most recent intake: 두타스테라이드 taken five months ago (180 days) outlasts
 * 아스피린 taken yesterday (3 days). Taking only the latest intake would under-report the wait.
 */
fun medicationConstraint(medications: List<MedicationRecord>): MedicationConstraint {
    medications.firstOrNull { it.isPermanentlyRestricted }
        ?.let { return MedicationConstraint.Permanent(it) }

    // Every remaining entry has a real end date, since the permanent ones returned above.
    val binding = medications
        .mapNotNull { record -> record.eligibleFrom?.let { record to it } }
        .maxByOrNull { (_, date) -> date }
        ?: return MedicationConstraint.None

    return MedicationConstraint.EligibleFrom(date = binding.second, source = binding.first)
}

/**
 * The next eligible donation date once both histories are taken into account. Mirrors [NextResult]
 * but admits the one outcome that has no date: a lifetime medication ban.
 */
sealed interface IntegratedNextResult {
    val reasons: List<Reason>

    data class Eligible(
        val nextDate: LocalDate,
        val dd: DDay,
        override val reasons: List<Reason>
    ) : IntegratedNextResult

    /** [cause] names what carries the lifetime ban - a drug, a 감염병 or a vCJD 체류. */
    data class PermanentlyProhibited(
        val cause: String,
        override val reasons: List<Reason>
    ) : IntegratedNextResult
}

private val MedicationReasonColor = Color(0xFF7C3AED)
private val PermanentReasonColor = Color(0xFFE11D48)
private val HealthReasonColor = Color(0xFFD97706)

/**
 * [integratedNextEligible] for each of the [REGULAR_DONATION_TYPES] - the donations 현황 plans. The
 * 기타 types aren't planned, but their records still restrict these (e.g. 조혈모세포 기증 for 6 months).
 */
fun nextEligibleByType(
    records: List<DonationRecord>,
    medications: List<MedicationRecord>,
    today: LocalDate,
    healthRestrictions: List<HealthRestriction> = emptyList()
): Map<DonationType, IntegratedNextResult> =
    REGULAR_DONATION_TYPES.associateWith { integratedNextEligible(it, records, medications, today, healthRestrictions) }

/**
 * Combines the donation-history constraint ("Date A", from [calcNext]), the medication-history
 * constraint ("Date B", from [medicationConstraint]) and every [healthRestrictions] entry that
 * blocks [type] (감염병·체류 이력, from [com.example.myapplication.domain.healthRestrictions]), and
 * returns the latest of them. A health restriction that leaves [type] open - 혈장 after a 말라리아
 * 위험지역 stay - doesn't move [type]'s date at all.
 *
 * A lifetime ban overrides everything: it is returned as [IntegratedNextResult.PermanentlyProhibited]
 * no matter what the donation history says, because no amount of waiting makes the donor eligible
 * again.
 */
fun integratedNextEligible(
    type: DonationType,
    records: List<DonationRecord>,
    medications: List<MedicationRecord>,
    today: LocalDate,
    healthRestrictions: List<HealthRestriction> = emptyList()
): IntegratedNextResult {
    val fromDonations = calcNext(type, records, today)
    val blocking = healthRestrictions.filter { it.blocks(type) }

    val medication = medicationConstraint(medications)
    if (medication is MedicationConstraint.Permanent) {
        return IntegratedNextResult.PermanentlyProhibited(
            cause = medication.source.ingredientName,
            reasons = fromDonations.reasons + Reason(
                "${medication.source.ingredientName} 복용 이력 → 영구 헌혈 금지",
                PermanentReasonColor
            )
        )
    }
    blocking.firstOrNull { it.isPermanent }?.let { ban ->
        return IntegratedNextResult.PermanentlyProhibited(
            cause = ban.title,
            reasons = fromDonations.reasons + Reason(ban.reasonText, PermanentReasonColor)
        )
    }

    // calcNext already floors its answer at today, so maxOf also discards a medication or health
    // window that has already elapsed. Like calcNext, only constraints still in force on today
    // are surfaced as reasons, rather than every drug or trip ever recorded.
    var nextDate = fromDonations.nextDate
    val reasons = fromDonations.reasons.toMutableList()
    if (medication is MedicationConstraint.EligibleFrom) {
        nextDate = maxOf(nextDate, medication.date)
        if (medication.date.isAfter(today)) {
            reasons += Reason(
                "${medication.source.ingredientName} 복용 후 ${medication.source.restrictionDays}일 제한 " +
                    "(${fmtShort(medication.source.intakeDate)})",
                MedicationReasonColor
            )
        }
    }
    blocking.forEach { restriction ->
        val eligibleFrom = restriction.eligibleFrom ?: return@forEach
        nextDate = maxOf(nextDate, eligibleFrom)
        if (restriction.isActive(today)) reasons += Reason(restriction.reasonText, HealthReasonColor)
    }

    return IntegratedNextResult.Eligible(nextDate, dday(nextDate, today), reasons)
}
