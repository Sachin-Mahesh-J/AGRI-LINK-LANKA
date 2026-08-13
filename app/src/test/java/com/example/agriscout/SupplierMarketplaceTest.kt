package com.example.agriscout

import com.example.agriscout.data.local.SupplierProductEntity
import com.example.agriscout.marketplace.SupplierProductMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupplierMarketplaceTest {
    @Test
    fun matcherMapsCategoryToVerifiedAvailableSupplierProducts() {
        val products = listOf(
            SupplierProductEntity(
                id = "sp-1",
                supplierId = "sup-1",
                supplierName = "GreenField",
                name = "Granular Urea 46%",
                category = "Fertilizers",
                cropSuitability = "Rice,Maize",
                availabilityStatus = "available",
                active = true,
                verified = true,
                updatedAt = 1L
            ),
            SupplierProductEntity(
                id = "sp-2",
                supplierId = "sup-2",
                supplierName = "CropCare",
                name = "Neem Oil",
                category = "Chemicals",
                cropSuitability = "Rice",
                availabilityStatus = "available",
                active = true,
                verified = true,
                updatedAt = 1L
            ),
            SupplierProductEntity(
                id = "sp-3",
                supplierId = "sup-1",
                supplierName = "GreenField",
                name = "Draft fertilizer",
                category = "Fertilizers",
                availabilityStatus = "available",
                active = true,
                verified = false,
                updatedAt = 1L
            )
        )

        val offers = SupplierProductMatcher.match(
            productCategory = "Fertilizers",
            cropType = "Rice",
            preferredNames = listOf("Urea"),
            products = products
        )

        assertEquals(1, offers.size)
        assertEquals("sp-1", offers.first().product.id)
        assertTrue(offers.first().matchReason.contains("Category: Fertilizers"))
        assertTrue(offers.first().matchReason.contains("GreenField"))
    }
}
