package com.example.myapplication.ui

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class ProfileNameInputTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun koreanNameCanBeTyped() {
        composeTestRule.setContent {
            BloodDonationCalculatorScreen(onToggleTheme = {})
        }
        composeTestRule.onNodeWithText("마이페이지").performClick()
        composeTestRule.onAllNodes(hasSetTextAction())[0].performTextInput("가나다")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("가나다").assertExists()
    }
}
