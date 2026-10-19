package com.example.myapplication.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso
import org.junit.Rule
import org.junit.Test

/**
 * The machine chips and each center's machines on the 헌혈의 집 tab. Reads the bundled
 * `blood_centers.json`, where 서울역센터 has PCS2 2 and Trima 1, 신도림테크노마트센터 has no 혈소판
 * machine, and 매봉센터's only 혈소판 machine is an Amicus - each the one center its search finds.
 */
@OptIn(ExperimentalTestApi::class)
class MachineFilterTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    /** The tab, once the bundled list has loaded - the chips appear only then. */
    private fun showList() {
        composeTestRule.setContent { BloodCenterListScreen(preferredProvince = null) }
        composeTestRule.waitUntilAtLeastOneExists(hasText("혈소판 가능"), timeoutMillis = 5_000)
    }

    private fun search(query: String) {
        composeTestRule.onNode(hasSetTextAction()).performTextReplacement(query)
    }

    private fun tapChip(label: String) {
        composeTestRule.onNodeWithText(label).performScrollTo().performClick()
    }

    @Test
    fun eachRowShowsHowManyMachinesOfEachKind() {
        showList()

        search("서울역")
        composeTestRule.onNode(hasText("서울역센터") and hasText("혈장 2 · 혈소판 1")).assertExists()

        search("신도림")
        composeTestRule.onNode(hasText("신도림테크노마트센터") and hasText("혈장 1 · 혈소판 없음")).assertExists()
    }

    @Test
    fun openingARowListsItsModels() {
        showList()
        search("서울역")

        composeTestRule.onNodeWithText("서울역센터").performClick()

        composeTestRule.onNode(hasText("서울역센터") and hasText("PCS2 2") and hasText("Trima 1")).assertExists()
    }

    @Test
    fun plateletChipHidesCentersWithoutAPlateletMachineUntilTappedAgain() {
        showList()
        search("신도림")

        tapChip("혈소판 가능")
        composeTestRule.onNodeWithText("신도림테크노마트센터").assertDoesNotExist()

        tapChip("혈소판 가능")
        composeTestRule.onNodeWithText("신도림테크노마트센터").assertExists()
    }

    @Test
    fun aModelChipKeepsOnlyCentersWithThatModel() {
        showList()

        tapChip("Trima")
        composeTestRule.onNode(hasText("곳 · Trima", substring = true)).assertExists()

        search("매봉")
        composeTestRule.onNodeWithText("매봉센터").assertDoesNotExist()

        tapChip("Amicus")
        composeTestRule.onNodeWithText("매봉센터").assertExists()
    }

    @Test
    fun backClearsTheMachineChipBeforeTheProvince() {
        showList()
        tapChip("서울")
        tapChip("혈소판 가능")

        Espresso.pressBack()
        composeTestRule.onNodeWithText("혈소판 가능").assertIsNotSelected()
        composeTestRule.onNodeWithText("서울").assertIsSelected()

        Espresso.pressBack()
        composeTestRule.onNodeWithText("서울").assertIsNotSelected()
    }
}
