package com.example.myapplication.domain

import com.example.myapplication.model.DonationType
import com.example.myapplication.model.DonorProfile
import com.example.myapplication.model.Sex
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictedBloodVolumeTest {

    private fun volume(heightCm: Double, weightKg: Double, sex: Sex) = predictedBloodVolume(heightCm, weightKg, sex)!!

    @Test
    fun `a height and weight in the table give its value exactly`() {
        assertEquals(PredictedBloodVolume(3365, true), volume(152.0, 45.4, Sex.MALE))
        assertEquals(PredictedBloodVolume(7575, true), volume(188.0, 140.6, Sex.MALE))
        assertEquals(PredictedBloodVolume(3517, true), volume(163.0, 63.5, Sex.FEMALE))
    }

    @Test
    fun `values between table entries are interpolated`() {
        // Halfway between 1.73m and 1.78m at 68.0kg (male): (4689 + 4860) / 2.
        assertEquals(4775, volume(175.5, 68.0, Sex.MALE).volumeMl)
        // Halfway in both: the mean of the four surrounding cells (4689, 4860, 4835, 5007).
        assertEquals(4848, volume(175.5, 70.25, Sex.MALE).volumeMl)
    }

    @Test
    fun `the 230 lb row sits at 104_3kg, not the source's 103_4kg typo`() {
        assertEquals(5697, volume(168.0, 104.3, Sex.MALE).volumeMl)
    }

    @Test
    fun `the female 72_5kg by 1_68m cell is the corrected 3965`() {
        assertEquals(3965, volume(168.0, 72.5, Sex.FEMALE).volumeMl)
        // So the column keeps rising with weight around it.
        assertTrue(volume(168.0, 68.0, Sex.FEMALE).volumeMl < 3965)
        assertTrue(volume(168.0, 77.0, Sex.FEMALE).volumeMl > 3965)
    }

    @Test
    fun `outside the table the edge is extended and flagged`() {
        val short = volume(148.0, 50.0, Sex.FEMALE)
        assertFalse(short.withinTable)
        assertTrue(short.volumeMl < volume(152.0, 50.0, Sex.FEMALE).volumeMl)

        val tall = volume(192.0, 80.0, Sex.MALE)
        assertFalse(tall.withinTable)
        assertTrue(tall.volumeMl > volume(188.0, 80.0, Sex.MALE).volumeMl)
    }

    @Test
    fun `a non-positive height or weight has no prediction`() {
        assertNull(predictedBloodVolume(0.0, 60.0, Sex.MALE))
        assertNull(predictedBloodVolume(170.0, -1.0, Sex.FEMALE))
    }

    // ---- 성분헌혈's 4,000mL rule now reads the same table ----

    private fun adult(heightCm: Double, weightKg: Double, sex: Sex) = DonorProfile(
        weightKg = weightKg,
        heightCm = heightCm,
        sex = sex,
        birthDate = LocalDate.now().minusYears(30)
    )

    @Test
    fun `apheresis is judged by the table's predicted volume`() {
        // 여성 163cm 63.5kg: the table predicts 3,517mL (Nadler's formula said 3,826mL).
        val result = checkPhysicalEligibility(adult(163.0, 63.5, Sex.FEMALE), DonationType.PLASMA)
        assertFalse(result.isEligible)
        assertTrue(result.reasons.single().contains("3,517mL"))

        assertTrue(checkPhysicalEligibility(adult(163.0, 63.5, Sex.FEMALE), DonationType.WHOLE_BLOOD).isEligible)
        assertTrue(checkPhysicalEligibility(adult(173.0, 81.6, Sex.FEMALE), DonationType.PLATELET).isEligible)
    }
}
