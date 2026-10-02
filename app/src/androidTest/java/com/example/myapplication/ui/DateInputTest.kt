package com.example.myapplication.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.example.myapplication.domain.formatDateDigits
import com.example.myapplication.domain.toDateDigits
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class DateInputTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private var reported: LocalDate? = null

    private fun showField(showCalendar: Boolean = true) {
        composeTestRule.setContent {
            // Holds the date the way the forms do, so the field sees its own reports come back.
            var date by remember { mutableStateOf<LocalDate?>(null) }
            DateInputField(date = date, onDateChange = { date = it; reported = it }, showCalendar = showCalendar)
        }
    }

    private val field get() = composeTestRule.onNode(hasSetTextAction())

    @Test
    fun typedDigitsReadAsYearMonthDay() {
        showField()
        field.performTextInput("20250315")
        field.assert(hasText("2025/03/15"))
        assertEquals(LocalDate.of(2025, 3, 15), reported)
    }

    @Test
    fun editingAValidDateBackToIncompleteKeepsWhatWasTyped() {
        showField()
        field.performTextInput("20250315")
        field.performTextReplacement("2025031")
        field.assert(hasText("2025/03/1"))
        assertNull(reported)
    }

    @Test
    fun aDateThatDoesNotExistIsFlagged() {
        showField()
        field.performTextInput("20250231")
        composeTestRule.onNodeWithText("유효한 날짜가 아니에요").assertIsDisplayed()
        assertNull(reported)
    }

    @Test
    fun aFutureDateIsFlagged() {
        showField()
        field.performTextInput(LocalDate.now().plusDays(1).toDateDigits())
        composeTestRule.onNodeWithText("오늘 이후 날짜는 입력할 수 없어요").assertIsDisplayed()
        assertNull(reported)
    }

    @Test
    fun theCalendarOnlyOffersPicking() {
        showField()
        composeTestRule.onNodeWithContentDescription("달력에서 선택").performClick()
        // The calendar has no typed input of its own, so the phone's date format never shows up.
        composeTestRule.onAllNodes(hasSetTextAction()).assertCountEquals(1)

        composeTestRule.onNodeWithText("확인").performClick()
        field.assert(hasText(formatDateDigits(LocalDate.now().toDateDigits())))
        assertEquals(LocalDate.now(), reported)
    }

    @Test
    fun theBirthDateFieldHasNoCalendar() {
        showField(showCalendar = false)
        composeTestRule.onNodeWithContentDescription("달력에서 선택").assertDoesNotExist()
    }
}
