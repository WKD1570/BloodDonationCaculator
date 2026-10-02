package com.example.myapplication.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.myapplication.model.DonationRecord
import com.example.myapplication.model.DonationType
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RecordListTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    // Two donations in 2026, two in 2025 and one in 2023.
    private val records = listOf(
        DonationRecord(id = 1, type = DonationType.WHOLE_BLOOD, date = LocalDate.of(2026, 9, 19), donatedVolumeMl = 350),
        DonationRecord(id = 2, type = DonationType.PLASMA, date = LocalDate.of(2026, 7, 2)),
        DonationRecord(id = 3, type = DonationType.WHOLE_BLOOD, date = LocalDate.of(2025, 12, 1)),
        DonationRecord(id = 4, type = DonationType.PLATELET, date = LocalDate.of(2025, 10, 10)),
        DonationRecord(id = 5, type = DonationType.WHOLE_BLOOD, date = LocalDate.of(2023, 9, 30))
    )

    private fun showList(records: List<DonationRecord>) {
        composeTestRule.setContent {
            RecordListCard(
                title = "헌혈 기록",
                emptyMessage = "아직 등록된 헌혈 기록이 없어요.",
                records = records,
                onAddClick = {},
                onRecordClick = {}
            )
        }
    }

    private fun topOf(text: String) = composeTestRule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top

    @Test
    fun recordsAreGroupedUnderYearHeadersNewestFirst() {
        showList(records)
        assertTrue(topOf("2026년 · 2건") < topOf("2025년 · 2건"))
        assertTrue(topOf("2025년 · 2건") < topOf("2023년 · 1건"))
    }

    @Test
    fun onlyTheNewestYearStartsOpen() {
        showList(records)
        composeTestRule.onNodeWithText("9월 19일").assertIsDisplayed()
        composeTestRule.onNodeWithText("7월 2일").assertIsDisplayed()
        composeTestRule.onNodeWithText("12월 1일").assertDoesNotExist()
        composeTestRule.onNodeWithText("9월 30일").assertDoesNotExist()
    }

    @Test
    fun tappingAYearOpensAndClosesIt() {
        showList(records)
        composeTestRule.onNodeWithText("2025년 · 2건").performClick()
        composeTestRule.onNodeWithText("12월 1일").assertIsDisplayed()
        composeTestRule.onNodeWithText("10월 10일").assertIsDisplayed()

        composeTestRule.onNodeWithText("2025년 · 2건").performClick()
        composeTestRule.onNodeWithText("12월 1일").assertDoesNotExist()

        composeTestRule.onNodeWithText("2026년 · 2건").performClick()
        composeTestRule.onNodeWithText("9월 19일").assertDoesNotExist()
    }

    @Test
    fun theLatestYearStartsOpenWhenNothingIsRecordedThisYear() {
        showList(records.drop(2)) // only 2025 and 2023
        composeTestRule.onNodeWithText("12월 1일").assertIsDisplayed()
        composeTestRule.onNodeWithText("9월 30일").assertDoesNotExist()
    }

    @Test
    fun rowsUnderAYearShowTheDateWithoutTheYear() {
        showList(records)
        composeTestRule.onNodeWithText("9월 19일").assertIsDisplayed()
        composeTestRule.onNodeWithText("2026년 9월 19일").assertDoesNotExist()
    }

    @Test
    fun wholeBloodRowsShowTheirVolumeAndOtherTypesDoNot() {
        showList(records)
        composeTestRule.onNodeWithText("전혈헌혈 320mL").assertIsDisplayed()
        composeTestRule.onNodeWithText("혈장성분헌혈").assertIsDisplayed()
    }
}
