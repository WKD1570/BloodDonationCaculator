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

    private fun ruleOf(name: String) = rules.diseases.single { it.name == name }

    @Test
    fun `parses every disease with its deferral period`() {
        assertEquals(92, rules.diseases.count { it.category == "감염병" })
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
    fun `adds the infectious diseases the 7th edition lists that were missing`() {
        assertEquals(DeferralPeriod.After(Period.ofYears(1)), deferralOf("E형간염"))
        assertEquals(DeferralPeriod.Permanent, deferralOf("사람T세포림프친화바이러스(HTLV) 감염증"))
        assertEquals(DeferralPeriod.After(Period.ofWeeks(4)), deferralOf("코로나바이러스감염증-19(코로나19)"))
        assertEquals(DeferralPeriod.After(Period.ofMonths(1)), deferralOf("중동호흡기증후군(MERS)"))
        assertEquals("감염병", ruleOf("E형간염").category)
        assertEquals("치료 종료일", ruleOf("E형간염").dateLabel)
    }

    @Test
    fun `parses 질환, 시술·수술, 예방접종 and 기타, each counted from its own date`() {
        val byCategory = rules.diseases.groupingBy { it.category }.eachCount()
        assertEquals(setOf("감염병", "질환", "시술·수술", "예방접종", "기타"), byCategory.keys)
        assertEquals(32, byCategory["질환"])
        assertEquals(37, byCategory["시술·수술"])
        assertEquals(34, byCategory["예방접종"])
        assertEquals(2, byCategory["기타"])

        val endoscopy = ruleOf("내시경(위·대장)")
        assertEquals(DeferralPeriod.After(Period.ofMonths(1)), endoscopy.deferral)
        assertEquals("시술일", endoscopy.dateLabel)
        assertEquals("시술 후 1개월", endoscopy.periodLabel)

        assertEquals("수혈일", ruleOf("수혈").dateLabel)
        assertEquals("접종 후 4주", ruleOf("대상포진 예방접종").periodLabel)
        assertEquals("분만·유산 후 6개월", ruleOf("분만·유산").periodLabel)
        assertEquals(DeferralPeriod.Permanent, ruleOf("뇌수술").deferral)
    }

    @Test
    fun `당일 and 24시간 shut out only that day, and 까지 lasts through the date`() {
        assertEquals(DeferralPeriod.After(Period.ofDays(1)), deferralOf("PET 등 핵의학검사"))
        assertEquals("시술 당일", ruleOf("PET 등 핵의학검사").periodLabel)
        assertEquals(DeferralPeriod.After(Period.ofDays(1)), deferralOf("독감(인플루엔자) 예방접종(주사)"))
        assertEquals("접종 후 24시간", ruleOf("독감(인플루엔자) 예방접종(주사)").periodLabel)

        val gastritis = ruleOf("위염·위궤양·역류성 식도염")
        assertEquals(DeferralPeriod.UntilTreatmentEnds, gastritis.deferral)
        assertEquals("증상 소실 시까지", gastritis.periodLabel)
        assertEquals("증상 소실일", gastritis.dateLabel)
        assertEquals("증상 소실일", ruleOf("설사(하루 3회 이상 묽은 변)").dateLabel)
        assertEquals("치료 종료일", ruleOf("식중독").dateLabel)
    }

    @Test
    fun `history is looked up by name, so no name is listed twice`() {
        assertEquals(rules.diseases.size, rules.diseases.map { it.name }.toSet().size)
        assertThrows(InfectionRuleDataException::class.java) {
            parseInfectionRules(
                File("src/main/assets/$INFECTION_RULES_ASSET").readText()
                    .replace("<Item>옴</Item>", "<Item>말라리아</Item>")
                    .byteInputStream()
            )
        }
    }

    @Test
    fun `an unreadable deferral period is an error rather than no restriction`() {
        assertThrows(InfectionRuleDataException::class.java) { parseDeferral("한동안") }
    }
}
