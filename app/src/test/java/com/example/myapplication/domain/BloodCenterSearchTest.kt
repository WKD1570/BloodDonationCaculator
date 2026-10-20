package com.example.myapplication.domain

import com.example.myapplication.model.BloodCenter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BloodCenterSearchTest {

    private fun center(
        name: String,
        address: String,
        bloodBank: String = "서울중앙",
        type: String = "국고"
    ) = BloodCenter(bloodBank, name, type, address, "02-000-0000")

    @Test
    fun `cuts the address right after the building number`() {
        assertEquals("서울 중구 한강대로 405", roadAddressOf("서울 중구 한강대로 405 서울역사 지하1층 B05호"))
    }

    @Test
    fun `drops everything after a comma`() {
        assertEquals("서울 마포구 양화로 152", roadAddressOf("서울 마포구 양화로 152, 대화빌딩 6층"))
    }

    @Test
    fun `splits a building number written onto the road name`() {
        assertEquals("서울 강남구 강남대로 364", roadAddressOf("서울 강남구 강남대로364, 미왕빌딩 2층"))
        assertEquals("세종시 한누리대로 265", roadAddressOf("세종시 한누리대로265 명동프라자 4층"))
        assertEquals(
            "경기 김포시 김포한강9로 76번길 63",
            roadAddressOf("경기 김포시 김포한강9로 76번길63 아스타프라자 305호, 306호")
        )
    }

    @Test
    fun `keeps 번길 and numbered road names intact`() {
        assertEquals(
            "경기 고양시 일산동구 중앙로 1261번길 55",
            roadAddressOf("경기 고양시 일산동구 중앙로 1261번길 55 효산캐슬빌딩 204호")
        )
        assertEquals("서울 강서구 강서로45길 5", roadAddressOf("서울 강서구 강서로45길 5, 2층"))
    }

    @Test
    fun `ignores a parenthesised 동 name after the number`() {
        assertEquals(
            "경기 성남시 분당구 야탑로 69번길 21",
            roadAddressOf("경기 성남시 분당구 야탑로 69번길 21(야탑동), 영진다이비스상가 211호")
        )
    }

    @Test
    fun `handles lot number addresses`() {
        assertEquals("경북 포항시 북구 상원동 462-5", roadAddressOf("경북 포항시 북구 상원동 462-5, 5층"))
    }

    @Test
    fun `rewrites the merged province name map apps do not know`() {
        assertEquals("전남 순천시 왕지로 33", roadAddressOf("전남광주 순천시 왕지로 33 진보석 2층"))
    }

    @Test
    fun `returns null when there is no building number`() {
        assertNull(roadAddressOf("대전 중구 중앙로 164번길 이안경원 2층"))
    }

    @Test
    fun `map query is the road address, or the address before any comma`() {
        assertEquals("서울 중구 한강대로 405", mapQueryOf(center("서울역센터", "서울 중구 한강대로 405 서울역사 지하1층 B05호")))
        assertEquals(
            "대전 중구 중앙로 164번길 이안경원 2층",
            mapQueryOf(center("으능정이센터", "대전 중구 중앙로 164번길 이안경원 2층"))
        )
    }

    @Test
    fun `province is the short form of the first word`() {
        assertEquals("서울", provinceOf("서울 중구 한강대로 405"))
        assertEquals("서울", provinceOf("서울특별시 종로구 새문안로 9"))
        assertEquals("경기", provinceOf("경기도 안양시 만안구 병목안로 2"))
        assertEquals("인천", provinceOf("인천광역시 서구 청라커낼로 277"))
        assertEquals("세종", provinceOf("세종시 한누리대로265"))
        assertEquals("전남", provinceOf("전남광주 순천시 왕지로 33"))
        assertEquals("", provinceOf("  "))
    }

    @Test
    fun `province counts follow the chip order with unknown spellings last`() {
        val centers = listOf(
            center("A", "부산 중구 구덕로 52"),
            center("B", "서울 중구 한강대로 405"),
            center("C", "서울특별시 종로구 새문안로 9"),
            center("D", "어딘가 1"),
            center("E", "경기 하남시 신장로 133")
        )
        assertEquals(listOf("서울" to 2, "경기" to 1, "부산" to 1, "어딘가" to 1), provinceCounts(centers))
    }

    @Test
    fun `search matches name, 혈액원 and address, ignoring spaces and 헌혈의 집`() {
        val gangnam = center("강남센터", "서울 서초구 강남대로 437, 7층", bloodBank = "서울남부")
        val seomyeon = center("서면센터", "부산 부산진구 중앙대로 698", bloodBank = "부산")
        val centers = listOf(gangnam, seomyeon)

        assertEquals(listOf(gangnam), searchCenters(centers, "강남"))
        assertEquals(listOf(gangnam), searchCenters(centers, "서울남부"))
        assertEquals(listOf(seomyeon), searchCenters(centers, "부산 서면"))
        assertEquals(listOf(seomyeon), searchCenters(centers, "헌혈의집 서면"))
        assertEquals(listOf(seomyeon), searchCenters(centers, "헌혈의 집 서면 센터"))
        assertEquals(listOf(seomyeon), searchCenters(centers, "중앙대로698"))
        assertEquals(emptyList<BloodCenter>(), searchCenters(centers, "제주"))
    }

    @Test
    fun `a query with nothing to match on keeps the whole list`() {
        val centers = listOf(center("강남센터", "서울 서초구 강남대로 437"), center("서면센터", "부산 부산진구 중앙대로 698"))
        assertEquals(centers, searchCenters(centers, ""))
        assertEquals(centers, searchCenters(centers, "  "))
        assertEquals(centers, searchCenters(centers, "헌혈의집"))
    }

    @Test
    fun `name matches come before address matches, keeping data order within each`() {
        val inAddressOnly = center("매봉센터", "서울 강남구 개포로31길 48")
        val first = center("강남센터", "서울 서초구 강남대로 437")
        val second = center("강남역센터", "서울 강남구 강남대로364")
        assertEquals(listOf(first, second, inAddressOnly), searchCenters(listOf(inAddressOnly, first, second), "강남"))
    }

    @Test
    fun `campus centers can be found by 대학 내`() {
        val campus = center("서울대학교센터", "서울 관악구 관악로 1", type = "자체대학")
        val station = center("서울역센터", "서울 중구 한강대로 405", type = "자체")
        assertEquals(listOf(campus), searchCenters(listOf(campus, station), "대학 내"))
    }

    @Test
    fun `filter narrows by province first, then searches`() {
        val seoul = center("서울역센터", "서울 중구 한강대로 405")
        val goyang = center("일산센터", "경기 고양시 일산동구 중앙로 1261번길 55") // 서울중앙혈액원, but in 경기
        val busan = center("서면센터", "부산 부산진구 중앙대로 698", bloodBank = "부산")
        val centers = listOf(seoul, goyang, busan)

        assertEquals(centers, filterCenters(centers, "", null))
        assertEquals(listOf(goyang), filterCenters(centers, "", "경기"))
        assertEquals(listOf(goyang), filterCenters(centers, "일산", "경기"))
        assertEquals(listOf(seoul, goyang), filterCenters(centers, "서울중앙", null))
        assertEquals(listOf(seoul), filterCenters(centers, "서울중앙", "서울"))
        assertEquals(emptyList<BloodCenter>(), filterCenters(centers, "서면", "서울"))
    }

    @Test
    fun `under 전체 the preferred province's centers come first, both parts keeping their order`() {
        val busan = center("서면센터", "부산 부산진구 중앙대로 698", bloodBank = "부산")
        val station = center("서울역센터", "서울 중구 한강대로 405")
        val ilsan = center("일산센터", "경기 고양시 일산동구 중앙로 1261번길 55") // 서울중앙혈액원, but in 경기
        val jongno = center("광화문센터", "서울특별시 종로구 새문안로 9")
        val visible = listOf(busan, station, ilsan, jongno)

        assertEquals(listOf(station, jongno) to listOf(busan, ilsan), splitPreferred(visible, null, "서울"))
        assertEquals(listOf(busan) to listOf(station, ilsan, jongno), splitPreferred(visible, null, "부산"))
    }

    @Test
    fun `nothing is split off with a province picked or none preferred`() {
        val seoul = listOf(center("서울역센터", "서울 중구 한강대로 405"), center("광화문센터", "서울 종로구 새문안로 9"))
        val none = emptyList<BloodCenter>()

        assertEquals(none to seoul, splitPreferred(seoul, "서울", "서울"))
        assertEquals(none to seoul, splitPreferred(seoul, null, null))
        assertEquals(none to seoul, splitPreferred(seoul, null, "부산"))
    }

    @Test
    fun `the preferred province's chip comes first, the rest keeping their order`() {
        val counts = listOf("서울" to 34, "경기" to 27, "부산" to 14, "제주" to 4)

        assertEquals(listOf("부산" to 14, "서울" to 34, "경기" to 27, "제주" to 4), preferredFirst(counts, "부산"))
        assertEquals(listOf("제주" to 4, "서울" to 34, "경기" to 27, "부산" to 14), preferredFirst(counts, "제주"))
        assertEquals(counts, preferredFirst(counts, null))
        assertEquals(counts, preferredFirst(counts, "세종"))
    }

    @Test
    fun `labels only the place types a donor cares about`() {
        assertEquals("혈액원 내", center("중앙센터", "", type = "원내").placeLabel)
        assertEquals("대학 내", center("강원대센터", "", type = "국고대학").placeLabel)
        assertEquals("대학 내", center("동의대센터", "", type = "자체대학").placeLabel)
        assertNull(center("서울역센터", "", type = "자체").placeLabel)
        assertNull(center("신촌센터", "", type = "국고").placeLabel)
        assertEquals("서울중앙혈액원", center("중앙센터", "").bloodBankLabel)
    }
}
