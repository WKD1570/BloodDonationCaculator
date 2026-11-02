package com.example.myapplication.data

import android.content.Context
import com.example.myapplication.model.DrugProduct
import com.example.myapplication.model.DrugRoute
import com.google.gson.Gson
import com.google.gson.JsonParseException
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Built by `tools/build_drug_products.py` from a drug list CSV. */
const val DRUG_PRODUCTS_ASSET = "drug_products.json"

class DrugProductDataException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The bundled product dictionary.
 *
 * @param asOf The date in the source CSV's file name, as "2026.07.20", when it has one.
 * @param sourceNote Where the products come from, with dates, as the build script wrote it -
 * "의약품 목록(2026.07.20), 식약처 의약품 제품 허가정보(2026.11.03)".
 */
data class DrugProductList(val products: List<DrugProduct>, val asOf: String?, val sourceNote: String? = null)

private class DrugProductsJson(
    val source: String? = null,
    val sourceNote: String? = null,
    val products: List<DrugProductJson?>? = null
)

/** All-nullable for the same reason as ProhibitedDrugJson: Gson bypasses Kotlin's null checks. */
private class DrugProductJson(
    val name: String? = null,
    val englishName: String? = null,
    val ingredient: String? = null,
    val ingredientCode: String? = null,
    /** Spelled out only for products without a 주성분코드 to read it from. */
    val route: String? = null
)

private val SOURCE_DATE = Regex("(20\\d{2})(\\d{2})(\\d{2})")

/**
 * Parses the product dictionary. Kept free of Android types so JVM unit tests can use it
 * directly. Entries without a product name or ingredient are skipped.
 */
fun parseDrugProducts(input: InputStream): DrugProductList {
    val raw = try {
        Gson().fromJson(input.reader(Charsets.UTF_8), DrugProductsJson::class.java)
    } catch (e: JsonParseException) {
        throw DrugProductDataException("$DRUG_PRODUCTS_ASSET is not valid JSON", e)
    }
    val products = raw?.products.orEmpty().filterNotNull().mapNotNull { json ->
        val name = json.name?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val ingredient = json.ingredient?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        DrugProduct(
            name = name,
            englishName = json.englishName?.trim().orEmpty(),
            ingredient = ingredient,
            route = json.route?.let(DrugRoute::fromName)
                ?: DrugRoute.fromIngredientCode(json.ingredientCode.orEmpty())
        )
    }
    if (products.isEmpty()) throw DrugProductDataException("$DRUG_PRODUCTS_ASSET contained no products")
    val asOf = raw?.source?.let { SOURCE_DATE.find(it) }?.destructured?.let { (y, m, d) -> "$y.$m.$d" }
    return DrugProductList(products, asOf, raw?.sourceNote?.trim()?.takeIf { it.isNotEmpty() })
}

/** Reads and parses the bundled dictionary, caching it for the process lifetime. Offline. */
class DrugProductRepository(context: Context) {
    private val appContext = context.applicationContext

    @Volatile
    private var cached: DrugProductList? = null

    suspend fun load(): DrugProductList = withContext(Dispatchers.IO) {
        cached ?: try {
            appContext.assets.open(DRUG_PRODUCTS_ASSET).use(::parseDrugProducts)
        } catch (e: IOException) {
            throw DrugProductDataException("Could not read asset $DRUG_PRODUCTS_ASSET", e)
        }.also { cached = it }
    }
}
