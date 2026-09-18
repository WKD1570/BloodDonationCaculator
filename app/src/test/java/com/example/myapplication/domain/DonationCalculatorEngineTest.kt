package com.example.myapplication.domain

import com.example.myapplication.model.DonationRecord
import com.example.myapplication.model.DonationType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DonationCalculatorEngineTest {

    @Test
    fun `volumeMl uses the recorded donated volume when present`() {
        val record = DonationRecord(
            id = 1,
            type = DonationType.WHOLE_BLOOD,
            date = LocalDate.of(2026, 7, 21),
            donatedVolumeMl = 430
        )
        assertEquals(430, record.volumeMl())
    }

    @Test
    fun `volumeMl falls back to the standard age-based default when no recorded volume`() {
        val record = DonationRecord(
            id = 1,
            type = DonationType.WHOLE_BLOOD,
            date = LocalDate.of(2026, 7, 21),
            birthDate = LocalDate.of(1990, 1, 1)
        )
        assertEquals(430, record.volumeMl())
    }

    @Test
    fun `volumeMl falls back to the reduced age-based default for 16-17 year olds`() {
        val record = DonationRecord(
            id = 1,
            type = DonationType.WHOLE_BLOOD,
            date = LocalDate.of(2026, 7, 21),
            birthDate = LocalDate.of(2009, 8, 1)
        )
        assertEquals(350, record.volumeMl())
    }

    @Test
    fun `recorded donated volume overrides the age-based default`() {
        val record = DonationRecord(
            id = 1,
            type = DonationType.WHOLE_BLOOD,
            date = LocalDate.of(2026, 7, 21),
            birthDate = LocalDate.of(1990, 1, 1),
            donatedVolumeMl = 350
        )
        assertEquals(350, record.volumeMl())
    }

    @Test
    fun `earliestEligibleDate allows a historical donation dated before a later-dated existing record`() {
        // Reported bug: a Whole Blood donation on 2026-07-21 must not block adding an earlier
        // Plasma donation on 2026-07-03 - the whole blood record hasn't happened yet as of 07-03.
        val wholeBlood = DonationRecord(id = 1, type = DonationType.WHOLE_BLOOD, date = LocalDate.of(2026, 7, 21))
        val candidateDate = LocalDate.of(2026, 7, 3)
        val eligibleDate = earliestEligibleDate(DonationType.PLASMA, listOf(wholeBlood), null, candidateDate)
        assertFalse(candidateDate.isBefore(eligibleDate))
    }

    @Test
    fun `earliestEligibleDate still blocks a donation within the interval of a preceding donation`() {
        val plasma = DonationRecord(id = 1, type = DonationType.PLASMA, date = LocalDate.of(2026, 7, 21))
        val candidateDate = LocalDate.of(2026, 7, 25)
        val eligibleDate = earliestEligibleDate(DonationType.PLASMA, listOf(plasma), null, candidateDate)
        assertEquals(LocalDate.of(2026, 8, 4), eligibleDate)
        assertTrue(candidateDate.isBefore(eligibleDate))
    }

    @Test
    fun `earliestEligibleDate ignores later-dated records when validating a mid-history insert`() {
        val early = DonationRecord(id = 1, type = DonationType.WHOLE_BLOOD, date = LocalDate.of(2026, 1, 1))
        val later = DonationRecord(id = 2, type = DonationType.WHOLE_BLOOD, date = LocalDate.of(2026, 4, 10))
        val candidateDate = LocalDate.of(2026, 1, 20)
        val eligibleDate = earliestEligibleDate(DonationType.PLASMA, listOf(early, later), null, candidateDate)
        // Bound only by `early` (Jan 1 + 56 days); `later` (Apr 10) postdates the candidate and must not apply.
        assertEquals(LocalDate.of(2026, 2, 26), eligibleDate)
    }

    @Test
    fun `calcNext next-eligible-date from today is unaffected by the asOfDate filter`() {
        val record = DonationRecord(id = 1, type = DonationType.WHOLE_BLOOD, date = LocalDate.of(2026, 1, 1))
        val today = LocalDate.of(2026, 1, 10)
        val result = calcNext(DonationType.PLASMA, listOf(record), today)
        assertEquals(LocalDate.of(2026, 2, 26), result.nextDate)
    }
}
