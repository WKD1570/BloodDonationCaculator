package com.example.myapplication.domain

import androidx.compose.ui.graphics.Color
import com.example.myapplication.model.DonationRecord
import com.example.myapplication.model.DonationType
import com.example.myapplication.model.MedicationRecord
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

    data class PermanentlyProhibited(
        val blocker: MedicationRecord,
        override val reasons: List<Reason>
    ) : IntegratedNextResult
}

private val MedicationReasonColor = Color(0xFF7C3AED)
private val PermanentReasonColor = Color(0xFFE11D48)

/**
 * Combines the donation-history constraint ("Date A", from [calcNext]) with the medication-history
 * constraint ("Date B", from [medicationConstraint]) and returns the later of the two.
 *
 * A lifetime medication ban overrides everything: it is returned as
 * [IntegratedNextResult.PermanentlyProhibited] no matter what the donation history says, because
 * no amount of waiting makes the donor eligible again.
 */
fun integratedNextEligible(
    type: DonationType,
    records: List<DonationRecord>,
    medications: List<MedicationRecord>,
    today: LocalDate,
    donorAge: Int? = null
): IntegratedNextResult {
    val fromDonations = calcNext(type, records, today, donorAge)

    return when (val constraint = medicationConstraint(medications)) {
        is MedicationConstraint.Permanent -> IntegratedNextResult.PermanentlyProhibited(
            blocker = constraint.source,
            reasons = fromDonations.reasons + Reason(
                "${constraint.source.ingredientName} 복용 이력 → 영구 헌혈 금지",
                PermanentReasonColor
            )
        )

        MedicationConstraint.None -> IntegratedNextResult.Eligible(
            nextDate = fromDonations.nextDate,
            dd = fromDonations.dd,
            reasons = fromDonations.reasons
        )

        is MedicationConstraint.EligibleFrom -> {
            // calcNext already floors its answer at today, so maxOf also discards a medication
            // window that has already elapsed.
            val nextDate = maxOf(fromDonations.nextDate, constraint.date)
            // Match calcNext's convention: only surface a reason for a constraint that is still
            // in force, rather than listing every drug ever recorded.
            val reasons = if (constraint.date.isAfter(today)) {
                fromDonations.reasons + Reason(
                    "${constraint.source.ingredientName} 복용 후 ${constraint.source.restrictionDays}일 제한 " +
                        "(${fmtShort(constraint.source.intakeDate)})",
                    MedicationReasonColor
                )
            } else {
                fromDonations.reasons
            }
            IntegratedNextResult.Eligible(nextDate, dday(nextDate, today), reasons)
        }
    }
}
