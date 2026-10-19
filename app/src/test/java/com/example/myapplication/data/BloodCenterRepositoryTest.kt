package com.example.myapplication.data

import com.example.myapplication.domain.PROVINCE_ORDER
import com.example.myapplication.domain.provinceOf
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BloodCenterRepositoryTest {

    private fun parse(json: String) = parseBloodCenters(json.byteInputStream())

    @Test
    fun `parses a center and trims its fields`() {
        val list = parse(
            """
            {"source": "대한적십자사_헌혈의집_정보_20260701.csv", "centers": [{
              "bloodBank": "서울중앙", "name": " 서울역센터 ", "type": "자체",
              "address": "서울 중구 한강대로 405 ", "phone": "02-752-9020"
            }]}
            """.trimIndent()
        )
        val center = list.centers.single()
        assertEquals("서울중앙/서울역센터", center.id)
        assertEquals("서울 중구 한강대로 405", center.address)
        assertEquals("02-752-9020", center.phone)
        assertEquals("2026.07.01", list.asOf)
    }

    @Test
    fun `has no date when the source name carries none`() {
        assertNull(parse("""{"source": "centers.csv", "centers": [{"name": "A센터", "address": "서울"}]}""").asOf)
        assertNull(parse("""{"centers": [{"name": "A센터", "address": "서울"}]}""").asOf)
    }

    @Test
    fun `skips entries without a name or address`() {
        val list = parse(
            """{"centers": [{"name": "", "address": "서울"}, {"name": "A센터"}, null, {"name": "B센터", "address": "부산"}]}"""
        )
        assertEquals(listOf("B센터"), list.centers.map { it.name })
    }

    @Test
    fun `throws when nothing usable is left`() {
        assertThrows(BloodCenterDataException::class.java) { parse("""{"centers": []}""") }
        assertThrows(BloodCenterDataException::class.java) { parse("{}") }
    }

    @Test
    fun `throws on malformed json`() {
        assertThrows(BloodCenterDataException::class.java) { parse("""{"centers": [""") }
    }

    @Test
    fun `bundled asset parses with unique ids and every center in a known province`() {
        val list = File("src/main/assets/$BLOOD_CENTERS_ASSET").inputStream().use(::parseBloodCenters)
        assertTrue("only ${list.centers.size} centers", list.centers.size > 100)
        assertEquals(list.centers.size, list.centers.map { it.id }.toSet().size)
        list.centers.forEach { assertTrue("${it.name}: ${it.address}", provinceOf(it.address) in PROVINCE_ORDER) }
        assertTrue(list.asOf != null)
    }
}
