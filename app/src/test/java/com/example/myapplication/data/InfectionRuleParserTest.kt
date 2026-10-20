package com.example.myapplication.data

import com.example.myapplication.model.DeferralPeriod
import com.example.myapplication.model.DonationType
import com.example.myapplication.model.InfectionRules
import java.io.File
import java.time.LocalDate
import java.time.Period
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class InfectionRuleParserTest {

    private val rules: InfectionRules =
        File("src/main/assets/$INFECTION_RULES_ASSET").inputStream().use(::parseInfectionRules)

    private fun deferralOf(disease: String) = rules.diseases.single { it.name == disease }.deferral

    @Test
    fun `parses every disease with its deferral period`() {
        assertEquals(84, rules.diseases.size)
        assertEquals(DeferralPeriod.Permanent, deferralOf("C형간염"))
        assertEquals(DeferralPeriod.After(Period.ofYears(3)), deferralOf("말라리아"))
        assertEquals(DeferralPeriod.After(Period.ofMonths(6)), deferralOf("뎅기열"))
        assertEquals(DeferralPeriod.After(Period.ofMonths(1)), deferralOf("결핵"))
        assertEquals(DeferralPeriod.UntilTreatmentEnds, deferralOf("인플루엔자"))
    }

    @Test
    fun `parses the overseas travel rule`() {
        assertEquals("대한민국", rules.overseasTravel.excludedCountry)
        assertEquals(Period.ofMonths(1), rules.overseasTravel.restriction.deferral)
        assertTrue(rules.overseasTravel.restriction.allowedTypes.isEmpty())
    }

    @Test
    fun `parses the vCJD rules, splitting a shared rule into its countries`() {
        val (uk, franceIreland) = rules.vcjdRules
        assertEquals(listOf("영국"), uk.countries)
        assertTrue("스코틀랜드" in uk.includedAreas)
        assertEquals(LocalDate.of(1980, 1, 1), uk.from)
        assertEquals(LocalDate.of(1996, 12, 31), uk.to)
        assertEquals(Period.ofMonths(3), uk.minStay)

        assertEquals(listOf("프랑스", "아일랜드"), franceIreland.countries)
        assertEquals(LocalDate.of(2001, 12, 31), franceIreland.to)
        assertEquals(Period.ofYears(5), franceIreland.minStay)
    }

    @Test
    fun `parses domestic malaria conditions and regions`() {
        val domestic = rules.domesticMalaria
        assertEquals(1, domestic.minNights)
        assertEquals(setOf(DonationType.PLASMA), domestic.overnight.allowedTypes)
        assertEquals(Period.ofYears(1), domestic.overnight.deferral)
        assertTrue(domestic.offshore.deferral.isZero)
        assertEquals(6, domestic.regions.size)
        assertTrue(domestic.regions.single { it.province == "북한" }.followsInternationalRules)
        assertFalse(domestic.regions.single { it.city == "파주시" }.followsInternationalRules)
    }

    @Test
    fun `parses international malaria conditions and countries`() {
        val international = rules.internationalMalaria
        assertEquals(Period.ofMonths(6), international.residenceMinStay)
        assertEquals(Period.ofYears(3), international.residence.deferral)
        assertEquals(Period.ofYears(1), international.travel.deferral)
        assertEquals(setOf(DonationType.PLASMA), international.travel.allowedTypes)
        assertEquals(86, international.countries.size)
        assertTrue(international.countries.single { it.name == "인도" }.entireRegion)
        assertFalse(international.countries.single { it.name == "태국" }.entireRegion)
    }

    @Test
    fun `malformed XML is reported as a data error`() {
        assertThrows(InfectionRuleDataException::class.java) {
            parseInfectionRules("<BloodDonationRules>".byteInputStream())
        }
    }

    @Test
    fun `an unreadable deferral period is an error rather than no restriction`() {
        assertThrows(InfectionRuleDataException::class.java) { parseDeferral("한동안") }
    }
}
