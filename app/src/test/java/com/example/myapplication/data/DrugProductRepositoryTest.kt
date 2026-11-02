package com.example.myapplication.data

import com.example.myapplication.domain.restrictionsFor
import com.example.myapplication.model.DrugRoute
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DrugProductRepositoryTest {

    private fun parse(json: String) = parseDrugProducts(json.byteInputStream())

    private val bundled by lazy { File("src/main/assets/$DRUG_PRODUCTS_ASSET").inputStream().use(::parseDrugProducts) }
    private val bundledDrugs by lazy { parseProhibitedDrugs(File("src/main/assets/$PROHIBITED_DRUGS_ASSET").readText()) }

    @Test
    fun `parses a product, its route and the source date`() {
        val list = parse(
            """
            {"source": "drug_products_20260720.csv", "products": [{
              "name": " 한미아스피린장용정100밀리그램 ", "englishName": "Aspirin Enteric Coated Tab. 100mg Hanmi",
              "ingredient": "아스피린", "ingredientCode": "111001ATE"
            }]}
            """.trimIndent()
        )
        val product = list.products.single()
        assertEquals("한미아스피린장용정100밀리그램", product.name)
        assertEquals("아스피린", product.ingredient)
        assertEquals(DrugRoute.ORAL, product.route)
        assertEquals("2026.07.20", list.asOf)
    }

    @Test
    fun `a spelled-out route wins over the ingredient code, and the source note is kept`() {
        val list = parse(
            """
            {"source": "drug_products_20260720.csv", "sourceNote": "의약품 목록(2026.07.20), 식약처 의약품 제품 허가정보(2026.11.03)",
             "products": [
              {"name": "보톡스주100단위", "ingredient": "클로스트리디움보툴리눔독소A형", "route": "injection"},
              {"name": "A정", "ingredient": "가", "route": "weird", "ingredientCode": "111001ATE"},
              {"name": "프로페시아정1밀리그램", "ingredient": "피나스테리드"}
             ]}
            """.trimIndent()
        )
        assertEquals(listOf(DrugRoute.INJECTION, DrugRoute.ORAL, DrugRoute.UNKNOWN), list.products.map { it.route })
        assertEquals("의약품 목록(2026.07.20), 식약처 의약품 제품 허가정보(2026.11.03)", list.sourceNote)
        assertEquals("2026.07.20", list.asOf)
    }

    @Test
    fun `a missing ingredient code leaves the route unknown`() {
        val product = parse("""{"products": [{"name": "A정", "ingredient": "가"}]}""").products.single()
        assertEquals(DrugRoute.UNKNOWN, product.route)
        assertNull(parse("""{"products": [{"name": "A정", "ingredient": "가"}]}""").asOf)
    }

    @Test
    fun `skips entries without a name or ingredient`() {
        val list = parse(
            """{"products": [{"name": "", "ingredient": "가"}, {"name": "A정"}, null, {"name": "B정", "ingredient": "나"}]}"""
        )
        assertEquals(listOf("B정"), list.products.map { it.name })
    }

    @Test
    fun `throws when nothing usable is left or the json is malformed`() {
        assertThrows(DrugProductDataException::class.java) { parse("""{"products": []}""") }
        assertThrows(DrugProductDataException::class.java) { parse("{}") }
        assertThrows(DrugProductDataException::class.java) { parse("""{"products": [""") }
    }

    @Test
    fun `bundled dictionary parses and names its sources`() {
        assertTrue("only ${bundled.products.size} products", bundled.products.size > 100)
        // Products from the coded drug list all carry a route; 식약처 ones may not.
        assertTrue(bundled.products.count { it.route != DrugRoute.UNKNOWN } >= 200)
        assertTrue(bundled.asOf != null)
        assertTrue(bundled.sourceNote.orEmpty().isNotBlank())
    }

    @Test
    fun `bundled products map onto the bundled official list`() {
        val aspirin = bundled.products.first { it.name.startsWith("한미아스피린") }
        assertEquals(listOf("아스피린"), restrictionsFor(aspirin, bundledDrugs).map { it.ingredientName })

        val dexamethasone = bundled.products.first { it.name.startsWith("대원덱사메타손") }
        assertEquals(
            listOf("항생제/스테로이드/보톡스/클로피도그렐"),
            restrictionsFor(dexamethasone, bundledDrugs).map { it.ingredientName }
        )
    }
}
