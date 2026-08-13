package com.example.agriscout.marketplace

import com.example.agriscout.data.local.SupplierProductEntity
import java.util.Locale

data class SupplierProductOffer(
    val product: SupplierProductEntity,
    val matchReason: String
)

/**
 * Maps recommendation product categories to verified supplier listings.
 * Company warehouse stock matching remains in [com.example.agriscout.calendar.ProductMatcher].
 */
object SupplierProductMatcher {
    fun match(
        productCategory: String?,
        cropType: String? = null,
        preferredNames: List<String> = emptyList(),
        products: List<SupplierProductEntity>
    ): List<SupplierProductOffer> {
        if (productCategory.isNullOrBlank()) return emptyList()
        val category = productCategory.trim()
        val crop = cropType?.trim().orEmpty()
        val preferred = preferredNames.map { it.lowercase(Locale.getDefault()) }

        return products
            .asSequence()
            .filter { it.active && it.verified }
            .filter { it.category.equals(category, ignoreCase = true) }
            .filter {
                it.availabilityStatus.equals("available", ignoreCase = true) ||
                    it.availabilityStatus.equals("limited", ignoreCase = true)
            }
            .map { product ->
                val cropMatch = crop.isNotBlank() &&
                    product.cropSuitability
                        .split(",")
                        .map { it.trim() }
                        .any { it.equals(crop, ignoreCase = true) }
                val nameMatch = preferred.any { pref ->
                    product.name.lowercase(Locale.getDefault()).contains(pref)
                }
                val reason = buildString {
                    append("Category: ${product.category}")
                    if (cropMatch) append(" · Suitable for $crop")
                    if (nameMatch) append(" · Matches preferred product")
                    append(" · ${product.supplierName}")
                    append(" · ${product.availabilityStatus}")
                }
                val rank = (if (nameMatch) 4 else 0) +
                    (if (cropMatch) 2 else 0) +
                    (if (product.availabilityStatus.equals("available", ignoreCase = true)) 1 else 0)
                Triple(rank, product, reason)
            }
            .sortedByDescending { it.first }
            .map { SupplierProductOffer(product = it.second, matchReason = it.third) }
            .toList()
    }
}
