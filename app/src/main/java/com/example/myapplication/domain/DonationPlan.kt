package com.example.myapplication.domain

import com.example.myapplication.model.DonationRecord
import com.example.myapplication.model.DonationType
import com.example.myapplication.model.MedicationRecord
import com.example.myapplication.model.PlannedDonation
import java.time.LocalDate

/**
 * How one planned donation fares against everything before it.
 *
 * @param allowedFrom The first day [planned]'s type is allowed, or null when a lifetime ban rules it
 * out altogether.
 * @param reasons What holds it back until [allowedFrom], when something does.
 */
data class PlanCheck(val planned: PlannedDonation, val allowedFrom: LocalDate?, val reasons: List<Reason>) {
    val isAllowed: Boolean get() = allowedFrom != null && !planned.date.isBefore(allowedFrom)
}

/**
 * Checks [plan] in date order with the rules 현황 uses - intervals, yearly counts and volume from
 * [records], plus [medications] and [healthRestrictions] - each as of its own day. An allowed
 * planned donation is assumed to happen, so it holds back the later ones like a real record would;
 * one that isn't allowed can't happen as planned, so it holds back nothing.
 */
fun checkPlan(
    plan: List<PlannedDonation>,
    records: List<DonationRecord>,
    medications: List<MedicationRecord>,
    healthRestrictions: List<HealthRestriction>
): List<PlanCheck> {
    val happening = records.toMutableList()
    return plan.sortedWith(compareBy({ it.date }, { it.id })).map { planned ->
        // Evaluated with the planned day as "today": it floors the answer there and keeps later
        // records and planned donations from binding it.
        val check = when (val result = integratedNextEligible(planned.type, happening, medications, planned.date, healthRestrictions)) {
            is IntegratedNextResult.Eligible -> PlanCheck(planned, result.nextDate, result.reasons)
            is IntegratedNextResult.PermanentlyProhibited -> PlanCheck(planned, null, result.reasons)
        }
        if (check.isAllowed) happening += planned.asRecord()
        check
    }
}

private const val CANDIDATE_ID = Long.MIN_VALUE

/**
 * Plans a [type] donation on each day from [from] through [until] that it's allowed on, earliest
 * first, and returns [plan] with them added. Days that already hold a planned donation are skipped,
 * and so is any day that would stop an allowed planned donation from happening.
 */
fun fillPlan(
    type: DonationType,
    from: LocalDate,
    until: LocalDate,
    plan: List<PlannedDonation>,
    records: List<DonationRecord>,
    medications: List<MedicationRecord>,
    healthRestrictions: List<HealthRestriction>,
    nextId: () -> Long
): List<PlannedDonation> {
    fun allowedIds(candidatePlan: List<PlannedDonation>, checks: List<PlanCheck> = checkPlan(candidatePlan, records, medications, healthRestrictions)) =
        checks.filter { it.isAllowed }.map { it.planned.id }.toSet()

    var current = plan
    var allowed = allowedIds(current)
    var day = from
    while (!day.isAfter(until)) {
        if (current.any { it.date == day }) {
            day = day.plusDays(1)
            continue
        }
        val candidate = PlannedDonation(CANDIDATE_ID, type, day)
        val checks = checkPlan(current + candidate, records, medications, healthRestrictions)
        val verdict = checks.first { it.planned.id == CANDIDATE_ID }
        val allowedFrom = verdict.allowedFrom ?: break // a lifetime ban: no day will do
        when {
            // Nothing before the first allowed day can change, so skip straight to it.
            !verdict.isAllowed -> day = allowedFrom
            allowedIds(current + candidate, checks).containsAll(allowed) -> {
                current = current + candidate.copy(id = nextId())
                allowed = allowedIds(current)
                day = day.plusDays(1)
            }
            else -> day = day.plusDays(1)
        }
    }
    return current
}

/**
 * The donated volume in the year up to [date] if the plan goes ahead: [records] plus the allowed
 * planned donations, counted the way 현황's gauge counts them.
 */
fun plannedAnnualVolume(checks: List<PlanCheck>, records: List<DonationRecord>, date: LocalDate): Int =
    (records + checks.filter { it.isAllowed }.map { it.planned.asRecord() })
        .filter { !it.date.isAfter(date) }
        .withinAnnualWindow(date)
        .sumOf { it.volumeMl() }

private fun PlannedDonation.asRecord() = DonationRecord(id = id, type = type, date = date)

/** [plan] from [today] on - a planned day that has passed has no use left. */
fun upcomingPlan(plan: List<PlannedDonation>, today: LocalDate): List<PlannedDonation> =
    plan.filterNot { it.date.isBefore(today) }

/** [plan] minus what [record] carried out: the planned donation on its day, of its type. */
fun planWithout(plan: List<PlannedDonation>, record: DonationRecord): List<PlannedDonation> =
    plan.filterNot { it.date == record.date && it.type == record.type }
