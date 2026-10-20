package com.example.myapplication.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * One 감염병 the donor has had. Unlike [MedicationRecord], only what the user entered is stored:
 * the restriction is looked up from the bundled rules by [diseaseName] on read, so an updated rule
 * (e.g. a new 말라리아 기준) applies to history that was saved before it.
 */
@Entity(tableName = "disease_history")
data class DiseaseRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val diseaseName: String,
    /** The restriction periods count from here (치료 종료 후 기준). */
    val treatmentEndDate: LocalDate
)

/**
 * One stay in a 말라리아 or vCJD restricted region, or any other country abroad - a trip, or
 * residence/military service.
 * [regionName] is a [StayRegion.name]; like [DiseaseRecord], the rule is looked up on read.
 */
@Entity(tableName = "stay_history")
data class StayRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val regionName: String,
    val startDate: LocalDate,
    /** The last day in the region; deferrals count from here. */
    val endDate: LocalDate,
    /** 국내 말라리아: lodged only at sea 30km or more from land, which doesn't restrict. */
    val offshoreOnly: Boolean = false,
    /**
     * 국외 말라리아 일부 지역 국가: whether the stay included that country's risk areas. Defaults to
     * true - restricting is the safe answer when the donor isn't sure.
     */
    val visitedRiskArea: Boolean = true
)

enum class StayRegionKind {
    DOMESTIC_MALARIA,
    INTERNATIONAL_MALARIA,
    VCJD,

    /** A country no 말라리아·vCJD rule lists, entered by name: only the 해외 방문 rule applies. */
    OVERSEAS
}

/** A place a stay can be recorded in, built from [InfectionRules] by `stayRegions`. */
data class StayRegion(
    /** Unique across all regions; what [StayRecord.regionName] stores. */
    val name: String,
    val kind: StayRegionKind,
    /** Listed under 국내 rather than 국외 - true for 북한 too, though it follows the 국외 기준. */
    val isDomestic: Boolean,
    /** The continent, province or rule it's listed under, for display. */
    val group: String,
    val detail: String,
    /** 국외 말라리아 PartialRegion: only some areas are risk areas. */
    val partialRegion: Boolean = false,
    /** Extra names it can be searched by - a vCJD country's included areas, e.g. 스코틀랜드. */
    val aliases: List<String> = emptyList()
)
