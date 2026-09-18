package com.example.myapplication.domain

import com.example.myapplication.model.RestrictedDrug
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrugRestrictionCalculatorTest {

    @Test
    fun `addRestrictionPeriod adds days across a month boundary`() {
        val result = addRestrictionPeriod(LocalDate.of(2026, 1, 30), 3, ChronoUnit.DAYS)
        assertEquals(LocalDate.of(2026, 2, 2), result)
    }

    @Test
    fun `addRestrictionPeriod adding one month from Jan 31 clamps to Feb 28 in a non-leap year`() {
        val result = addRestrictionPeriod(LocalDate.of(2026, 1, 31), 1, ChronoUnit.MONTHS)
        assertEquals(LocalDate.of(2026, 2, 28), result)
    }

    @Test
    fun `addRestrictionPeriod adding one month from Jan 31 clamps to Feb 29 in a leap year`() {
        val result = addRestrictionPeriod(LocalDate.of(2024, 1, 31), 1, ChronoUnit.MONTHS)
        assertEquals(LocalDate.of(2024, 2, 29), result)
    }

    @Test
    fun `addRestrictionPeriod adding six months crosses a leap day without dropping it`() {
        val result = addRestrictionPeriod(LocalDate.of(2023, 12, 31), 6, ChronoUnit.MONTHS)
        assertEquals(LocalDate.of(2024, 6, 30), result)
    }

    private val aspirin = RestrictedDrug(
        category = "기타 약물",
        ingredientName = "아스피린",
        representativeBrands = "아스피린",
        restrictionDays = 3,
        restrictionText = "복용 후 3일간 헌혈 금지 (감기 치료 목적 경구 복용 포함)"
    )

    private val etretinate = RestrictedDrug(
        category = "건선 치료제",
        ingredientName = "에트레티네이트",
        representativeBrands = "티가손, 타가손, 테지손",
        restrictionDays = -1,
        restrictionText = "복용 시 영구적으로 헌혈 금지"
    )

    @Test
    fun `nextEligibleDonationDate adds the drug's restriction days to the medication date`() {
        val result = nextEligibleDonationDate(aspirin, LocalDate.of(2026, 9, 16))
        assertEquals(LocalDate.of(2026, 9, 19), result)
    }

    @Test
    fun `nextEligibleDonationDate returns null for a permanently restricted drug`() {
        val result = nextEligibleDonationDate(etretinate, LocalDate.of(2026, 9, 16))
        assertNull(result)
    }

    @Test
    fun `searchDrugs matches by ingredient name`() {
        val results = searchDrugs("아스피린", listOf(aspirin, etretinate))
        assertEquals(listOf(aspirin), results)
    }

    @Test
    fun `searchDrugs matches by representative brand name`() {
        val results = searchDrugs("타가손", listOf(aspirin, etretinate))
        assertEquals(listOf(etretinate), results)
    }

    @Test
    fun `searchDrugs is case-insensitive`() {
        val ibuprofen = aspirin.copy(ingredientName = "Ibuprofen")
        val results = searchDrugs("ibu", listOf(ibuprofen))
        assertEquals(listOf(ibuprofen), results)
    }

    @Test
    fun `searchDrugs returns nothing for a blank query`() {
        val results = searchDrugs("", listOf(aspirin, etretinate))
        assertTrue(results.isEmpty())
    }

    @Test
    fun `PROHIBITED_DRUGS contains no duplicate ingredient plus brand entries`() {
        val keys = PROHIBITED_DRUGS.map { it.ingredientName to it.representativeBrands }
        assertEquals(keys.size, keys.distinct().size)
    }
}
