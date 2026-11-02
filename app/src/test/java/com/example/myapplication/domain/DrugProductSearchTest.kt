package com.example.myapplication.domain

import com.example.myapplication.model.DrugProduct
import com.example.myapplication.model.DrugRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrugProductSearchTest {

    private fun product(name: String, ingredient: String, route: DrugRoute = DrugRoute.ORAL, english: String = "") =
        DrugProduct(name, english, ingredient, route)

    private fun restrictionNames(product: DrugProduct) =
        restrictionsFor(product, PROHIBITED_DRUGS).map { it.ingredientName }

    private val aspirin = product("한미아스피린장용정100밀리그램", "아스피린", english = "Aspirin Enteric Coated Tab. 100mg Hanmi")
    private val quetiapine = product("쿠에티정200밀리그램(쿠에티아핀푸마르산염)", "푸마르산쿠에티아핀", english = "Quety Tab. 200mg")
    private val dexamethasoneShot = product("대원덱사메타손주사액", "디나트륨인산덱사메타손", DrugRoute.INJECTION)
    private val lidocaineShot = product("제일리도카인주사액 20ml", "염산리도카인일수화물", DrugRoute.INJECTION)

    @Test
    fun `route comes from the 7th character of the ingredient code`() {
        assertEquals(DrugRoute.ORAL, DrugRoute.fromIngredientCode("111001ATE"))
        assertEquals(DrugRoute.INJECTION, DrugRoute.fromIngredientCode("142232BIJ"))
        assertEquals(DrugRoute.EXTERNAL, DrugRoute.fromIngredientCode("171130CCM"))
        assertEquals(DrugRoute.ORAL, DrugRoute.fromIngredientCode("B65200ATB"))
        assertEquals(DrugRoute.UNKNOWN, DrugRoute.fromIngredientCode("12345"))
        assertEquals(DrugRoute.UNKNOWN, DrugRoute.fromIngredientCode(""))
    }

    @Test
    fun `route can be spelled out for products without a code`() {
        assertEquals(DrugRoute.INJECTION, DrugRoute.fromName("injection"))
        assertEquals(DrugRoute.ORAL, DrugRoute.fromName(" Oral "))
        assertEquals(DrugRoute.EXTERNAL, DrugRoute.fromName("external"))
        assertEquals(null, DrugRoute.fromName("weird"))
    }

    @Test
    fun `식약처 products without a code still match by ingredient`() {
        assertEquals(listOf("피나스테라이드", "피나스테라이드"), restrictionNames(product("프로페시아정1밀리그램", "피나스테리드", DrugRoute.UNKNOWN)))
        assertEquals(
            listOf("항생제/스테로이드/보톡스/클로피도그렐"),
            restrictionNames(product("보톡스주100단위", "클로스트리디움보툴리눔독소A형", DrugRoute.INJECTION))
        )
        assertEquals(
            listOf("아스피린", "항생제/스테로이드/보톡스/클로피도그렐"),
            restrictionNames(product("듀오플라빈정", "클로피도그렐황산수소염/아스피린", DrugRoute.UNKNOWN))
        )
    }

    @Test
    fun `식약처 products with English ingredients match the Korean list`() {
        assertEquals(listOf("아스피린"), restrictionNames(product("보령아스트릭스캡슐100밀리그람(아스피린)", "Aspirin Enteric Granules", DrugRoute.UNKNOWN)))
        assertEquals(listOf("피나스테라이드", "피나스테라이드"), restrictionNames(product("프로페시아정1밀리그램", "Finasteride", DrugRoute.UNKNOWN)))
        assertEquals(listOf("이소트레티노인"), restrictionNames(product("로아큐탄연질캡슐10밀리그램", "Isotretinoin", DrugRoute.UNKNOWN)))
        assertEquals(listOf("알리트레티노인"), restrictionNames(product("알리톡연질캡슐10밀리그램", "Alitretinoin", DrugRoute.UNKNOWN)))
        assertEquals(listOf("아시트레틴"), restrictionNames(product("네오티가손캡슐10밀리그램", "Acitretin", DrugRoute.UNKNOWN)))
        assertEquals(
            listOf("아스피린", "항생제/스테로이드/보톡스/클로피도그렐"),
            restrictionNames(product("듀오플라빈정", "Clopidogrel Bisulfate/Aspirin", DrugRoute.UNKNOWN))
        )
        assertEquals(
            listOf("항생제/스테로이드/보톡스/클로피도그렐"),
            restrictionNames(product("보톡스주100단위", "Clostridium Botulinum Toxin Type A", DrugRoute.INJECTION))
        )
        assertEquals(
            listOf("항생제/스테로이드/보톡스/클로피도그렐"),
            restrictionNames(product("덱사주", "Dexamethasone Sodium Phosphate", DrugRoute.INJECTION))
        )
        assertEquals(emptyList<String>(), restrictionNames(product("중외5%포도당생리식염액", "Glucose/Sodium Chloride", DrugRoute.UNKNOWN)))
        assertEquals(emptyList<String>(), restrictionNames(product("어떤트레티노인크림", "Tretinoin", DrugRoute.EXTERNAL)))
    }

    @Test
    fun `the strictest entry decides for a combination product`() {
        val combo = product("플라빅스에이정", "Aspirin Enteric Pellets/Clopidogrel Bisulfate", DrugRoute.UNKNOWN)
        val match = ProductMatch(combo, restrictionsFor(combo, PROHIBITED_DRUGS))
        assertEquals(2, match.restrictions.size)
        assertEquals(7, match.strictestRestriction?.restrictionDays)

        val permanent = PROHIBITED_DRUGS.first { it.restrictionDays < 0 }
        val aspirin = PROHIBITED_DRUGS.first { it.ingredientName == "아스피린" }
        assertEquals(permanent, ProductMatch(combo, listOf(aspirin, permanent)).strictestRestriction)
        assertEquals(null, ProductMatch(combo, emptyList()).strictestRestriction)
    }

    @Test
    fun `a product whose ingredient is on the list maps to that entry`() {
        assertEquals(listOf("아스피린"), restrictionNames(aspirin))
    }

    @Test
    fun `salts and hydrates of a listed ingredient still match`() {
        assertEquals(
            listOf("항생제/스테로이드/보톡스/클로피도그렐"),
            restrictionNames(product("플라빅스정75밀리그램", "클로피도그렐황산수소염"))
        )
    }

    @Test
    fun `other spellings of listed ingredients match`() {
        val names = restrictionNames(product("어떤피나정", "피나스테리드"))
        assertEquals(listOf("피나스테라이드", "피나스테라이드"), names)
        assertEquals(listOf("두타스테라이드"), restrictionNames(product("어떤두타캡슐", "두타스테리드")))
        assertEquals(listOf("아스피린"), restrictionNames(product("어떤해열제", "아세틸살리실산")))
    }

    @Test
    fun `steroids count only as injections`() {
        assertEquals(listOf("항생제/스테로이드/보톡스/클로피도그렐"), restrictionNames(dexamethasoneShot))
        assertEquals(emptyList<String>(), restrictionNames(product("소론도정(프레드니솔론)", "프레드니솔론")))
        assertEquals(emptyList<String>(), restrictionNames(product("실크론지크림", "베타메타손디프로피오네이트", DrugRoute.EXTERNAL)))
    }

    @Test
    fun `botulinum toxin, placenta and clotting factor injections match their entries`() {
        assertEquals(
            listOf("항생제/스테로이드/보톡스/클로피도그렐"),
            restrictionNames(product("어떤톡스주", "클로스트리디움보툴리눔독소A형", DrugRoute.INJECTION))
        )
        assertEquals(
            listOf("태반주사제/혈액응고인자"),
            restrictionNames(product("어떤태반주", "인태반가수분해물", DrugRoute.INJECTION))
        )
        assertEquals(
            listOf("태반주사제/혈액응고인자"),
            restrictionNames(product("어떤인자주", "혈액응고인자8", DrugRoute.INJECTION))
        )
        assertEquals(emptyList<String>(), restrictionNames(product("어떤태반정", "인태반가수분해물")))
    }

    @Test
    fun `an ingredient not on the list matches nothing`() {
        assertEquals(emptyList<String>(), restrictionNames(quetiapine))
        assertEquals(emptyList<String>(), restrictionNames(lidocaineShot))
    }

    @Test
    fun `antibiotics are never guessed, but an unmatched injection is flagged`() {
        assertEquals(emptyList<String>(), restrictionNames(product("어떤세프주", "세프트리악손나트륨", DrugRoute.INJECTION)))
        assertTrue(mayBeRestrictedInjection(ProductMatch(lidocaineShot, emptyList())))
        assertFalse(mayBeRestrictedInjection(ProductMatch(quetiapine, emptyList())))
        assertFalse(mayBeRestrictedInjection(ProductMatch(dexamethasoneShot, restrictionsFor(dexamethasoneShot, PROHIBITED_DRUGS))))
    }

    @Test
    fun `search finds products by name, ingredient and English name, ignoring spaces and case`() {
        val products = listOf(aspirin, quetiapine, dexamethasoneShot)
        assertEquals(listOf(aspirin), searchProducts("한미아스피린", products, PROHIBITED_DRUGS).map { it.product })
        assertEquals(listOf(aspirin), searchProducts("한미 아스피린", products, PROHIBITED_DRUGS).map { it.product })
        assertEquals(listOf(quetiapine), searchProducts("쿠에티아핀", products, PROHIBITED_DRUGS).map { it.product })
        assertEquals(listOf(quetiapine), searchProducts("quety", products, PROHIBITED_DRUGS).map { it.product })
        assertEquals(listOf(dexamethasoneShot), searchProducts("덱사메타손", products, PROHIBITED_DRUGS).map { it.product })
    }

    @Test
    fun `search carries each product's restrictions`() {
        val match = searchProducts("아스피린", listOf(aspirin), PROHIBITED_DRUGS).single()
        assertTrue(match.isRestricted)
        assertEquals(3, match.restrictions.single().restrictionDays)
    }

    @Test
    fun `one character is too short to search products`() {
        assertEquals(emptyList<ProductMatch>(), searchProducts("정", listOf(aspirin, quetiapine), PROHIBITED_DRUGS))
        assertEquals(emptyList<ProductMatch>(), searchProducts("   ", listOf(aspirin), PROHIBITED_DRUGS))
    }

    @Test
    fun `restricted products come first and the limit applies`() {
        val tablet = product("가나정", "가나성분")
        val shot = product("가나주사", "덱사메타손", DrugRoute.INJECTION)
        val results = searchProducts("가나", listOf(tablet, shot), PROHIBITED_DRUGS)
        assertEquals(listOf(shot, tablet), results.map { it.product })
        assertEquals(listOf(shot), searchProducts("가나", listOf(tablet, shot), PROHIBITED_DRUGS, limit = 1).map { it.product })
    }
}
