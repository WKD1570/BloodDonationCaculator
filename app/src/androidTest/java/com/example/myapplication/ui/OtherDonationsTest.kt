package com.example.myapplication.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.myapplication.domain.toDateDigits
import com.example.myapplication.model.DonationRecord
import com.example.myapplication.model.DonationType
import com.example.myapplication.model.OTHER_DONATION_TYPES
import com.example.myapplication.model.REGULAR_DONATION_TYPES
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class OtherDonationsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val today = LocalDate.of(2026, 9, 30)

    private val records = listOf(
        DonationRecord(id = 1, type = DonationType.WHOLE_BLOOD, date = LocalDate.of(2026, 9, 19)),
        DonationRecord(id = 2, type = DonationType.WHITE_BLOOD_CELL, date = LocalDate.of(2026, 8, 1)),
        DonationRecord(id = 3, type = DonationType.STEM_CELL, date = LocalDate.of(2026, 5, 2))
    )

    private var saved: DonationRecord? = null

    private fun showForm(types: List<DonationType>, records: List<DonationRecord> = emptyList()) {
        composeTestRule.setContent {
            RecordFormDialog(
                initial = null,
                records = records,
                types = types,
                onDismiss = {},
                onSave = { saved = it },
                onDelete = null
            )
        }
    }

    // InputScreen emits its cards into the caller's layout, which in the app is a scrolling Column.
    private fun showInputScreen(onAddClick: (List<DonationType>) -> Unit) {
        composeTestRule.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                InputScreen(records = records, onAddClick = onAddClick, onRecordClick = {})
            }
        }
    }

    // The add form's only text field is the YYYY/MM/DD date.
    private fun saveWithTodaysDate() {
        composeTestRule.onNode(hasSetTextAction()).performTextInput(LocalDate.now().toDateDigits())
        composeTestRule.onNodeWithText("저장").performClick()
    }

    @Test
    fun theOtherCardTakesTheWhiteBloodCellAndStemCellDonations() {
        showInputScreen(onAddClick = {})
        composeTestRule.onNodeWithText("기타").assertExists()
        // 헌혈 기록 keeps the whole blood donation; 기타 gets the other two.
        composeTestRule.onNodeWithText("1건 등록됨").assertExists()
        composeTestRule.onNodeWithText("2건 등록됨").assertExists()
    }

    @Test
    fun eachCardsAddButtonOffersItsOwnTypes() {
        var offered: List<DonationType>? = null
        showInputScreen(onAddClick = { offered = it })
        composeTestRule.onAllNodesWithText("+ 추가")[0].performClick()
        assertEquals(REGULAR_DONATION_TYPES, offered)
        composeTestRule.onAllNodesWithText("+ 추가")[1].performClick()
        assertEquals(OTHER_DONATION_TYPES, offered)
    }

    @Test
    fun theOtherFormOffersOnlyTheTwoOtherTypesAndNoPhotoScan() {
        showForm(OTHER_DONATION_TYPES)
        composeTestRule.onNodeWithText("기타 기록 추가").assertIsDisplayed()
        composeTestRule.onNodeWithText("백혈구성분헌혈").assertIsDisplayed()
        composeTestRule.onNodeWithText("조혈모세포 기증").assertIsDisplayed()
        composeTestRule.onNodeWithText("전혈헌혈").assertDoesNotExist()
        composeTestRule.onNodeWithText("사진으로 채우기").assertDoesNotExist()
    }

    @Test
    fun theRegularFormDoesNotOfferTheOtherTypes() {
        showForm(REGULAR_DONATION_TYPES)
        composeTestRule.onNodeWithText("전혈헌혈").assertIsDisplayed()
        composeTestRule.onNodeWithText("백혈구성분헌혈").assertDoesNotExist()
        composeTestRule.onNodeWithText("조혈모세포 기증").assertDoesNotExist()
    }

    @Test
    fun aStemCellDonationSavesEvenRightAfterAWholeBloodDonation() {
        showForm(OTHER_DONATION_TYPES, listOf(recentWholeBlood()))
        composeTestRule.onNodeWithText("조혈모세포 기증").performClick()

        saveWithTodaysDate()
        assertEquals(DonationType.STEM_CELL, saved?.type)
    }

    @Test
    fun aWhiteBloodCellDonationRightAfterAWholeBloodDonationIsBlocked() {
        showForm(OTHER_DONATION_TYPES, listOf(recentWholeBlood()))
        composeTestRule.onNodeWithText("백혈구성분헌혈").performClick()

        saveWithTodaysDate()
        assertNull(saved)
        composeTestRule.onNodeWithText("헌혈 제한기간이에요", substring = true).assertIsDisplayed()
    }

    // 10 days before the date picker's default (the device's today), inside whole blood's 56-day block.
    private fun recentWholeBlood() =
        DonationRecord(id = 1, type = DonationType.WHOLE_BLOOD, date = LocalDate.now().minusDays(10))
}
