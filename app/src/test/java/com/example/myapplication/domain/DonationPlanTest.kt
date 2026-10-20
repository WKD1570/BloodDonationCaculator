package com.example.myapplication.domain

import com.example.myapplication.model.DonationRecord
import com.example.myapplication.model.DonationType
import com.example.myapplication.model.DonationType.PLASMA
import com.example.myapplication.model.DonationType.PLATELET
import com.example.myapplication.model.DonationType.WHOLE_BLOOD
import com.example.myapplication.model.MedicationRecord
import com.example.myapplication.model.PlannedDonation
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DonationPlanTest {

    private fun day(month: Int, dayOfMonth: Int, year: Int = 2026) = LocalDate.of(year, month, dayOfMonth)
    private fun record(id: Long, type: DonationType, date: LocalDate) = DonationRecord(id = id, type = type, date = date)
    private fun planned(id: Long, type: DonationType, date: LocalDate) = PlannedDonation(id = id, type = type, date = date)

    /** Whole blood on 8/29 holds every type back 56 days, to 10/24. */
    private val history = listOf(record(1, WHOLE_BLOOD, day(8, 29)))

    private fun check(plan: List<PlannedDonation>, medications: List<MedicationRecord> = emptyList(), health: List<HealthRestriction> = emptyList()) =
        checkPlan(plan, history, medications, health)

    @Test
    fun `a planned donation on or after its first allowed day is allowed`() {
        val result = check(listOf(planned(10, PLATELET, day(10, 24)))).single()
        assertTrue(result.isAllowed)
        assertEquals(day(10, 24), result.allowedFrom)
    }

    @Test
    fun `one inside a waiting period isn't, and says from when and why`() {
        val result = check(listOf(planned(10, PLATELET, day(10, 23)))).single()
        assertFalse(result.isAllowed)
        assertEquals(day(10, 24), result.allowedFrom)
        assertTrue(result.reasons.isNotEmpty())
    }

    @Test
    fun `an allowed planned donation holds back the ones after it`() {
        val results = check(listOf(planned(10, PLATELET, day(10, 24)), planned(11, PLASMA, day(11, 1))))
        assertTrue(results[0].isAllowed)
        assertFalse(results[1].isAllowed)
        assertEquals(day(11, 7), results[1].allowedFrom) // 혈소판 on 10/24 + 14 days
    }

    @Test
    fun `a planned donation that isn't allowed holds back nothing`() {
        // If 10/23 counted, it would block 혈장 until 11/6.
        val results = check(listOf(planned(10, PLATELET, day(10, 23)), planned(11, PLASMA, day(10, 30))))
        assertFalse(results[0].isAllowed)
        assertTrue(results[1].isAllowed)
    }

    @Test
    fun `results come back in date order whatever order the plan is in`() {
        val results = check(listOf(planned(11, PLASMA, day(11, 7)), planned(10, PLATELET, day(10, 24))))
        assertEquals(listOf(10L, 11L), results.map { it.planned.id })
        assertTrue(results.all { it.isAllowed })
    }

    @Test
    fun `allowed planned donations count toward the yearly count and volume`() {
        val wholeBlood = listOf(day(11, 1, 2025), day(1, 1), day(3, 1), day(5, 1)).mapIndexed { i, d -> record(i + 1L, WHOLE_BLOOD, d) }
        val plan = listOf(planned(10, WHOLE_BLOOD, day(7, 1)), planned(11, WHOLE_BLOOD, day(9, 1)))

        val results = checkPlan(plan, wholeBlood, emptyList(), emptyList())

        assertTrue(results[0].isAllowed) // the 5th whole blood within a year, 2,150 mL
        assertFalse(results[1].isAllowed) // a 6th, and 2,580 mL
        assertEquals(day(11, 2), results[1].allowedFrom) // once 2025-11-01 drops out (+366 days)
    }

    @Test
    fun `medications bind planned days, and a lifetime ban leaves no allowed day`() {
        val aspirin = MedicationRecord(ingredientName = "아스피린", representativeBrands = "", intakeDate = day(10, 28), restrictionDays = 3, restrictionText = "")
        val waiting = check(listOf(planned(10, PLATELET, day(10, 29))), medications = listOf(aspirin)).single()
        assertFalse(waiting.isAllowed)
        assertEquals(day(10, 31), waiting.allowedFrom)

        val banned = check(listOf(planned(10, PLATELET, day(12, 1))), medications = listOf(aspirin.copy(restrictionDays = -1))).single()
        assertFalse(banned.isAllowed)
        assertNull(banned.allowedFrom)
    }

    @Test
    fun `a health restriction binds only the types it blocks`() {
        val malariaStay = HealthRestriction(
            title = "말라리아 위험지역", periodLabel = "1년", detail = "", countedFrom = day(1, 1),
            eligibleFrom = day(1, 1, 2027), allowedTypes = setOf(PLASMA), sources = emptySet()
        )
        val results = check(listOf(planned(10, PLASMA, day(11, 1)), planned(11, WHOLE_BLOOD, day(12, 20))), health = listOf(malariaStay))
        assertTrue(results[0].isAllowed)
        assertFalse(results[1].isAllowed)
        assertEquals(day(1, 1, 2027), results[1].allowedFrom)
    }

    @Test
    fun `filling plans every earliest day for the type within the period`() {
        var id = 100L
        val plan = fillPlan(PLATELET, day(10, 20), day(12, 31), emptyList(), history, emptyList(), emptyList()) { id++ }
        assertEquals(listOf(day(10, 24), day(11, 7), day(11, 21), day(12, 5), day(12, 19)), plan.map { it.date })
        assertTrue(plan.all { it.type == PLATELET })
        assertEquals(plan.size, plan.map { it.id }.toSet().size)
    }

    @Test
    fun `filling never stops an already planned donation from happening`() {
        val wholeBlood = planned(1, WHOLE_BLOOD, day(11, 14))
        var id = 100L
        val plan = fillPlan(PLATELET, day(10, 20), day(11, 30), listOf(wholeBlood), history, emptyList(), emptyList()) { id++ }

        // 혈소판 on 11/7 would push 전혈 11/14 back to 11/21, and after 11/14 전혈 holds everything to 2027.
        assertEquals(listOf(day(10, 24), day(11, 14)), plan.map { it.date }.sorted())
        assertTrue(checkPlan(plan, history, emptyList(), emptyList()).all { it.isAllowed })
    }

    @Test
    fun `filling adds nothing under a lifetime ban`() {
        val ban = MedicationRecord(ingredientName = "아시트레틴", representativeBrands = "", intakeDate = day(1, 1), restrictionDays = -1, restrictionText = "")
        val plan = fillPlan(PLATELET, day(10, 20), day(12, 31), emptyList(), history, listOf(ban), emptyList()) { 1L }
        assertTrue(plan.isEmpty())
    }

    @Test
    fun `planned days before today are dropped, today's kept`() {
        val plan = listOf(planned(1, PLATELET, day(10, 19)), planned(2, PLATELET, day(10, 20)), planned(3, PLASMA, day(11, 3)))
        assertEquals(listOf(2L, 3L), upcomingPlan(plan, day(10, 20)).map { it.id })
    }

    @Test
    fun `a real donation clears the plan for its day and type only`() {
        val plan = listOf(planned(1, PLATELET, day(10, 24)), planned(2, PLASMA, day(11, 7)), planned(3, PLATELET, day(11, 21)))
        assertEquals(listOf(2L, 3L), planWithout(plan, record(9, PLATELET, day(10, 24))).map { it.id })
        assertEquals(listOf(1L, 2L, 3L), planWithout(plan, record(9, WHOLE_BLOOD, day(10, 24))).map { it.id })
    }

    @Test
    fun `the 1-year volume as of a day counts records and only the allowed planned donations`() {
        val records = history + record(2, PLATELET, day(9, 1, 2025)) // older than a year by 11/7
        val checks = checkPlan(
            listOf(planned(10, PLATELET, day(10, 24)), planned(11, PLATELET, day(11, 7)), planned(12, PLASMA, day(11, 10))),
            records, emptyList(), emptyList()
        )
        // 전혈 430 + 혈소판 90 + 90; 혈장 on 11/10 is inside the 혈소판 wait, so it doesn't happen.
        assertEquals(610, plannedAnnualVolume(checks, records, day(11, 10)))
    }
}
