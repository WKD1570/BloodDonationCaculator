package com.example.myapplication.domain

import com.example.myapplication.data.INFECTION_RULES_ASSET
import com.example.myapplication.data.parseInfectionRules
import com.example.myapplication.model.DiseaseRecord
import com.example.myapplication.model.DonationType
import com.example.myapplication.model.StayRecord
import com.example.myapplication.model.StayRegionKind
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InfectionRestrictionCalculatorTest {

    private val rules = File("src/main/assets/$INFECTION_RULES_ASSET").inputStream().use(::parseInfectionRules)

    private fun disease(name: String, end: LocalDate, id: Long = 1) =
        DiseaseRecord(id = id, diseaseName = name, treatmentEndDate = end)

    private fun stay(
        region: String,
        start: LocalDate,
        end: LocalDate,
        id: Long = 1,
        offshoreOnly: Boolean = false,
        visitedRiskArea: Boolean = true
    ) = StayRecord(id, region, start, end, offshoreOnly, visitedRiskArea)

    // ---- 감염병 ----

    @Test
    fun `a fixed deferral counts from the treatment end date and blocks every type`() {
        val r = diseaseRestriction(disease("말라리아", LocalDate.of(2025, 2, 10)), rules)!!
        assertEquals(LocalDate.of(2028, 2, 10), r.eligibleFrom)
        DonationType.entries.forEach { assertTrue(r.blocks(it)) }
    }

    @Test
    fun `a month deferral clamps to the end of a shorter month`() {
        val r = diseaseRestriction(disease("결핵", LocalDate.of(2026, 1, 31)), rules)!!
        assertEquals(LocalDate.of(2026, 2, 28), r.eligibleFrom)
    }

    @Test
    fun `an until-treatment-ends disease is eligible again the day after treatment ends`() {
        val r = diseaseRestriction(disease("인플루엔자", LocalDate.of(2026, 9, 20)), rules)!!
        assertEquals(LocalDate.of(2026, 9, 21), r.eligibleFrom)
    }

    @Test
    fun `a permanent disease has no eligible date`() {
        val r = diseaseRestriction(disease("C형간염", LocalDate.of(2010, 1, 1)), rules)!!
        assertTrue(r.isPermanent)
        assertTrue(r.isActive(LocalDate.of(2099, 1, 1)))
    }

    @Test
    fun `a disease the rules don't list doesn't restrict`() {
        assertNull(diseaseRestriction(disease("감기", LocalDate.of(2026, 9, 1)), rules))
    }

    // ---- 국내 말라리아 ----

    @Test
    fun `an overnight domestic stay blocks whole blood and platelets for a year but not plasma`() {
        val r = malariaRestriction(stay("경기 파주시", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2)), rules)!!
        assertEquals(LocalDate.of(2027, 7, 2), r.eligibleFrom)
        assertTrue(r.blocks(DonationType.WHOLE_BLOOD))
        assertTrue(r.blocks(DonationType.PLATELET))
        assertFalse(r.blocks(DonationType.PLASMA))
    }

    @Test
    fun `a domestic day trip without staying the night doesn't restrict`() {
        assertNull(malariaRestriction(stay("강원 철원군", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1)), rules))
    }

    @Test
    fun `lodging only far out at sea doesn't restrict`() {
        val offshore = stay("인천 강화군", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 5), offshoreOnly = true)
        assertNull(malariaRestriction(offshore, rules))
    }

    @Test
    fun `North Korea follows the international rules, so even a day visit restricts`() {
        val r = malariaRestriction(stay("북한", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 1)), rules)!!
        assertEquals(LocalDate.of(2027, 5, 1), r.eligibleFrom)
        assertFalse(r.blocks(DonationType.PLASMA))
    }

    // ---- 국외 말라리아 ----

    @Test
    fun `a trip shorter than 6 months is a year's deferral from leaving`() {
        val r = malariaRestriction(stay("인도", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 10)), rules)!!
        assertEquals(LocalDate.of(2027, 1, 10), r.eligibleFrom)
        assertTrue(r.detail.contains("여행"))
    }

    @Test
    fun `a stay of 6 months or more counts as residence, a 3 year deferral`() {
        val r = malariaRestriction(stay("나이지리아", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 6, 30)), rules)!!
        assertEquals(LocalDate.of(2028, 6, 30), r.eligibleFrom)
        assertTrue(r.detail.contains("거주"))
    }

    @Test
    fun `one day short of 6 months is still travel`() {
        val r = malariaRestriction(stay("나이지리아", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 6, 29)), rules)!!
        assertEquals(LocalDate.of(2026, 6, 29), r.eligibleFrom)
    }

    @Test
    fun `a partial-region country restricts only when its risk areas were visited`() {
        val trip = stay("태국", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 7))
        assertEquals(LocalDate.of(2027, 3, 7), malariaRestriction(trip, rules)!!.eligibleFrom)
        assertNull(malariaRestriction(trip.copy(visitedRiskArea = false), rules))
    }

    @Test
    fun `visitedRiskArea doesn't exempt an entire-region country`() {
        val trip = stay("가나", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 7), visitedRiskArea = false)
        assertEquals(LocalDate.of(2027, 3, 7), malariaRestriction(trip, rules)!!.eligibleFrom)
    }

    // ---- vCJD ----

    @Test
    fun `3 months in the UK within 1980-1996 is a lifetime ban`() {
        val r = vcjdRestrictions(listOf(stay("영국", LocalDate.of(1990, 1, 1), LocalDate.of(1990, 3, 31))), rules)
        assertEquals(1, r.size)
        assertTrue(r.single().isPermanent)
        DonationType.entries.forEach { assertTrue(r.single().blocks(it)) }
    }

    @Test
    fun `only the part of a stay inside the target period counts`() {
        // 1996-11-01 ~ 1997-06-30: only Nov and Dec 1996 (61 days) fall inside 1980-1996.
        val stays = listOf(stay("영국", LocalDate.of(1996, 11, 1), LocalDate.of(1997, 6, 30)))
        assertTrue(vcjdRestrictions(stays, rules).isEmpty())
    }

    @Test
    fun `stays in France and Ireland add up toward their shared 5 years`() {
        val france = stay("프랑스", LocalDate.of(1990, 1, 1), LocalDate.of(1992, 12, 31), id = 1)
        val ireland = stay("아일랜드", LocalDate.of(1995, 1, 1), LocalDate.of(1997, 12, 31), id = 2)

        assertTrue(vcjdRestrictions(listOf(france), rules).isEmpty())
        val r = vcjdRestrictions(listOf(france, ireland), rules).single()
        assertEquals(setOf(HistorySource.Stay(1), HistorySource.Stay(2)), r.sources)
    }

    @Test
    fun `a UK stay after 1996 doesn't restrict`() {
        assertTrue(vcjdRestrictions(listOf(stay("영국", LocalDate.of(2010, 1, 1), LocalDate.of(2015, 1, 1))), rules).isEmpty())
    }

    // ---- catalog ----

    @Test
    fun `regions list domestic, international and vCJD places once each`() {
        val regions = stayRegions(rules)
        assertEquals(regions.size, regions.distinctBy { it.name }.size)
        assertEquals(StayRegionKind.DOMESTIC_MALARIA, regions.single { it.name == "경기 파주시" }.kind)
        assertTrue(regions.single { it.name == "북한" }.isDomestic)
        assertEquals(StayRegionKind.VCJD, regions.single { it.name == "아일랜드" }.kind)
        assertTrue(regions.single { it.name == "태국" }.partialRegion)
    }

    @Test
    fun `a UK area finds the UK`() {
        assertEquals(listOf("영국"), searchStayRegions("스코틀랜드", stayRegions(rules)).map { it.name })
    }
}
