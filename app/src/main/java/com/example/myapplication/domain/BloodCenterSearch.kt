package com.example.myapplication.domain

import com.example.myapplication.model.BloodCenter

/** Short province names in the order the region chips show them. */
val PROVINCE_ORDER = listOf(
    "서울", "경기", "인천", "강원", "대전", "세종", "충북", "충남", "광주",
    "전북", "전남", "대구", "경북", "부산", "울산", "경남", "제주"
)

/**
 * Province spellings map apps don't know yet, mapped to ones they do. The 2026-07 CSV writes
 * 순천 under the merged 전남광주 name while every other 전남 row still says 전남.
 */
private val MAP_ALIASES = mapOf("전남광주" to "전남")

/** Long and alternative spellings of a province, mapped to the short form most rows use. */
private val PROVINCE_ALIASES = MAP_ALIASES + mapOf(
    "서울특별시" to "서울",
    "경기도" to "경기",
    "인천광역시" to "인천",
    "강원도" to "강원",
    "강원특별자치도" to "강원",
    "대전광역시" to "대전",
    "세종시" to "세종",
    "세종특별자치시" to "세종",
    "충청북도" to "충북",
    "충청남도" to "충남",
    "광주광역시" to "광주",
    "전라북도" to "전북",
    "전북특별자치도" to "전북",
    "전라남도" to "전남",
    "대구광역시" to "대구",
    "경상북도" to "경북",
    "부산광역시" to "부산",
    "울산광역시" to "울산",
    "경상남도" to "경남",
    "제주특별자치도" to "제주",
    "제주도" to "제주"
)

private val WHITESPACE = Regex("\\s+")
private val PARENTHETICAL = Regex("\\([^)]*\\)")

/** A building number on its own: "405", "462-5". */
private val BUILDING_NUMBER = Regex("^\\d+(-\\d+)?$")

/** A road name with the building number run on: "강남대로364", "행복로13", "76번길63". */
private val ROAD_WITH_NUMBER = Regex("^(.+(?:로|길))(\\d+(?:-\\d+)?)$")

private fun tokens(address: String): List<String> {
    val parts = address.replace(PARENTHETICAL, " ").trim().split(WHITESPACE).filter { it.isNotEmpty() }
    if (parts.isEmpty()) return parts
    return listOf(MAP_ALIASES[parts[0]] ?: parts[0]) + parts.drop(1)
}

/**
 * The province of an address in its short form ("서울", "경북"), which is where a center actually
 * is - unlike its 혈액원, which can cover a neighbouring province (서울중앙 runs 고양's centers).
 */
fun provinceOf(address: String): String {
    val first = tokens(address).firstOrNull().orEmpty()
    return PROVINCE_ALIASES[first] ?: first
}

/** Each province with how many centers it has, in [PROVINCE_ORDER], unknown spellings last. */
fun provinceCounts(centers: List<BloodCenter>): List<Pair<String, Int>> {
    val counts = centers.groupingBy { provinceOf(it.address) }.eachCount()
    val known = PROVINCE_ORDER.mapNotNull { province -> counts[province]?.let { province to it } }
    val others = counts.filterKeys { it.isNotEmpty() && it !in PROVINCE_ORDER }.toList().sortedBy { it.first }
    return known + others
}

/**
 * The address cut right after its building number - "서울 중구 한강대로 405" out of "서울 중구
 * 한강대로 405 서울역사 지하1층 B05호". Map apps find that reliably; floor, unit and building names
 * after it only cause misses. Works the same for lot-number (지번) addresses.
 *
 * Returns null when no building number can be found, as in "대전 중구 중앙로 164번길 이안경원 2층".
 */
fun roadAddressOf(address: String): String? {
    val parts = tokens(address.substringBefore(','))
    val kept = mutableListOf<String>()
    for ((index, part) in parts.withIndex()) {
        // The first token is always the province, never a number.
        if (index > 0) {
            if (BUILDING_NUMBER.matches(part)) return (kept + part).joinToString(" ")
            ROAD_WITH_NUMBER.matchEntire(part)?.let { match ->
                return (kept + match.groupValues[1] + match.groupValues[2]).joinToString(" ")
            }
        }
        kept += part
    }
    return null
}

/**
 * What to search a map app for: the road address, or the address minus anything after a comma
 * (always floor or unit detail in this data) when there's no building number.
 */
fun mapQueryOf(center: BloodCenter): String =
    roadAddressOf(center.address) ?: tokens(center.address.substringBefore(',')).joinToString(" ")

/** The "헌혈의 집" people habitually type in front of a name is in every row, so it matches nothing. */
private val NOISE = listOf("헌혈의 집", "헌혈의집")

private fun BloodCenter.searchText(): String =
    "$name $bloodBank 혈액원 $address ${placeLabel.orEmpty()}".replace(" ", "")

/**
 * Centers matching every word of [query] in their name, 혈액원 or address, ignoring spaces - so
 * "강남", "서울역", "부산 서면" and "헌혈의집 수원" all work. Name matches come first, otherwise the
 * data's own order is kept. A query with nothing to match on returns [centers] unchanged.
 */
fun searchCenters(centers: List<BloodCenter>, query: String): List<BloodCenter> {
    var cleaned = query
    NOISE.forEach { cleaned = cleaned.replace(it, " ") }
    val words = cleaned.trim().split(WHITESPACE).filter { it.isNotEmpty() }
    if (words.isEmpty()) return centers

    return centers
        .filter { center ->
            val text = center.searchText()
            words.all { it in text }
        }
        .sortedBy { center -> if (words.all { it in center.name }) 0 else 1 }
}

/** The list as shown: narrowed to [province] and [machine] when picked, then searched. */
fun filterCenters(
    centers: List<BloodCenter>,
    query: String,
    province: String?,
    machine: MachineFilter? = null
): List<BloodCenter> {
    val narrowed = centers.filter { center ->
        (province == null || provinceOf(center.address) == province) && (machine == null || center.has(machine))
    }
    return searchCenters(narrowed, query)
}

/**
 * [visible] split for display: under 전체 ([province] null), the centers in [preferred] - 마이페이지's
 * 자주 헌혈하는 지역 - come first under their own heading, then the rest. Both parts keep their
 * order, so search ranking still holds within each. With a province picked the list is a single
 * province already, so nothing is split off; nor when nothing is preferred.
 */
fun splitPreferred(
    visible: List<BloodCenter>,
    province: String?,
    preferred: String?
): Pair<List<BloodCenter>, List<BloodCenter>> =
    if (province != null || preferred == null) emptyList<BloodCenter>() to visible
    else visible.partition { provinceOf(it.address) == preferred }

/** Province chips with [preferred]'s moved to the front, right after 전체; the rest keep their order. */
fun preferredFirst(counts: List<Pair<String, Int>>, preferred: String?): List<Pair<String, Int>> =
    counts.sortedBy { it.first != preferred }
