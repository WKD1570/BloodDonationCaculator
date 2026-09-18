package com.example.myapplication.domain

import com.example.myapplication.model.DonationType
import com.example.myapplication.model.DonorProfile
import com.example.myapplication.model.Sex
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalEligibilityCalculatorTest {

    private fun profileAged(age: Int, donatedAge60To64: Boolean = false): DonorProfile = DonorProfile(
        weightKg = 70.0,
        heightCm = 175.0,
        sex = Sex.MALE,
        birthDate = LocalDate.now().minusYears(age.toLong())
    ).copy(donatedAge60To64 = donatedAge60To64)

    @Test
    fun `65 or older without prior donation between 60 and 64 is ineligible for whole blood`() {
        val result = checkPhysicalEligibility(profileAged(65), DonationType.WHOLE_BLOOD)
        assertFalse(result.isEligible)
    }

    @Test
    fun `65 or older with prior donation between 60 and 64 is eligible for whole blood`() {
        val result = checkPhysicalEligibility(profileAged(65, donatedAge60To64 = true), DonationType.WHOLE_BLOOD)
        assertTrue(result.isEligible)
    }

    @Test
    fun `65 or older with prior donation between 60 and 64 is eligible for plasma`() {
        val result = checkPhysicalEligibility(profileAged(65, donatedAge60To64 = true), DonationType.PLASMA)
        assertTrue(result.isEligible)
    }

    @Test
    fun `under 65 does not require the prior donation flag`() {
        val result = checkPhysicalEligibility(profileAged(64), DonationType.WHOLE_BLOOD)
        assertTrue(result.isEligible)
    }

    @Test
    fun `65 or older platelet donation is rejected by age range alone, not double-counted`() {
        val result = checkPhysicalEligibility(profileAged(65), DonationType.PLATELET)
        assertFalse(result.isEligible)
        assertTrue(result.reasons.size == 1)
    }
}
