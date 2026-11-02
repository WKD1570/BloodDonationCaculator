package com.example.myapplication.model

/** How a product is taken, from the 7th character of its 주성분코드. */
enum class DrugRoute(val label: String) {
    ORAL("먹는 약"),
    INJECTION("주사"),
    EXTERNAL("외용"),
    UNKNOWN("");

    companion object {
        /** The route as the product dictionary spells it out ("injection"), when it does. */
        fun fromName(name: String): DrugRoute? = when (name.trim().lowercase()) {
            "oral" -> ORAL
            "injection" -> INJECTION
            "external" -> EXTERNAL
            else -> null
        }

        /** "111001ATE" -> ORAL: A 내복, B 주사, C 외용. */
        fun fromIngredientCode(code: String): DrugRoute = when (code.trim().getOrNull(6)?.uppercaseChar()) {
            'A' -> ORAL
            'B' -> INJECTION
            'C' -> EXTERNAL
            else -> UNKNOWN
        }
    }
}

/**
 * One medicine product from the bundled product dictionary (`drug_products.json`). It carries no
 * restriction of its own - it only links a name people know ("한미아스피린장용정") to the
 * ingredient the official 헌혈 제한 약물 list is written in ("아스피린").
 */
data class DrugProduct(
    val name: String,
    val englishName: String,
    val ingredient: String,
    val route: DrugRoute
)
