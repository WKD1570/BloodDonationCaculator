package com.example.myapplication.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 마이페이지's 자주 헌혈하는 지역 and what it does to the 헌혈의 집 tab. The list tests read the bundled
 * `blood_centers.json`, where 부산's first center is 전포센터 and the whole list starts with 서울's
 * 중앙센터.
 */
@OptIn(ExperimentalTestApi::class)
class PreferredProvinceTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val busanHeading = "부산 (자주 헌혈하는 지역)"

    private fun bounds(text: String) = composeTestRule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot

    private fun showCard(selected: String?, onSelect: (String?) -> Unit = {}) {
        composeTestRule.setContent { PreferredProvinceCard(selected = selected, onSelect = onSelect) }
    }

    /** The tab with 부산 preferred, once the bundled list has loaded - the chips appear only then. */
    private fun showListPreferringBusan() {
        composeTestRule.setContent { BloodCenterListScreen(preferredProvince = "부산") }
        composeTestRule.waitUntilAtLeastOneExists(hasText("전체"), timeoutMillis = 5_000)
    }

    @Test
    fun tappingAProvincePicksIt() {
        var picked: String? = null
        showCard(selected = null, onSelect = { picked = it })

        composeTestRule.onNodeWithText("부산").performClick()

        assertEquals("부산", picked)
    }

    @Test
    fun tappingThePickedProvinceAgainClearsIt() {
        var picked: String? = "unchanged"
        showCard(selected = "부산", onSelect = { picked = it })

        composeTestRule.onNodeWithText("부산").performClick()

        assertNull(picked)
    }

    @Test
    fun thePickedProvinceShowsAsSelected() {
        showCard(selected = "부산")

        composeTestRule.onNodeWithText("부산").assertIsSelected()
        composeTestRule.onNodeWithText("서울").assertIsNotSelected()
    }

    @Test
    fun preferredCentersComeFirstUnderTheirOwnHeading() {
        showListPreferringBusan()

        assertTrue(bounds(busanHeading).top < bounds("전포센터").top)

        composeTestRule.onNode(hasScrollToKeyAction()).performScrollToKey(OTHERS_HEADING_KEY)
        assertTrue(bounds("다른 지역").top < bounds("중앙센터").top)
    }

    @Test
    fun pickingAProvinceShowsItWithoutHeadings() {
        showListPreferringBusan()

        composeTestRule.onNodeWithText("부산").performScrollTo().performClick()

        composeTestRule.onNodeWithText("전포센터").assertExists()
        composeTestRule.onNodeWithText(busanHeading).assertDoesNotExist()
        composeTestRule.onNodeWithText("다른 지역").assertDoesNotExist()
    }

    @Test
    fun thePreferredChipComesRightAfterAll() {
        showListPreferringBusan()

        assertTrue(bounds("전체").left < bounds("부산").left)
        assertTrue(bounds("부산").left < bounds("서울").left)
    }
}
