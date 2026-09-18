package com.example.myapplication.data

import com.example.myapplication.domain.PROHIBITED_DRUGS
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProhibitedDrugRepositoryTest {

    @Test
    fun `parses a well formed entry into the model`() {
        val drugs = parseProhibitedDrugs(
            """
            [
              {
                "category": "기타 약물",
                "ingredientName": "아스피린",
                "representativeBrands": "아스피린",
                "restrictionDays": 3,
                "restrictionText": "복용 후 3일간 헌혈 금지"
              }
            ]
            """.trimIndent()
        )
        assertEquals(1, drugs.size)
        val drug = drugs.single()
        assertEquals("기타 약물", drug.category)
        assertEquals("아스피린", drug.ingredientName)
        assertEquals("아스피린", drug.representativeBrands)
        assertEquals(3, drug.restrictionDays)
        assertEquals("복용 후 3일간 헌혈 금지", drug.restrictionText)
    }

    @Test
    fun `keeps a negative restrictionDays as the permanent marker`() {
        val drugs = parseProhibitedDrugs(
            """[{"ingredientName":"에트레티네이트","restrictionDays":-1}]"""
        )
        assertEquals(-1, drugs.single().restrictionDays)
    }

    @Test
    fun `defaults missing display fields to blank rather than dropping the entry`() {
        val drug = parseProhibitedDrugs(
            """[{"ingredientName":"아스피린","restrictionDays":3}]"""
        ).single()
        assertEquals("", drug.category)
        assertEquals("", drug.representativeBrands)
        assertEquals("", drug.restrictionText)
    }

    @Test
    fun `skips entries missing an ingredient name or a restriction period`() {
        val drugs = parseProhibitedDrugs(
            """
            [
              {"ingredientName":"아스피린","restrictionDays":3},
              {"ingredientName":"  ","restrictionDays":7},
              {"ingredientName":"티클로피딘"}
            ]
            """.trimIndent()
        )
        assertEquals(listOf("아스피린"), drugs.map { it.ingredientName })
    }

    @Test
    fun `throws when the payload is not valid JSON`() {
        assertThrows(ProhibitedDrugDataException::class.java) {
            parseProhibitedDrugs("{ not json")
        }
    }

    @Test
    fun `throws when no entry is usable`() {
        assertThrows(ProhibitedDrugDataException::class.java) {
            parseProhibitedDrugs("""[{"category":"기타 약물"}]""")
        }
    }

    /**
     * Guards the shipped asset itself: a JVM test cannot open Android assets, but the file is
     * plain text on disk at a fixed path relative to the module directory.
     *
     * Only the fields that drive the date calculation are pinned to the compiled-in fallback.
     * `representativeBrands` is deliberately excluded: the asset carries the full 대한적십자사 brand
     * lists while the fallback keeps an abbreviated form, and locking them together would make
     * every routine data refresh fail this test.
     */
    @Test
    fun `the bundled asset parses and agrees with the fallback on the calculation fields`() {
        val asset = File("src/main/assets/$PROHIBITED_DRUGS_ASSET")
        assertTrue("missing $asset", asset.exists())

        val fromAsset = parseProhibitedDrugs(asset.readText())

        assertEquals(
            PROHIBITED_DRUGS.map { Triple(it.category, it.ingredientName, it.restrictionDays) },
            fromAsset.map { Triple(it.category, it.ingredientName, it.restrictionDays) }
        )
    }

    @Test
    fun `every bundled entry is searchable and carries a usable restriction period`() {
        val drugs = parseProhibitedDrugs(
            File("src/main/assets/$PROHIBITED_DRUGS_ASSET").readText()
        )
        assertTrue(drugs.isNotEmpty())
        drugs.forEach { drug ->
            assertTrue("blank ingredientName in $drug", drug.ingredientName.isNotBlank())
            assertTrue("blank representativeBrands in $drug", drug.representativeBrands.isNotBlank())
            // -1 means a lifetime ban; anything else must be a real, forward-moving period.
            assertTrue("nonsensical restrictionDays in $drug", drug.restrictionDays == -1 || drug.restrictionDays > 0)
        }
    }

    @Test
    fun `bundled entries are unique by ingredient and brand list`() {
        val drugs = parseProhibitedDrugs(
            File("src/main/assets/$PROHIBITED_DRUGS_ASSET").readText()
        )
        val keys = drugs.map { it.ingredientName to it.representativeBrands }
        assertEquals(keys.size, keys.distinct().size)
    }
}
