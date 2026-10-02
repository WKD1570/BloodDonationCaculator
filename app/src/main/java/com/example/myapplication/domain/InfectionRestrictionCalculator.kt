package com.example.myapplication.domain

import com.example.myapplication.model.DeferralPeriod
import com.example.myapplication.model.DiseaseRecord
import com.example.myapplication.model.DomesticMalariaRegion
import com.example.myapplication.model.DonationType
import com.example.myapplication.model.InfectionRules
import com.example.myapplication.model.MalariaRestriction
import com.example.myapplication.model.StayRecord
import com.example.myapplication.model.StayRegion
import com.example.myapplication.model.StayRegionKind
import com.example.myapplication.model.VcjdRule
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit

/** The history entry a [HealthRestriction] comes from. */
sealed interface HistorySource {
    data class Disease(val id: Long) : HistorySource
    data class Stay(val id: Long) : HistorySource
}

/**
 * A restriction that 감염병 or 체류 history places on donating.
 *
 * @param countedFrom The date [periodLabel] counts from (치료 종료일, or the last day of a stay);
 * null for a vCJD ban, which isn't counted from anywhere.
 * @param eligibleFrom The first day the blocked types are allowed again, or null for a lifetime ban.
 * @param allowedTypes The donation types this doesn't restrict at all - e.g. 혈장 after a 말라리아
 * 위험지역 stay. Empty when every type is restricted.
 */
data class HealthRestriction(
    val title: String,
    val periodLabel: String,
    val detail: String,
    val countedFrom: LocalDate?,
    val eligibleFrom: LocalDate?,
    val allowedTypes: Set<DonationType>,
    val sources: Set<HistorySource>
) {
    val isPermanent: Boolean get() = eligibleFrom == null

    fun blocks(type: DonationType): Boolean = type !in allowedTypes

    /** Still in force on [today]: a lifetime ban, or one that ends after today. */
    fun isActive(today: LocalDate): Boolean = eligibleFrom?.isAfter(today) ?: true

    val reasonText: String
        get() = if (isPermanent) "$title → 영구 헌혈 금지"
        else "$title · $periodLabel 제한" + (countedFrom?.let { " (${fmtShort(it)})" } ?: "")
}

fun Period.label(): String = when {
    years > 0 && months == 0 && days == 0 -> "${years}년"
    years == 0 && days == 0 -> "${toTotalMonths()}개월"
    toTotalMonths() == 0L -> "${days}일"
    else -> toString()
}

/** How [StayRecord.regionName] names a domestic region: "경기 파주시", or just "북한". */
val DomesticMalariaRegion.regionName: String
    get() = if (followsInternationalRules) province else "$province $city"

/** Every place a stay can be recorded in, in the order the rules list them: 국내, 국외, vCJD. */
fun stayRegions(rules: InfectionRules): List<StayRegion> {
    val domestic = rules.domesticMalaria
    val international = rules.internationalMalaria
    val domesticRegions = domestic.regions.map { region ->
        if (region.followsInternationalRules) {
            StayRegion(
                name = region.regionName,
                kind = StayRegionKind.INTERNATIONAL_MALARIA,
                isDomestic = true,
                group = "말라리아 위험지역",
                detail = listOfNotNull(region.city, region.note).joinToString(" · ")
            )
        } else {
            StayRegion(
                name = region.regionName,
                kind = StayRegionKind.DOMESTIC_MALARIA,
                isDomestic = true,
                group = "말라리아 위험지역",
                detail = "${domestic.minNights}박 이상 숙박·거주·군복무 시 ${domestic.overnight.deferral.label()} 제한"
            )
        }
    }
    val malariaCountries = international.countries.map { country ->
        StayRegion(
            name = country.name,
            kind = StayRegionKind.INTERNATIONAL_MALARIA,
            isDomestic = false,
            group = "말라리아 · ${country.continent}",
            detail = if (country.entireRegion) "전지역 위험" else "일부 지역 위험",
            partialRegion = !country.entireRegion
        )
    }
    val vcjdCountries = rules.vcjdRules.flatMap { rule ->
        rule.countries.map { country ->
            StayRegion(
                name = country,
                kind = StayRegionKind.VCJD,
                isDomestic = false,
                group = "vCJD 헌혈금지지역",
                detail = "${rule.targetPeriodText} ${rule.minStayText} 체류 시 영구 헌혈 금지",
                aliases = rule.includedAreas.filterNot { "전지역" in it }
            )
        }
    }
    // A name listed by two rule sets is still evaluated against both; it's only listed once.
    return (domesticRegions + malariaCountries + vcjdCountries).distinctBy { it.name }
}

