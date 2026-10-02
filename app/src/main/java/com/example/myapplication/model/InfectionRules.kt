package com.example.myapplication.model

import java.time.LocalDate
import java.time.Period

/**
 * The 감염병·vCJD·말라리아 헌혈 제한 기준 bundled as `blood_donation_rules.xml`, parsed by
 * [com.example.myapplication.data.parseInfectionRules].
 */
data class InfectionRules(
    val diseases: List<DiseaseRule>,
    val vcjdRules: List<VcjdRule>,
    val domesticMalaria: DomesticMalariaRules,
    val internationalMalaria: InternationalMalariaRules
)

/** How long a disease (or stay) defers donation. */
sealed interface DeferralPeriod {

    /** 영구배제: no amount of waiting makes the donor eligible again. */
    data object Permanent : DeferralPeriod

    /** 치료종료 시까지: eligible again from the day after treatment ends. */
    data object UntilTreatmentEnds : DeferralPeriod

    /** Eligible again [period] after the date it's counted from - e.g. 3년 after treatment ends. */
    data class After(val period: Period) : DeferralPeriod
}

/** One `<Disease>` of 일반 감염병, deferred for [deferral] after treatment ends. */
data class DiseaseRule(val name: String, val deferral: DeferralPeriod, val periodText: String)

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
