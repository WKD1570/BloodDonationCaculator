package com.example.myapplication.model

/**
 * @param restrictionDays Days after taking the drug before donation is allowed again, or -1 for
 * a permanent restriction.
 */
data class RestrictedDrug(
    val category: String,
    val ingredientName: String,
    val representativeBrands: String,
    val restrictionDays: Int,
    val restrictionText: String
)

val RestrictedDrug.isPermanentlyRestricted: Boolean get() = restrictionDays < 0
