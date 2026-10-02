package com.example.myapplication.domain

import com.example.myapplication.model.DonationRecord
import com.example.myapplication.model.DonationType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DonationCalculatorEngineTest {

    @Test
    fun `volumeMl counts a 400mL whole blood donation as 430mL`() {
        val record = DonationRecord(
            id = 1,
            type = DonationType.WHOLE_BLOOD,
            date = LocalDate.of(2026, 7, 21),
            donatedVolumeMl = 430
        )
        assertEquals(430, record.volumeMl())
    }

    @Test
    fun `volumeMl counts a 320mL whole blood donation as 430mL too`() {
        val record = DonationRecord(
            id = 1,
            type = DonationType.WHOLE_BLOOD,
            date = LocalDate.of(2026, 7, 21),
            donatedVolumeMl = 350
        )
        assertEquals(430, record.volumeMl())
    }

    @Test
    fun `volumeMl counts a whole blood donation saved without a volume as 430mL`() {
        val record = DonationRecord(
            id = 1,
            type = DonationType.WHOLE_BLOOD,
            date = LocalDate.of(2026, 7, 21),
            birthDate = LocalDate.of(1990, 1, 1)
        )
        assertEquals(430, record.volumeMl())
    }

    @Test
    fun `volumeMl counts a 16-17 year old's whole blood donation as 430mL too`() {
        val record = DonationRecord(
            id = 1,
            type = DonationType.WHOLE_BLOOD,
            date = LocalDate.of(2026, 7, 21),
            birthDate = LocalDate.of(2009, 8, 1)
        )
        assertEquals(430, record.volumeMl())
    }

    @Test
    fun `earliestEligibleDate allows a historical donation dated before a later-dated existing record`() {
        // Reported bug: a Whole Blood donation on 2026-07-21 must not block adding an earlier
        // Plasma donation on 2026-07-03 - the whole blood record hasn't happened yet as of 07-03.
        val wholeBlood = DonationRecord(id = 1, type = DonationType.WHOLE_BLOOD, date = LocalDate.of(2026, 7, 21))
        val candidateDate = LocalDate.of(2026, 7, 3)
        val eligibleDate = earliestEligibleDate(DonationType.PLASMA, listOf(wholeBlood), candidateDate)
        assertFalse(candidateDate.isBefore(eligibleDate))
    }

    @Test
    fun `earliestEligibleDate still blocks a donation within the interval of a preceding donation`() {
        val plasma = DonationRecord(id = 1, type = DonationType.PLASMA, date = LocalDate.of(2026, 7, 21))
        val candidateDate = LocalDate.of(2026, 7, 25)
        val eligibleDate = earliestEligibleDate(DonationType.PLASMA, listOf(plasma), candidateDate)
        assertEquals(LocalDate.of(2026, 8, 4), eligibleDate)
        assertTrue(candidateDate.isBefore(eligibleDate))
    }

    @Test
    fun `earliestEligibleDate ignores later-dated records when validating a mid-history insert`() {
        val early = DonationRecord(id = 1, type = DonationType.WHOLE_BLOOD, date = LocalDate.of(2026, 1, 1))
        val later = DonationRecord(id = 2, type = DonationType.WHOLE_BLOOD, date = LocalDate.of(2026, 4, 10))
        val candidateDate = LocalDate.of(2026, 1, 20)
        val eligibleDate = earliestEligibleDate(DonationType.PLASMA, listOf(early, later), candidateDate)
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

    @Test
    fun `withinAnnualWindow keeps a donation on the last day it still counts`() {
        val today = LocalDate.of(2026, 9, 30)
        val record = DonationRecord(
            id = 1,
            type = DonationType.WHOLE_BLOOD,
            date = today.minusDays(365)
        )
        assertEquals(listOf(record), listOf(record).withinAnnualWindow(today))
    }

    @Test
    fun `withinAnnualWindow drops a donation once 366 days have passed`() {
        val today = LocalDate.of(2026, 9, 30)
        val record = DonationRecord(
            id = 1,
            type = DonationType.WHOLE_BLOOD,
            date = today.minusDays(366)
        )
        assertEquals(emptyList<DonationRecord>(), listOf(record).withinAnnualWindow(today))
    }

    @Test
    fun `withinAnnualWindow keeps only the past year of donations`() {
        val today = LocalDate.of(2026, 9, 30)
        val expired = DonationRecord(id = 1, type = DonationType.WHOLE_BLOOD, date = LocalDate.of(2024, 5, 1))
        val recent = DonationRecord(id = 2, type = DonationType.WHOLE_BLOOD, date = LocalDate.of(2026, 5, 1))
        val kept = listOf(expired, recent).withinAnnualWindow(today)
        assertEquals(listOf(recent), kept)
        assertEquals(430, kept.sumOf { it.volumeMl() })
    }

    // 4 whole blood donations (430mL each) + 1 plasma donation (45mL) = 1,765mL in the past year.
    private val nearAnnualVolumeCap = listOf(
        DonationRecord(id = 1, type = DonationType.WHOLE_BLOOD, date = LocalDate.of(2025, 11, 1)),
        DonationRecord(id = 2, type = DonationType.WHOLE_BLOOD, date = LocalDate.of(2026, 1, 1)),
        DonationRecord(id = 3, type = DonationType.WHOLE_BLOOD, date = LocalDate.of(2026, 3, 1)),
        DonationRecord(id = 4, type = DonationType.PLASMA, date = LocalDate.of(2026, 5, 1)),
        DonationRecord(id = 5, type = DonationType.WHOLE_BLOOD, date = LocalDate.of(2026, 7, 1))
    )

    @Test
    fun `earliestEligibleDate blocks a whole blood donation that would exceed the annual volume cap`() {
        // 1,765 + 430mL = 2,195mL, over 2,160mL - blocked until the oldest donation (2025-11-01)
        // expires 366 days later.
        val eligibleDate = earliestEligibleDate(DonationType.WHOLE_BLOOD, nearAnnualVolumeCap, LocalDate.of(2026, 9, 30))
        assertEquals(LocalDate.of(2026, 11, 2), eligibleDate)
    }

    @Test
    fun `320mL whole blood donations count 430mL toward the annual volume cap`() {
        // The same donations given as 320mL still total 1,765mL, so the next whole blood donation is
        // blocked just the same. Counted at 350mL they'd total 1,445mL and leave room for it.
        val records = nearAnnualVolumeCap.map {
            if (it.type == DonationType.WHOLE_BLOOD) it.copy(donatedVolumeMl = 350) else it
        }
        val eligibleDate = earliestEligibleDate(DonationType.WHOLE_BLOOD, records, LocalDate.of(2026, 9, 30))
        assertEquals(LocalDate.of(2026, 11, 2), eligibleDate)
    }

    @Test
    fun `wholeBloodStatedVolumeMl is the certificate amount, without the diagnostic draw`() {
        val record = DonationRecord(
            id = 1,
            type = DonationType.WHOLE_BLOOD,
            date = LocalDate.of(2026, 9, 19),
            donatedVolumeMl = 350
        )
        assertEquals(320, record.wholeBloodStatedVolumeMl())
    }

    @Test
    fun `wholeBloodStatedVolumeMl is 400mL for a whole blood record saved without a volume`() {
        val record = DonationRecord(id = 1, type = DonationType.WHOLE_BLOOD, date = LocalDate.of(2026, 9, 19))
        assertEquals(400, record.wholeBloodStatedVolumeMl())
    }

    @Test
    fun `wholeBloodStatedVolumeMl is null for plasma even with a scanned certificate amount`() {
        val record = DonationRecord(
            id = 1,
            type = DonationType.PLASMA,
            date = LocalDate.of(2026, 9, 19),
            donatedVolumeMl = 600
        )
        assertNull(record.wholeBloodStatedVolumeMl())
    }

    @Test
    fun `a stem cell donation blocks every donation type for 6 calendar months`() {
        val stemCell = DonationRecord(id = 1, type = DonationType.STEM_CELL, date = LocalDate.of(2026, 3, 15))
        val result = calcNext(DonationType.PLASMA, listOf(stemCell), LocalDate.of(2026, 4, 1))
        assertEquals(LocalDate.of(2026, 9, 15), result.nextDate)
        assertTrue(result.reasons.any { "6개월" in it.text })
    }

    @Test
    fun `a white blood cell donation blocks the next donation for 14 days`() {
        val whiteBloodCell = DonationRecord(id = 1, type = DonationType.WHITE_BLOOD_CELL, date = LocalDate.of(2026, 9, 1))
        val result = calcNext(DonationType.PLATELET, listOf(whiteBloodCell), LocalDate.of(2026, 9, 5))
        assertEquals(LocalDate.of(2026, 9, 15), result.nextDate)
    }

    // 4 whole blood donations (430mL each) = 1,720mL: on its own, another whole blood donation (430mL)
    // on 2026-09-30 still fits under the 2,160mL cap (2,150mL), and only the 56-day interval to 08-26 binds.
    private val fourWholeBloodDonations = listOf(
        DonationRecord(id = 1, type = DonationType.WHOLE_BLOOD, date = LocalDate.of(2025, 11, 1)),
        DonationRecord(id = 2, type = DonationType.WHOLE_BLOOD, date = LocalDate.of(2026, 1, 1)),
        DonationRecord(id = 3, type = DonationType.WHOLE_BLOOD, date = LocalDate.of(2026, 3, 1)),
        DonationRecord(id = 4, type = DonationType.WHOLE_BLOOD, date = LocalDate.of(2026, 7, 1))
    )

    @Test
    fun `white blood cell donations count 90mL each toward the annual volume cap`() {
        // 1,720 + 4 x 90 + a 90mL platelet donation = 2,170mL, over 2,160mL - blocked until the
        // 2025-11-01 donation expires. Counted at plasma's 45mL they'd total 1,990mL and pass.
        val records = fourWholeBloodDonations + listOf(
            DonationRecord(id = 5, type = DonationType.WHITE_BLOOD_CELL, date = LocalDate.of(2026, 5, 1)),
            DonationRecord(id = 6, type = DonationType.WHITE_BLOOD_CELL, date = LocalDate.of(2026, 5, 15)),
            DonationRecord(id = 7, type = DonationType.WHITE_BLOOD_CELL, date = LocalDate.of(2026, 8, 27)),
            DonationRecord(id = 8, type = DonationType.WHITE_BLOOD_CELL, date = LocalDate.of(2026, 9, 10))
        )
        val eligibleDate = earliestEligibleDate(DonationType.PLATELET, records, LocalDate.of(2026, 9, 30))
        assertEquals(LocalDate.of(2026, 11, 2), eligibleDate)
    }

    @Test
    fun `a stem cell donation counts 30mL toward the annual volume cap`() {
        // 1,720 + 30 + 430 = 2,180mL, over 2,160mL - blocked until the 2025-11-01 donation expires. The
        // stem cell donation's own 6-month block (to 2026-08-01) has already passed.
        val records = fourWholeBloodDonations +
            DonationRecord(id = 5, type = DonationType.STEM_CELL, date = LocalDate.of(2026, 2, 1))
        val eligibleDate = earliestEligibleDate(DonationType.WHOLE_BLOOD, records, LocalDate.of(2026, 9, 30))
        assertEquals(LocalDate.of(2026, 11, 2), eligibleDate)
    }

    @Test
    fun `recording a stem cell donation is never blocked by blood donation limits`() {
        // 9 days after a whole blood donation, which blocks other blood donations for 56 days.
        val wholeBlood = DonationRecord(id = 1, type = DonationType.WHOLE_BLOOD, date = LocalDate.of(2026, 9, 1))
        val candidateDate = LocalDate.of(2026, 9, 10)
        val eligibleDate = earliestEligibleDate(DonationType.STEM_CELL, listOf(wholeBlood), candidateDate)
        assertFalse(candidateDate.isBefore(eligibleDate))
    }
}
