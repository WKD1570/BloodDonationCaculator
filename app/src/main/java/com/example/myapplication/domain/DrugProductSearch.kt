package com.example.myapplication.domain

import com.example.myapplication.model.DrugProduct
import com.example.myapplication.model.DrugRoute
import com.example.myapplication.model.RestrictedDrug

/** A product found by search, with the official list entries its ingredient falls under. */
data class ProductMatch(val product: DrugProduct, val restrictions: List<RestrictedDrug>) {
    val isRestricted: Boolean get() = restrictions.isNotEmpty()

    /**
     * The entry that decides when donating is possible again - a lifetime ban, else the longest
     * wait. A combination product like 플라빅스에이정 (클로피도그렐 + 아스피린) falls under two
     * entries, and the 1-week one, not the 3-day one, is what holds.
     */
    val strictestRestriction: RestrictedDrug?
        get() = restrictions.maxByOrNull { if (it.restrictionDays < 0) Int.MAX_VALUE else it.restrictionDays }
}

/** Shortest query that searches products; one character would match nearly all of them. */
const val MIN_PRODUCT_QUERY_LENGTH = 2

/** Most products a search returns; "피나스테리드" alone matches some 200. */
const val PRODUCT_RESULT_LIMIT = 50

/**
 * Other spellings of restricted ingredients that product data uses, mapped to the official
 * list's spelling. 아세틸살리실산 is aspirin's chemical name. 식약처 허가정보 writes ingredients
 * in English ("Aspirin Enteric Granules", "Clostridium Botulinum Toxin Type A"), so the English
 * names are here too, lower case and without spaces as [normalizedIngredient] compares them.
 */
private val INGREDIENT_SPELLINGS = mapOf(
    "피나스테리드" to "피나스테라이드",
    "두타스테리드" to "두타스테라이드",
    "아세틸살리실산" to "아스피린",
    "acetylsalicylicacid" to "아스피린",
    "aspirin" to "아스피린",
    "clopidogrel" to "클로피도그렐",
    "ticlopidine" to "티클로피딘",
    "finasteride" to "피나스테라이드",
    "dutasteride" to "두타스테라이드",
    "isotretinoin" to "이소트레티노인",
    "alitretinoin" to "알리트레티노인",
    "thalidomide" to "탈리도마이드",
    "vismodegib" to "비스모데깁",
    "acitretin" to "아시트레틴",
    "etretinate" to "에트레티네이트",
    "botulinum" to "보툴리눔",
    "placenta" to "태반"
)

/**
 * Words in the official list that name a class of drug rather than an ingredient. The list
 * restricts them only as injections - "항생제(주사), 스테로이드제(주사), 보톡스(주사)".
 */
private const val ANTIBIOTIC_CLASS = "항생제"
private const val STEROID_CLASS = "스테로이드"
private const val BOTOX_CLASS = "보톡스"

/** Systemic corticosteroids, as they appear in ingredient names. */
private val STEROID_INGREDIENTS = listOf(
    "덱사메타손", "프레드니솔론", "히드로코르티손", "하이드로코르티손", "트리암시놀론", "베타메타손",
    "dexamethasone", "prednisolone", "hydrocortisone", "triamcinolone", "betamethasone"
)
private val BOTULINUM_INGREDIENTS = listOf("보툴리눔", "보툴리늄")

/** "태반주사제/혈액응고인자": both are injectables; matched on these words in the ingredient. */
private val PLACENTA_OR_CLOTTING_WORDS = listOf("태반", "혈액응고")

private fun String.squash(): String = replace(" ", "").lowercase()

private fun normalizedIngredient(product: DrugProduct): String {
    var ingredient = product.ingredient.squash()
    INGREDIENT_SPELLINGS.forEach { (from, to) -> ingredient = ingredient.replace(from, to) }
    return ingredient
}

/**
 * The entries of [drugs] (the official list) that [product]'s ingredient falls under. Plain
 * ingredient names match wherever they appear in the product's ingredient, so salts and
 * hydrates count ("클로피도그렐황산수소염" is 클로피도그렐). The class entries match only for
 * injections: steroids and botulinum toxin by ingredient name, 태반·혈액응고 by keyword.
 * Antibiotics have too many names to recognise reliably, so an unmatched injection is flagged
 * in the UI instead (see [mayBeRestrictedInjection]).
 */
fun restrictionsFor(product: DrugProduct, drugs: List<RestrictedDrug>): List<RestrictedDrug> {
    val ingredient = normalizedIngredient(product)
    if (ingredient.isEmpty()) return emptyList()
    val injection = product.route == DrugRoute.INJECTION
    return drugs.filter { drug ->
        drug.ingredientName.split('/').map { it.squash() }.filter { it.isNotEmpty() }.any { word ->
            when {
                word == ANTIBIOTIC_CLASS -> false
                word == STEROID_CLASS -> injection && STEROID_INGREDIENTS.any { it in ingredient }
                word == BOTOX_CLASS -> injection && BOTULINUM_INGREDIENTS.any { it in ingredient }
                PLACENTA_OR_CLOTTING_WORDS.any { word.startsWith(it) } ->
                    injection && PLACENTA_OR_CLOTTING_WORDS.any { it in ingredient }
                else -> word in ingredient
            }
        }
    }
}

/**
 * An injection that matched nothing could still be an antibiotic or steroid shot, which the
 * official list restricts for a week - worth saying so rather than reporting it as unlisted.
 */
fun mayBeRestrictedInjection(match: ProductMatch): Boolean =
    !match.isRestricted && match.product.route == DrugRoute.INJECTION

/**
 * Products whose name, English name or ingredient contains [query] (ignoring spaces and case),
 * each checked against [drugs]. Restricted products come first; otherwise the dictionary's order
 * is kept. Queries shorter than [MIN_PRODUCT_QUERY_LENGTH] find nothing.
 */
fun searchProducts(
    query: String,
    products: List<DrugProduct>,
    drugs: List<RestrictedDrug>,
    limit: Int = PRODUCT_RESULT_LIMIT
): List<ProductMatch> {
    val needle = query.squash()
    if (needle.length < MIN_PRODUCT_QUERY_LENGTH) return emptyList()
    return products
        .filter { needle in it.name.squash() || needle in it.ingredient.squash() || needle in it.englishName.squash() }
        .map { ProductMatch(it, restrictionsFor(it, drugs)) }
        .sortedBy { if (it.isRestricted) 0 else 1 }
        .take(limit)
}
