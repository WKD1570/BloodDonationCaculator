package com.example.myapplication.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.myapplication.domain.toDateDigits
import com.example.myapplication.model.DonationRecord
import com.example.myapplication.model.DonationType
import com.example.myapplication.model.REGULAR_DONATION_TYPES
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class WholeBloodVolumeSelectorTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private var saved: DonationRecord? = null

    private fun showForm(initial: DonationRecord? = null, records: List<DonationRecord> = emptyList()) {
        composeTestRule.setContent {
            RecordFormDialog(
                initial = initial,
                records = records,
                types = REGULAR_DONATION_TYPES,
                onDismiss = {},
                onSave = { saved = it },
                onDelete = null
            )
        }
    }

    // The add form's only text field is the YYYY/MM/DD date.
    private fun saveWithTodaysDate() {
        composeTestRule.onNode(hasSetTextAction()).performTextInput(LocalDate.now().toDateDigits())
        composeTestRule.onNodeWithText("저장").performClick()
    }

    @Test
    fun volumeButtonsAppearOnlyUnderWholeBlood() {
        showForm()
        composeTestRule.onNodeWithText("320mL").assertDoesNotExist()

        composeTestRule.onNodeWithText("전혈헌혈").performClick()
        composeTestRule.onNodeWithText("320mL").assertIsDisplayed()
        composeTestRule.onNodeWithText("400mL").assertIsDisplayed()

        composeTestRule.onNodeWithText("혈장성분헌혈").performClick()
        composeTestRule.onNodeWithText("320mL").assertDoesNotExist()
        composeTestRule.onNodeWithText("400mL").assertDoesNotExist()
    }

    @Test
    fun switchingFromWholeBloodToPlasmaDropsTheWholeBloodVolume() {
        showForm()
        composeTestRule.onNodeWithText("전혈헌혈").performClick()
        composeTestRule.onNodeWithText("혈장성분헌혈").performClick()

        saveWithTodaysDate()
        assertEquals(DonationType.PLASMA, saved?.type)
        assertNull(saved?.donatedVolumeMl)
    }

    @Test
    fun wholeBloodStartsOn400mL() {
        showForm()
        composeTestRule.onNodeWithText("전혈헌혈").performClick()
        composeTestRule.onNodeWithText("400mL").assertIsSelected()

        saveWithTodaysDate()
        assertEquals(430, saved?.donatedVolumeMl) // 400mL + 30mL test draw
    }

    @Test
    fun picking320mLSavesItsDrawnVolume() {
        showForm()
        composeTestRule.onNodeWithText("전혈헌혈").performClick()
        composeTestRule.onNodeWithText("320mL").performClick()
        composeTestRule.onNodeWithText("320mL").assertIsSelected()

        saveWithTodaysDate()
        assertEquals(350, saved?.donatedVolumeMl) // 320mL + 30mL test draw
    }

    // 4 whole blood donations (430mL each) + 1 plasma donation (45mL) = 1,765mL in the past year, the
    // last one 91 days ago: a whole blood donation today is only limited by the 2,160mL annual cap,
    // which a 320mL donation (350mL drawn) fits under and a 400mL one (430mL drawn) doesn't.
    private fun nearAnnualVolumeCap(): List<DonationRecord> {
        val today = LocalDate.now()
        return listOf(
            DonationRecord(id = 1, type = DonationType.WHOLE_BLOOD, date = today.minusDays(333)),
            DonationRecord(id = 2, type = DonationType.WHOLE_BLOOD, date = today.minusDays(272)),
            DonationRecord(id = 3, type = DonationType.WHOLE_BLOOD, date = today.minusDays(213)),
            DonationRecord(id = 4, type = DonationType.PLASMA, date = today.minusDays(152)),
            DonationRecord(id = 5, type = DonationType.WHOLE_BLOOD, date = today.minusDays(91))
        )
    }

    @Test
    fun a320mLDonationThatFitsUnderTheAnnualVolumeCapSaves() {
        showForm(records = nearAnnualVolumeCap())
        composeTestRule.onNodeWithText("전혈헌혈").performClick()
        composeTestRule.onNodeWithText("320mL").performClick()

        saveWithTodaysDate()
        assertEquals(350, saved?.donatedVolumeMl)
    }

    @Test
    fun a400mLDonationOverTheAnnualVolumeCapIsBlocked() {
        showForm(records = nearAnnualVolumeCap())
        composeTestRule.onNodeWithText("전혈헌혈").performClick()

        saveWithTodaysDate()
        assertNull(saved)
        composeTestRule.onNodeWithText("헌혈 제한기간이에요", substring = true).assertIsDisplayed()
    }

    @Test
    fun wholeBloodRecordSavedWithoutAVolumeOpensOn400mL() {
        // Whole blood records added before the volume could be picked have no donatedVolumeMl and
        // have been counted at the standard 430mL.
        showForm(DonationRecord(id = 1, type = DonationType.WHOLE_BLOOD, date = LocalDate.of(2026, 7, 21)))
        composeTestRule.onNodeWithText("400mL").assertIsSelected()
    }
}