/** Case-insensitive match on the name, its aliases, or the group it's listed under. */
fun searchStayRegions(query: String, regions: List<StayRegion>): List<StayRegion> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return regions
    return regions.filter { region ->
        (listOf(region.name, region.group) + region.aliases).any { it.contains(trimmed, ignoreCase = true) }
    }
}

/** The restriction a 감염병 places on donation, or null when [rules] don't list it. */
fun diseaseRestriction(record: DiseaseRecord, rules: InfectionRules): HealthRestriction? {
    val rule = rules.diseases.firstOrNull { it.name == record.diseaseName } ?: return null
    val end = record.treatmentEndDate
    val (eligibleFrom, periodLabel) = when (val deferral = rule.deferral) {
        DeferralPeriod.Permanent -> null to "영구"
        DeferralPeriod.UntilTreatmentEnds -> end.plusDays(1) to "치료 종료 시까지"
        is DeferralPeriod.After -> end.plus(deferral.period) to "치료 종료 후 ${deferral.period.label()}"
    }
    return HealthRestriction(
        title = record.diseaseName,
        periodLabel = periodLabel,
        detail = "감염병 · ${rule.periodText}",
        countedFrom = end,
        eligibleFrom = eligibleFrom,
        allowedTypes = emptySet(),
        sources = setOf(HistorySource.Disease(record.id))
    )
}

/** Days from [start] through [end], both counted. */
private fun inclusiveDays(start: LocalDate, end: LocalDate): Long = ChronoUnit.DAYS.between(start, end) + 1

private fun StayRecord.malariaRestriction(restriction: MalariaRestriction, detail: String): HealthRestriction? {
    if (restriction.deferral.isZero) return null
    return HealthRestriction(
        title = "$regionName 체류",
        periodLabel = "체류 종료 후 ${restriction.deferral.label()}",
        detail = detail,
        countedFrom = endDate,
        eligibleFrom = endDate.plus(restriction.deferral),
        allowedTypes = restriction.allowedTypes,
        sources = setOf(HistorySource.Stay(id))
    )
}

/**
 * The 말라리아 restriction a single stay places on donation, or null when it doesn't restrict:
 * a region the rules don't list, a 국내 stay of fewer nights than required or lodged only far out
 * at sea, or a 국외 일부 지역 country whose risk areas weren't visited.
 *
 * 국외 stays are told apart by length: one of [com.example.myapplication.model.InternationalMalariaRules.residenceMinStay]
 * or more is judged as 거주·복무, anything shorter as 여행 - so a single-day visit still restricts.
 */
fun malariaRestriction(stay: StayRecord, rules: InfectionRules): HealthRestriction? {
    val domestic = rules.domesticMalaria
    val domesticRegion = domestic.regions.firstOrNull { it.regionName == stay.regionName }
    if (domesticRegion != null && !domesticRegion.followsInternationalRules) {
        if (ChronoUnit.DAYS.between(stay.startDate, stay.endDate) < domestic.minNights) return null
        return if (stay.offshoreOnly) {
            stay.malariaRestriction(domestic.offshore, "국내 말라리아 · ${domestic.offshoreConditionText} 숙박")
        } else {
            stay.malariaRestriction(domestic.overnight, "국내 말라리아 · ${domestic.minNights}박 이상 숙박·거주·군복무")
        }
    }

    val international = rules.internationalMalaria
    val country = international.countries.firstOrNull { it.name == stay.regionName }
    if (domesticRegion == null && country == null) return null
    if (country != null && !country.entireRegion && !stay.visitedRiskArea) return null

    val isResidence = !stay.startDate.plus(international.residenceMinStay).isAfter(stay.endDate.plusDays(1))
    return if (isResidence) {
        stay.malariaRestriction(international.residence, "국외 말라리아 · 거주·복무 (${international.residenceText})")
    } else {
        stay.malariaRestriction(international.travel, "국외 말라리아 · 여행 (${international.travelText})")
    }
}

