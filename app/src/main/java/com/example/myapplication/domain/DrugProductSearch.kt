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

    /**
     * The entries to choose between when the list restricts this ingredient differently depending
     * on why it was taken - 메토트렉세이트 for 건선 (4주) or for 암·자가면역질환 (영구), 와파린 to
     * prevent a clot (7일) or to treat one (영구). Only the donor knows which applies, so the
     * strictest isn't assumed for them. Empty when the period doesn't depend on it; two entries
     * with the same period (피나스테라이드 for 탈모 or 전립선) aren't a choice.
     */
    val purposeChoices: List<RestrictedDrug>
        get() = restrictions.groupBy { it.ingredientName }.values
            .firstOrNull { entries -> entries.distinctBy { it.restrictionDays }.size > 1 }
            .orEmpty()
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
    "placenta" to "태반",
    "dipyridamole" to "디피리다몰",
    "indometacin" to "인도메타신",
    "indomethacin" to "인도메타신",
    "piroxicam" to "피록시캄",
    "펜타민" to "펜터민",
    "phentermine" to "펜터민",
    "phendimetrazine" to "펜디메트라진",
    "리라글루타이드" to "리라글루티드",
    "liraglutide" to "리라글루티드",
    "tranilast" to "트라닐라스트",
    "praziquantel" to "프라지콴텔",
    "methotrexate" to "메토트렉세이트",
    "warfarin" to "와파린",
    // Before "heparin", so a heparinoid cream isn't read as heparin.
    "heparinoid" to "헤파리노이드",
    "heparin" to "헤파린",
    "hirudin" to "히루딘",
    "denosumab" to "데노수맙",
    "propylthiouracil" to "프로필티오우라실",
    "thiamazole" to "메티마졸",
    "티아마졸" to "메티마졸",
    "methimazole" to "메티마졸"
)

/** Antibiotics have too many names to recognise reliably, so this class word never matches. */
private const val ANTIBIOTIC_CLASS = "항생제"

/** Systemic corticosteroids, as they appear in ingredient names. */
private val STEROID_INGREDIENTS = listOf(
    "덱사메타손", "프레드니솔론", "히드로코르티손", "하이드로코르티손", "트리암시놀론", "베타메타손", "데플라자코트",
    "dexamethasone", "prednisolone", "hydrocortisone", "triamcinolone", "betamethasone", "deflazacort"
)
private val BOTULINUM_INGREDIENTS = listOf("보툴리눔", "보툴리늄")

/** Common 해열진통소염제 (아스피린, 디피리다몰, 인도메타신 and 피록시캄 have entries of their own). */
private val PAIN_RELIEVER_INGREDIENTS = listOf(
    // 덱시부프로펜 is spelled out: unlike "dexibuprofen", the Korean name doesn't contain 이부프로펜.
    "아세트아미노펜", "이부프로펜", "덱시부프로펜", "나프록센", "록소프로펜", "디클로페낙", "아세클로페낙",
    "멜록시캄", "세레콕시브", "케토프로펜", "케토롤락", "에토돌락", "펠루비프로펜", "플루르비프로펜",
    "acetaminophen", "paracetamol", "ibuprofen", "naproxen", "loxoprofen", "diclofenac", "aceclofenac",
    "meloxicam", "celecoxib", "ketoprofen", "ketorolac", "etodolac", "pelubiprofen", "flurbiprofen"
)

/** How a product shows it belongs to a class named in the list: one of [ingredients], taken a way in [routes]. */
private class DrugClass(val ingredients: List<String>, val routes: Set<DrugRoute>)

/**
 * Words in the official list that name a class of drug rather than an ingredient, as
 * [squash]ed. The combined entry restricts "스테로이드제(주사), 보톡스(주사)" as injections;
 * 태반주사제 and 혈액응고인자 are injectables too. A 해열진통소염제 counts however it is taken -
 * creams and patches never get this far (see [isTopical]), so a 외용 one here is a suppository.
 * Class words not here (근육이완제, 항진균제 …) name too many ingredients to list and never
 * match a product.
 */
private val DRUG_CLASSES: Map<String, DrugClass> = mapOf(
    "스테로이드" to DrugClass(STEROID_INGREDIENTS, setOf(DrugRoute.INJECTION)),
    "스테로이드제(먹는약)" to DrugClass(STEROID_INGREDIENTS, setOf(DrugRoute.ORAL)),
    "보톡스" to DrugClass(BOTULINUM_INGREDIENTS, setOf(DrugRoute.INJECTION)),
    "태반주사제" to DrugClass(listOf("태반"), setOf(DrugRoute.INJECTION)),
    "혈액응고인자" to DrugClass(listOf("혈액응고"), setOf(DrugRoute.INJECTION)),
    "해열진통소염제" to DrugClass(PAIN_RELIEVER_INGREDIENTS, DrugRoute.entries.toSet())
)

/** In a 외용 product's name, these mean a suppository, which acts on the whole body. */
private val SUPPOSITORY_WORDS = listOf("좌약", "좌제")

/**
 * Creams, eye drops, sprays and other 외용 products don't restrict donation (판정기준 표1-1);
 * suppositories are the exception.
 */
private fun isTopical(product: DrugProduct): Boolean =
    product.route == DrugRoute.EXTERNAL && SUPPOSITORY_WORDS.none { it in product.name }

private fun String.squash(): String = replace(" ", "").lowercase()

private fun normalizedIngredient(product: DrugProduct): String {
    var ingredient = product.ingredient.squash()
    INGREDIENT_SPELLINGS.forEach { (from, to) -> ingredient = ingredient.replace(from, to) }
    return ingredient
}

/**
 * The entries of [drugs] (the official list) that [product]'s ingredient falls under. Plain
 * ingredient names match wherever they appear in the product's ingredient, so salts and
 * hydrates count ("클로피도그렐황산수소염" is 클로피도그렐). Class words match through
 * [DRUG_CLASSES] - steroids as injections or tablets by their own entries, botulinum toxin and
 * 태반·혈액응고 as injections, common pain relievers. A topical product matches nothing.
 * Antibiotics have too many names to recognise reliably, so an unmatched injection is flagged
 * in the UI instead (see [mayBeRestrictedInjection]).
 */
fun restrictionsFor(product: DrugProduct, drugs: List<RestrictedDrug>): List<RestrictedDrug> {
    if (isTopical(product)) return emptyList()
    val ingredient = normalizedIngredient(product)
    if (ingredient.isEmpty()) return emptyList()
    return drugs.filter { drug ->
        drug.ingredientName.split('/').map { it.squash() }.filter { it.isNotEmpty() }.any { word ->
            val drugClass = DRUG_CLASSES[word]
            when {
                word == ANTIBIOTIC_CLASS -> false
                drugClass != null -> product.route in drugClass.routes && drugClass.ingredients.any { it in ingredient }
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
