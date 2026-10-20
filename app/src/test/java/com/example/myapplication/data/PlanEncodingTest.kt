package com.example.myapplication.data

import com.example.myapplication.model.DonationType
import com.example.myapplication.model.PlannedDonation
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class PlanEncodingTest {

    private val plan = listOf(
        PlannedDonation(1760000000001, DonationType.PLATELET, LocalDate.of(2026, 10, 24)),
        PlannedDonation(1760000000002, DonationType.WHOLE_BLOOD, LocalDate.of(2026, 12, 19))
    )

    @Test
    fun `a plan survives being written and read back`() {
        assertEquals(plan, decodePlan(encodePlan(plan)))
    }

    @Test
    fun `nothing saved reads as an empty plan`() {
        assertEquals(emptyList<PlannedDonation>(), decodePlan(""))
        assertEquals(emptyList<PlannedDonation>(), decodePlan(encodePlan(emptyList())))
    }

    @Test
    fun `malformed lines are dropped and the rest kept`() {
        val raw = listOf(
            "x|PLATELET|2026-10-24",
            "3|NOT_A_TYPE|2026-10-24",
            "4|PLASMA|2026-13-40",
            "5|PLASMA",
            "6|PLASMA|2026-11-07"
        ).joinToString("\n")
        assertEquals(listOf(PlannedDonation(6, DonationType.PLASMA, LocalDate.of(2026, 11, 7))), decodePlan(raw))
    }
}
