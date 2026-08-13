package com.example.agriscout.calendar

import com.example.agriscout.data.local.InventoryItemEntity
import java.util.Locale

data class ProductMatch(
    val primary: InventoryItemEntity?,
    val alternative: InventoryItemEntity?
)

object ProductMatcher {
    fun match(
        productCategory: String,
        preferredProducts: List<String>,
        inventoryItems: List<InventoryItemEntity>
    ): ProductMatch {
        if (productCategory.isBlank()) return ProductMatch(null, null)

        val categoryItems = inventoryItems.filter { item ->
            item.category.equals(productCategory, ignoreCase = true)
        }
        if (categoryItems.isEmpty()) return ProductMatch(null, null)

        val preferred = preferredProducts.map { it.lowercase(Locale.getDefault()) }
        val preferredInStock = categoryItems
            .filter { item -> item.quantity > 0 && preferred.any { pref -> item.name.lowercase(Locale.getDefault()).contains(pref) } }
            .maxByOrNull { it.quantity }
        val anyInStock = categoryItems.filter { it.quantity > 0 }.maxByOrNull { it.quantity }
        val primary = preferredInStock ?: anyInStock

        val alternative = categoryItems
            .filter { item ->
                item.quantity > 0 &&
                    item.id != primary?.id &&
                    (preferred.isEmpty() || preferred.none { pref -> item.name.lowercase(Locale.getDefault()).contains(pref) })
            }
            .maxByOrNull { it.quantity }
            ?: categoryItems
                .filter { it.quantity > 0 && it.id != primary?.id }
                .maxByOrNull { it.quantity }

        return ProductMatch(primary = primary, alternative = alternative)
    }
}
