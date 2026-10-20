package com.example.myapplication.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso
import com.example.myapplication.domain.checkPlan
import com.example.myapplication.domain.fillPlan
import com.example.myapplication.model.DonationRecord
import com.example.myapplication.model.DonationType
import com.example.myapplication.model.PlannedDonation
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The 헌혈 계획 page and the 더보기 entry to it. "Today" is Tuesday 2026-10-20 and the only donation
 * is whole blood on 8/29, which holds every type back until Saturday 10/24.
 */
class DonationPlannerTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 20)
    private val records = listOf(DonationRecord(id = 1, type = DonationType.WHOLE_BLOOD, date = LocalDate.of(2026, 8, 29)))
    private var backPressed = false

    /** The page backed by the real plan rules, the way DonationViewModel backs it. */
    private fun showPlanner(initial: List<PlannedDonation> = emptyList()) {
        composeTestRule.setContent {
            var plan by remember { mutableStateOf(initial) }
            var nextId by remember { mutableStateOf(100L) }
            DonationPlannerScreen(
                records = records,
                plan = checkPlan(plan, records, emptyList(), emptyList()),
                nextByType = emptyMap(),
                today = today,
                onToggleDay = { date, type ->
                    plan = if (plan.any { it.date == date }) plan.filterNot { it.date == date }
                    else plan + PlannedDonation(nextId++, type, date)
                },
                onRemove = { id -> plan = plan.filterNot { it.id == id } },
                onFill = { type, months ->
                    plan = fillPlan(type, today, today.plusMonths(months), plan, records, emptyList(), emptyList()) { nextId++ }
                },
                onClear = { plan = emptyList() },
                onBack = { backPressed = true }
            )
        }
    }

    private fun tap(text: String) = composeTestRule.onNodeWithText(text).performScrollTo().performClick()

    @Test
    fun tappingADayPlansTheChosenTypeAndTappingAgainTakesItOff() {
        showPlanner()
        tap("혈소판성분헌혈")

        tap("24")
        composeTestRule.onNode(hasText("10월 24일 (토)")).assertExists()
        composeTestRule.onNodeWithText("✓ 가능").assertExists()

        tap("24")
        composeTestRule.onNode(hasText("10월 24일 (토)")).assertDoesNotExist()
    }

    @Test
    fun aDayTooSoonSaysFromWhenItWouldBeAllowed() {
        showPlanner()
        tap("23")
        composeTestRule.onNode(hasText("10월 23일 (금)")).assertExists()
        composeTestRule.onNodeWithText("✗ 10월 24일부터 가능").assertExists()
    }

    @Test
    fun fillingPlansTheEarliestDaysForTheChosenType() {
        showPlanner()
        tap("혈소판성분헌혈")
        tap("3개월")
        tap("가능한 날짜로 채우기")

        composeTestRule.onNode(hasText("10월 24일 (토)")).assertExists()
        composeTestRule.onNode(hasText("11월 7일 (토)")).assertExists()
        composeTestRule.onNode(hasText("계획 7회", substring = true)).assertExists()
    }

    @Test
    fun clearingAsksFirstThenEmptiesThePlan() {
        showPlanner(initial = listOf(PlannedDonation(1, DonationType.PLATELET, LocalDate.of(2026, 10, 24))))
        tap("계획 모두 지우기")
        composeTestRule.onNode(hasText("10월 24일 (토)")).assertExists()

        composeTestRule.onNodeWithText("지우기").performClick()
        composeTestRule.onNode(hasText("10월 24일 (토)")).assertDoesNotExist()
    }

    @Test
    fun backLeavesThePlanner() {
        showPlanner()
        Espresso.pressBack()
        assertTrue(backPressed)
    }

    @Test
    fun theCalendarCardShowsPlannedDaysAndItsMoreButtonOpensThePlanner() {
        var opened = false
        val plan = checkPlan(listOf(PlannedDonation(1, DonationType.PLATELET, LocalDate.of(2026, 10, 24))), records, emptyList(), emptyList())
        composeTestRule.setContent {
            DonationCalendarCard(records = records, today = today, nextByType = emptyMap(), plan = plan, onMore = { opened = true })
        }

        composeTestRule.onNodeWithContentDescription("혈소판성분헌혈 계획").assertExists()
        composeTestRule.onNodeWithText("더보기 ›").performClick()
        assertTrue(opened)
    }
}
