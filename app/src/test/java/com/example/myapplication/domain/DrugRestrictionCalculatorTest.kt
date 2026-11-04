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
    fun `searchDrugs matches by category`() {
        assertEquals(listOf(etretinate), searchDrugs("건선", listOf(aspirin, etretinate)))
        assertEquals(
            listOf(7, -1),
            searchDrugs("항응고제").map { it.restrictionDays }
        )
    }

    @Test
    fun `the 판정기준 drugs are listed with their periods`() {
        fun days(name: String) = PROHIBITED_DRUGS.filter { it.ingredientName == name }.map { it.restrictionDays }
        assertEquals(listOf(1), days("감기약·두통약"))
        assertEquals(listOf(3), days("디피리다몰"))
        assertEquals(listOf(3), days("항생제(감기 치료 목적)"))
        assertEquals(listOf(7), days("항생제(먹는 약)"))
        assertEquals(listOf(28, -1), days("메토트렉세이트"))
        assertEquals(listOf(7, -1), days("와파린/헤파린/히루딘"))
        assertEquals(listOf(56), days("생물학적 동등성 시험(성분을 아는 경우)"))
        assertEquals(listOf(180), days("데노수맙"))
        assertEquals(listOf(730), days("프로필티오우라실/메티마졸"))
        // 당일: possible again the next day.
        val coldMedicine = PROHIBITED_DRUGS.first { it.ingredientName == "감기약·두통약" }
        assertEquals(LocalDate.of(2026, 11, 4), nextEligibleDonationDate(coldMedicine, LocalDate.of(2026, 11, 3)))
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
