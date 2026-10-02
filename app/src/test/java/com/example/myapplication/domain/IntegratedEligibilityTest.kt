package com.example.myapplication.domain

import com.example.myapplication.model.DonationRecord
import com.example.myapplication.model.DonationType
import com.example.myapplication.model.MedicationRecord
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegratedEligibilityTest {

    private val today = LocalDate.of(2026, 9, 19)

    private fun medication(
        ingredient: String,
        intakeDate: LocalDate,
        restrictionDays: Int
    ) = MedicationRecord(
        id = 0,
        ingredientName = ingredient,
        representativeBrands = "",
        intakeDate = intakeDate,
        restrictionDays = restrictionDays,
        restrictionText = ""
    )

    private fun wholeBlood(date: LocalDate) =
        DonationRecord(id = 1, type = DonationType.WHOLE_BLOOD, date = date)

    // ---- medicationConstraint (Date B) ----

    @Test
    fun `no medication on record yields no constraint`() {
        assertEquals(MedicationConstraint.None, medicationConstraint(emptyList()))
    }

    @Test
    fun `a single entry ends its restriction intakeDate plus restrictionDays later`() {
        val aspirin = medication("아스피린", LocalDate.of(2026, 9, 17), 3)
        val constraint = medicationConstraint(listOf(aspirin))
        assertEquals(
            MedicationConstraint.EligibleFrom(LocalDate.of(2026, 9, 20), aspirin),
            constraint
        )
    }

    @Test
    fun `the binding entry is the one whose window ends last, not the latest intake`() {
        // 두타스테라이드 taken five months ago still outlasts 아스피린 taken two days ago.
        val dutasteride = medication("두타스테라이드", LocalDate.of(2026, 4, 20), 180)
        val aspirin = medication("아스피린", LocalDate.of(2026, 9, 17), 3)

        val constraint = medicationConstraint(listOf(aspirin, dutasteride))

        assertEquals(
            MedicationConstraint.EligibleFrom(LocalDate.of(2026, 10, 17), dutasteride),
            constraint
        )
    }

    @Test
    fun `a permanent entry outranks a temporary one regardless of order or dates`() {
        val etretinate = medication("에트레티네이트", LocalDate.of(2020, 1, 1), -1)
        val dutasteride = medication("두타스테라이드", LocalDate.of(2026, 9, 1), 180)

        val constraint = medicationConstraint(listOf(dutasteride, etretinate))

        assertEquals(MedicationConstraint.Permanent(etretinate), constraint)
    }

    // ---- integratedNextEligible (max of Date A and Date B) ----

    @Test
    fun `with no medication the result matches the donation-only calculation`() {
        val records = listOf(wholeBlood(LocalDate.of(2026, 9, 1)))
        val expected = calcNext(DonationType.WHOLE_BLOOD, records, today)

        val result = integratedNextEligible(DonationType.WHOLE_BLOOD, records, emptyList(), today)

        assertEquals(
            IntegratedNextResult.Eligible(expected.nextDate, expected.dd, expected.reasons),
            result
        )
    }

    @Test
    fun `medication wins when its window ends after the donation interval`() {
        // Date A: 2026-09-01 + 56 days = 2026-10-27. Date B: 2026-09-15 + 180 = 2027-03-14.
        val records = listOf(wholeBlood(LocalDate.of(2026, 9, 1)))
        val meds = listOf(medication("두타스테라이드", LocalDate.of(2026, 9, 15), 180))

        val result = integratedNextEligible(DonationType.WHOLE_BLOOD, records, meds, today)

        assertEquals(
            LocalDate.of(2027, 3, 14),
            (result as IntegratedNextResult.Eligible).nextDate
        )
    }

    @Test
    fun `donation history wins when it binds later than the medication`() {
        // Date A: 2026-09-01 + 56 days = 2026-10-27. Date B: 2026-09-18 + 3 = 2026-09-21.
        val records = listOf(wholeBlood(LocalDate.of(2026, 9, 1)))
        val meds = listOf(medication("아스피린", LocalDate.of(2026, 9, 18), 3))

        val result = integratedNextEligible(DonationType.WHOLE_BLOOD, records, meds, today)

        assertEquals(
            LocalDate.of(2026, 10, 27),
            (result as IntegratedNextResult.Eligible).nextDate
        )
    }

    @Test
    fun `a medication window that has already elapsed does not move the date`() {
        val records = listOf(wholeBlood(LocalDate.of(2026, 9, 1)))
        val meds = listOf(medication("아스피린", LocalDate.of(2026, 1, 5), 3))

        val result = integratedNextEligible(DonationType.WHOLE_BLOOD, records, meds, today)

        assertEquals(
            calcNext(DonationType.WHOLE_BLOOD, records, today).nextDate,
            (result as IntegratedNextResult.Eligible).nextDate
        )
    }

    @Test
    fun `medication alone still produces a date when there is no donation history`() {
        val meds = listOf(medication("이소트레티노인", LocalDate.of(2026, 9, 10), 28))

        val result = integratedNextEligible(DonationType.PLASMA, emptyList(), meds, today)

        assertEquals(
            LocalDate.of(2026, 10, 8),
            (result as IntegratedNextResult.Eligible).nextDate
        )
    }

    @Test
    fun `a permanent restriction overrides the donation history entirely`() {
        val records = listOf(wholeBlood(LocalDate.of(2020, 1, 1)))
        val etretinate = medication("에트레티네이트", LocalDate.of(2021, 5, 4), -1)

        val result = integratedNextEligible(DonationType.WHOLE_BLOOD, records, listOf(etretinate), today)

        assertTrue(result is IntegratedNextResult.PermanentlyProhibited)
        assertEquals("에트레티네이트", (result as IntegratedNextResult.PermanentlyProhibited).cause)
    }

    @Test
    fun `a permanent restriction applies to every donation type`() {
        val meds = listOf(medication("에트레티네이트", LocalDate.of(2021, 5, 4), -1))
        DonationType.entries.forEach { type ->
            val result = integratedNextEligible(type, emptyList(), meds, today)
            assertTrue("$type should be permanently prohibited", result is IntegratedNextResult.PermanentlyProhibited)
        }
    }

    @Test
    fun `an active medication restriction is surfaced as a reason`() {
        val meds = listOf(medication("두타스테라이드", LocalDate.of(2026, 9, 15), 180))

        val result = integratedNextEligible(DonationType.PLASMA, emptyList(), meds, today)

        assertTrue(result.reasons.any { it.text.contains("두타스테라이드") })
    }

    @Test
    fun `an elapsed medication restriction is not listed as a reason`() {
        val meds = listOf(medication("아스피린", LocalDate.of(2026, 1, 5), 3))

        val result = integratedNextEligible(DonationType.PLASMA, emptyList(), meds, today)

        assertTrue(result.reasons.none { it.text.contains("아스피린") })
    }

    @Test
    fun `a stem cell donation pushes each planned type back 6 months, and only the regular types are planned`() {
        val stemCell = DonationRecord(id = 1, type = DonationType.STEM_CELL, date = LocalDate.of(2026, 3, 15))
        val next = nextEligibleByType(listOf(stemCell), emptyList(), LocalDate.of(2026, 4, 1))

        assertEquals(setOf(DonationType.WHOLE_BLOOD, DonationType.PLASMA, DonationType.PLATELET), next.keys)
        next.values.forEach { result ->
            assertEquals(LocalDate.of(2026, 9, 15), (result as IntegratedNextResult.Eligible).nextDate)
        }
    }

    // ---- 감염병·체류 restrictions ----

    private fun healthRestriction(eligibleFrom: LocalDate?, allowed: Set<DonationType> = emptySet()) = HealthRestriction(
        title = "태국 체류",
        periodLabel = "체류 종료 후 1년",
        detail = "",
        countedFrom = eligibleFrom?.minusYears(1),
        eligibleFrom = eligibleFrom,
        allowedTypes = allowed,
        sources = setOf(HistorySource.Stay(1))
    )

    @Test
    fun `a malaria stay pushes whole blood and platelets back but leaves plasma alone`() {
        val trip = healthRestriction(LocalDate.of(2027, 3, 7), allowed = setOf(DonationType.PLASMA))

        val next = nextEligibleByType(emptyList(), emptyList(), today, listOf(trip))

        assertEquals(LocalDate.of(2027, 3, 7), (next.getValue(DonationType.WHOLE_BLOOD) as IntegratedNextResult.Eligible).nextDate)
        assertEquals(LocalDate.of(2027, 3, 7), (next.getValue(DonationType.PLATELET) as IntegratedNextResult.Eligible).nextDate)
        val plasma = next.getValue(DonationType.PLASMA) as IntegratedNextResult.Eligible
        assertEquals(today, plasma.nextDate)
        assertTrue(plasma.reasons.isEmpty())
    }

    @Test
    fun `the later of donation history and a health restriction wins`() {
        val records = listOf(wholeBlood(LocalDate.of(2026, 9, 1)))
        val shortRestriction = healthRestriction(LocalDate.of(2026, 9, 25))

        val result = integratedNextEligible(DonationType.WHOLE_BLOOD, records, emptyList(), today, listOf(shortRestriction))

        assertEquals(LocalDate.of(2026, 10, 27), (result as IntegratedNextResult.Eligible).nextDate)
        assertTrue(result.reasons.any { it.text.contains("태국 체류") })
    }

    @Test
    fun `an elapsed health restriction is not listed as a reason`() {
        val result = integratedNextEligible(DonationType.WHOLE_BLOOD, emptyList(), emptyList(), today, listOf(healthRestriction(LocalDate.of(2026, 1, 1))))

        assertEquals(today, (result as IntegratedNextResult.Eligible).nextDate)
        assertTrue(result.reasons.isEmpty())
    }

    @Test
    fun `a permanent health restriction prohibits the types it blocks`() {
        val vcjd = healthRestriction(null).copy(title = "영국 체류(vCJD)")

        val result = integratedNextEligible(DonationType.PLASMA, emptyList(), emptyList(), today, listOf(vcjd))

        assertEquals("영국 체류(vCJD)", (result as IntegratedNextResult.PermanentlyProhibited).cause)
    }
}
