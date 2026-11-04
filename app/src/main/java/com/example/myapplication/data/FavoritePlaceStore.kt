package com.example.myapplication.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "donation_state"
private const val KEY_FAVORITE_PLACES = "favorite_places"

/**
 * The 헌혈 장소 the donor starred, by [com.example.myapplication.model.BloodCenter.id]
 * ("서울중앙/서울역센터", "한마음/홍대점"). One instance per process, so the list and a restored
 * backup see the same set.
 */
class FavoritePlaceStore private constructor(private val prefs: SharedPreferences) {

    private val _ids = MutableStateFlow(decodeFavorites(prefs.getString(KEY_FAVORITE_PLACES, null)))
    val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    /** Stars [id], or unstars it when it's already starred. */
    fun toggle(id: String) {
        val current = _ids.value
        replaceAll(if (id in current) current - id else current + id)
    }

    fun replaceAll(ids: Set<String>) {
        _ids.value = ids
        prefs.edit {
            if (ids.isEmpty()) remove(KEY_FAVORITE_PLACES) else putString(KEY_FAVORITE_PLACES, encodeFavorites(ids))
        }
    }

    companion object {
        @Volatile
        private var instance: FavoritePlaceStore? = null

        fun get(context: Context): FavoritePlaceStore =
            instance ?: synchronized(this) {
                instance ?: FavoritePlaceStore(
                    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ).also { instance = it }
            }
    }
}

/** One id per line. Ids are "혈액원/이름", which never hold a line break. */
internal fun encodeFavorites(ids: Set<String>): String = ids.sorted().joinToString("\n")

internal fun decodeFavorites(raw: String?): Set<String> =
    raw.orEmpty().split("\n").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
