package com.example.myapplication.data

import com.example.myapplication.domain.stayRegions
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionLocatorTest {

    private val locator = File("src/main/assets/$REGION_SHAPES_ASSET").inputStream().use(::parseRegionShapes)

    @Test
    fun `every region a stay can be recorded in has a boundary`() {
        val rules = File("src/main/assets/$INFECTION_RULES_ASSET").inputStream().use(::parseInfectionRules)
        val missing = stayRegions(rules).map { it.name } - locator.regionNames
        assertTrue("No boundary for $missing", missing.isEmpty())
    }

    @Test
    fun `domestic malaria areas are told apart from their neighbours`() {
        assertEquals("경기 파주시", locator.locate(37.7599, 126.7802)) // 파주시청
        assertEquals("경기 연천군", locator.locate(38.0965, 127.0748)) // 연천군청
        assertEquals("경기 김포시", locator.locate(37.6153, 126.7156)) // 김포시청
        assertEquals("인천 강화군", locator.locate(37.7467, 126.4880)) // 강화군청
        assertEquals("강원 철원군", locator.locate(38.1467, 127.3133)) // 철원군청
        assertNull(locator.locate(37.5665, 126.9780)) // 서울시청
        assertNull(locator.locate(37.6584, 126.8320)) // 고양시청, next to 파주
    }

    @Test
    fun `international malaria countries are found, including overseas territories`() {
        assertEquals("태국", locator.locate(13.7563, 100.5018)) // Bangkok
        assertEquals("나이지리아", locator.locate(6.5244, 3.3792)) // Lagos
        assertEquals("인도", locator.locate(28.6139, 77.2090)) // New Delhi
        assertEquals("프랑스령 기아나", locator.locate(4.9224, -52.3135)) // Cayenne
        assertEquals("탄자니아", locator.locate(-6.1659, 39.2026)) // Zanzibar
        assertNull(locator.locate(35.6762, 139.6503)) // Tokyo
    }

    @Test
    fun `North Korea is restricted except around Paektu`() {
        assertEquals("북한", locator.locate(39.0392, 125.7625)) // Pyongyang
        assertNull(locator.locate(41.9936, 128.0778)) // 백두산
    }

    @Test
    fun `vCJD countries cover their listed areas but not France overseas`() {
        assertEquals("영국", locator.locate(51.5074, -0.1278)) // London
        assertEquals("영국", locator.locate(55.9533, -3.1883)) // Edinburgh
        assertEquals("영국", locator.locate(54.5973, -5.9301)) // Belfast
        assertEquals("영국", locator.locate(54.1523, -4.4861)) // Isle of Man
        assertEquals("영국", locator.locate(49.2079, -2.1955)) // Jersey airport
        assertEquals("영국", locator.locate(-51.6970, -57.8517)) // Falklands
        assertEquals("프랑스", locator.locate(48.8566, 2.3522)) // Paris
        assertEquals("아일랜드", locator.locate(53.3498, -6.2603)) // Dublin
    }

    @Test
    fun `a seaside point the coarse coastline leaves out snaps to the nearby restricted coast`() {
        assertEquals("영국", locator.locate(49.1858, -2.1100)) // St Helier, just outside the 1:10m Jersey outline
        assertNull(locator.locate(49.6, -2.9)) // open sea, nowhere near a coast
    }

    @Test
    fun `a point across a land border is never snapped into a restricted country`() {
        assertNull(locator.locate(1.4382, 103.7890)) // Woodlands, Singapore - 1km from Malaysia
        assertNull(locator.locate(32.5560, -117.0470)) // San Ysidro, USA - 2km from Tijuana, Mexico
        assertEquals("멕시코", locator.locate(32.5149, -117.0382)) // Tijuana
        assertEquals("말레이시아", locator.locate(1.4655, 103.7578)) // Johor Bahru
    }
}