/**
 * [minStay] in days, counting a month as 30 days and a year as 12 months, so that stays in
 * separate trips can be added up against it. Errs short of the calendar length (5년 → 1,800 days),
 * which errs toward restricting.
 */
private fun minStayDays(minStay: Period): Long = minStay.toTotalMonths() * 30 + minStay.days

/**
 * The lifetime bans vCJD rules place on donation: one per rule whose countries were stayed in, in
 * total across every stay, for its minimum time or more during its target period. Only the part
 * of each stay inside the target period counts.
 */
fun vcjdRestrictions(stays: List<StayRecord>, rules: InfectionRules): List<HealthRestriction> =
    rules.vcjdRules.mapNotNull { rule -> vcjdRestriction(stays, rule) }

/** Days of [stay] inside [rule]'s target period, both ends counted; 0 when it's entirely outside. */
private fun daysInTargetPeriod(stay: StayRecord, rule: VcjdRule): Long {
    val start = maxOf(stay.startDate, rule.from)
    val end = minOf(stay.endDate, rule.to)
    return if (start.isAfter(end)) 0 else inclusiveDays(start, end)
}

private fun vcjdRestriction(stays: List<StayRecord>, rule: VcjdRule): HealthRestriction? {
    val counted = stays
        .filter { it.regionName in rule.countries }
        .mapNotNull { stay -> daysInTargetPeriod(stay, rule).takeIf { it > 0 }?.let { stay to it } }
    val totalDays = counted.sumOf { it.second }
    if (counted.isEmpty() || totalDays < minStayDays(rule.minStay)) return null

    val countries = counted.map { it.first.regionName }.distinct().joinToString("·")
    return HealthRestriction(
        title = "$countries 체류(vCJD)",
        periodLabel = "영구",
        detail = "vCJD 헌혈금지지역 · ${rule.targetPeriodText} 중 ${totalDays}일 체류 (기준 ${rule.minStayText})",
        countedFrom = null,
        eligibleFrom = null,
        allowedTypes = emptySet(),
        sources = counted.map { HistorySource.Stay(it.first.id) }.toSet()
    )
}

/**
 * Time spent in one vCJD country: [totalDays] in all, and [daysInPeriod] of them inside its rule's
 * target period - the part that counts toward the rule's [VcjdRule.minStay].
 */
data class VcjdExposure(val country: String, val rule: VcjdRule, val totalDays: Long, val daysInPeriod: Long)

/** [VcjdExposure] for each vCJD country [stays] spent any time in, in the rules' order. */
fun vcjdExposures(stays: List<StayRecord>, rules: InfectionRules): List<VcjdExposure> =
    rules.vcjdRules.flatMap { rule ->
        rule.countries.mapNotNull { country ->
            val inCountry = stays.filter { it.regionName == country }
            if (inCountry.isEmpty()) return@mapNotNull null
            VcjdExposure(
                country = country,
                rule = rule,
                totalDays = inCountry.sumOf { inclusiveDays(it.startDate, it.endDate) },
                daysInPeriod = inCountry.sumOf { daysInTargetPeriod(it, rule) }
            )
        }
    }

/** Every restriction the donor's 감염병 and 체류 history places on donation, active or elapsed. */
fun healthRestrictions(
    diseases: List<DiseaseRecord>,
    stays: List<StayRecord>,
    rules: InfectionRules
): List<HealthRestriction> =
    diseases.mapNotNull { diseaseRestriction(it, rules) } +
        stays.mapNotNull { malariaRestriction(it, rules) } +
        vcjdRestrictions(stays, rules)
