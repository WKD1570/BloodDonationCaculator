package com.example.myapplication.ui

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.example.myapplication.domain.formatDateDigits
import com.example.myapplication.domain.toDateDigits
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MedicationDateDialogTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private var confirmed: LocalDate? = null

    private fun showDialog() {
        composeTestRule.setContent {
            MedicationDateDialog(initialDate = null, onDismiss = {}, onConfirm = { confirmed = it })
        }
    }

    private val field get() = composeTestRule.onNode(hasSetTextAction())

    @Test
    fun startsOnTodayTypedAsYearMonthDay() {
        showDialog()
        field.assert(hasText(formatDateDigits(LocalDate.now().toDateDigits())))

        composeTestRule.onNodeWithText("확인").performClick()
        assertEquals(LocalDate.now(), confirmed)
    }

    @Test
    fun confirmStaysDisabledUntilTheDateIsComplete() {
        showDialog()
        field.performTextReplacement("2025031")
        composeTestRule.onNodeWithText("확인").assertIsNotEnabled()
    }

    @Test
    fun theTypedDateIsWhatGetsConfirmed() {
        showDialog()
        field.performTextReplacement("20250315")
        composeTestRule.onNodeWithText("확인").performClick()
        assertEquals(LocalDate.of(2025, 3, 15), confirmed)
    }
}
