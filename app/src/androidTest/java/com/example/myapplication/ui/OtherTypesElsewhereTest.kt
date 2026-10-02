package com.example.myapplication.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.myapplication.domain.integratedNextEligible
import com.example.myapplication.model.DonationRecord
import com.example.myapplication.model.DonationType
import com.example.myapplication.model.DonorProfile
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

class OtherTypesElsewhereTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val today = LocalDate.of(2026, 9, 30)

    private val records = listOf(
        DonationRecord(id = 1, type = DonationType.WHOLE_BLOOD, date = LocalDate.of(2026, 9, 19)),
        DonationRecord(id = 2, type = DonationType.STEM_CELL, date = LocalDate.of(2026, 5, 2))
    )

    // Next dates the way the calculator gives them for every type, 기타 ones included.
    private val nextByType = DonationType.entries.associateWith {
        integratedNextEligible(it, records, emptyList(), today)
    }

    private fun show(content: @Composable () -> Unit) {
        composeTestRule.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) { content() }
        }
    }

    @Test
    fun statusNextDatesListOnlyTheRegularTypes() {
        show { NextDatesCard(records = records, medications = emptyList(), nextByType = nextByType) }
        composeTestRule.onNodeWithText("전혈헌혈").assertExists()
        composeTestRule.onNodeWithText("백혈구성분헌혈").assertDoesNotExist()
        composeTestRule.onNodeWithText("조혈모세포 기증").assertDoesNotExist()
    }

    @Test
    fun calendarLegendHasAnEntryPerType() {
        show { DonationCalendarCard(records = records, today = today, nextByType = nextByType) }
        composeTestRule.onNodeWithText("백혈구성분").assertExists()
        composeTestRule.onNodeWithText("조혈모세포 기증").assertExists()
        composeTestRule.onNodeWithText("기타").assertDoesNotExist()
    }

    @Test
    fun myPageScreensOnlyTheRegularTypes() {
        show { PhysicalProfileCard(profile = DonorProfile(), onProfileChange = {}) }
        composeTestRule.onNodeWithText("전혈헌혈").assertExists()
        composeTestRule.onNodeWithText("백혈구성분헌혈").assertDoesNotExist()
        composeTestRule.onNodeWithText("조혈모세포 기증").assertDoesNotExist()
    }

    @Test
    fun legendShowsTheOtherTypesRules() {
        show { LegendCard() }
        composeTestRule.onNodeWithText("백혈구성분헌혈").assertExists()
        composeTestRule.onNodeWithText("조혈모세포 기증").assertExists()
        composeTestRule.onNodeWithText("30mL").assertExists()
        composeTestRule.onNodeWithText("최소 30mL").assertDoesNotExist()
        composeTestRule.onNodeWithText("6개월").assertExists()
        composeTestRule.onNodeWithText("※ 조혈모세포 채혈량은 알 수 없어 최소 기준인 30mL로 설정했습니다.").assertExists()
    }
}
