package com.example.myapplication.model

import java.time.LocalDate
import java.time.Period

/**
 * The 감염병·vCJD·말라리아 헌혈 제한 기준 bundled as `blood_donation_rules.xml`, parsed by
 * [com.example.myapplication.data.parseInfectionRules].
 *
 * [diseases] holds every history entry one date restricts: the 감염병 and, after them, the 질환,
 * 시술·수술, 예방접종 and 기타 사유 of `<OtherHistory>`, told apart by [DiseaseRule.category].
 */
data class InfectionRules(
    val diseases: List<DiseaseRule>,
    val vcjdRules: List<VcjdRule>,
    val domesticMalaria: DomesticMalariaRules,
    val internationalMalaria: InternationalMalariaRules,
    val overseasTravel: OverseasTravelRule
)

/** How long a disease (or stay) defers donation. */
sealed interface DeferralPeriod {

    /** 영구배제: no amount of waiting makes the donor eligible again. */
    data object Permanent : DeferralPeriod

    /** 치료종료 시까지 (or 증상 소실 시까지): eligible again from the day after the date it's counted from. */
    data object UntilTreatmentEnds : DeferralPeriod

    /**
     * Eligible again [period] after the date it's counted from - e.g. 3년 after treatment ends. 당일
     * and 24시간 are one day: that day is out, the next one is fine.
     */
    data class After(val period: Period) : DeferralPeriod
}

/**
 * One history entry a date restricts: a `<Disease>` of 일반 감염병, or an `<Item>` of 질환·시술·
 * 예방접종·기타, deferred for [deferral] from [countedFrom].
 *
 * @param periodText The period as written ("3년", "4주", "당일").
 * @param category 감염병, 질환, 시술·수술, 예방접종 or 기타.
 * @param countedFrom What the date is ("치료 종료", "시술", "접종"); "${countedFrom}일" labels it.
 */
data class DiseaseRule(
    val name: String,
    val deferral: DeferralPeriod,
    val periodText: String,
    val category: String = INFECTIOUS_DISEASE_CATEGORY,
    val countedFrom: String = TREATMENT_END
) {
    /** "치료 종료 후 3년", "시술 당일", "증상 소실 시까지", or 영구. */
    val periodLabel: String
        get() = when (deferral) {
            DeferralPeriod.Permanent -> "영구"
            DeferralPeriod.UntilTreatmentEnds -> "$countedFrom 시까지"
            is DeferralPeriod.After -> if (periodText == SAME_DAY) "$countedFrom $SAME_DAY" else "$countedFrom 후 $periodText"
        }

    /** The date field's label: 치료 종료일, 시술일, 접종일, 분만·유산일. */
    val dateLabel: String get() = "${countedFrom}일"
}

const val INFECTIOUS_DISEASE_CATEGORY = "감염병"
const val TREATMENT_END = "치료 종료"
private const val SAME_DAY = "당일"

/**
 * One vCJD `<Region>`: staying in any of [countries] for a combined [minStay] or more between
 * [from] and [to] (both inclusive) is a lifetime ban. 프랑스·아일랜드 share one rule, so their stays
 * add up toward its 5년.
 */
data class VcjdRule(
    val countries: List<String>,
    val includedAreas: List<String>,
    val from: LocalDate,
    val to: LocalDate,
    val minStay: Period,
    val targetPeriodText: String,
    val minStayText: String
)

/**
 * A `<Restriction>`: which donation types stay open, and for how long the others are deferred.
 * A zero [deferral] means no restriction at all.
 */
data class MalariaRestriction(val allowedTypes: Set<DonationType>, val deferral: Period)

data class DomesticMalariaRegion(
    val province: String,
    val city: String,
    /** 북한: "국외헌혈기준에 준하여 적용" - judged by [InternationalMalariaRules] instead. */
    val followsInternationalRules: Boolean,
    val note: String?
)

data class DomesticMalariaRules(
    val activeYearText: String,
    /** Staying fewer nights than this (e.g. a day trip) doesn't restrict. */
    val minNights: Int,
    val overnight: MalariaRestriction,
    /** Lodging only at sea 30km+ from land: [offshore] applies instead of [overnight]. */
    val offshore: MalariaRestriction,
    val offshoreConditionText: String,
    val regions: List<DomesticMalariaRegion>
)

data class MalariaCountry(
    val name: String,
    val continent: String,
    /** False for a PartialRegion country, where only some areas are malaria risk areas. */
    val entireRegion: Boolean
)

data class InternationalMalariaRules(
    /** A stay at least this long counts as 거주·복무 ([residence]); a shorter one as 여행 ([travel]). */
    val residenceMinStay: Period,
    val residence: MalariaRestriction,
    val travel: MalariaRestriction,
    val residenceText: String,
    val travelText: String,
    val countries: List<MalariaCountry>
)

/**
 * 해외 방문: any stay outside [excludedCountry] (대한민국) defers donation by [restriction] from the
 * day the donor came back, on top of any 말라리아·vCJD restriction the same stay places.
 */
data class OverseasTravelRule(val excludedCountry: String, val restriction: MalariaRestriction)
