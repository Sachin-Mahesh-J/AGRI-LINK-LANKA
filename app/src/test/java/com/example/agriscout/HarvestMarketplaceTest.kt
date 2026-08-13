package com.example.agriscout

import com.example.agriscout.data.local.HarvestListingEntity
import com.example.agriscout.marketplace.HarvestListingFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HarvestMarketplaceTest {
    @Test
    fun filterKeepsVerifiedPublicListedHarvestsByCropAndDistrict() {
        val listings = listOf(
            HarvestListingEntity(
                id = "hl-1",
                farmId = "farm-1",
                cropType = "Rice",
                district = "Anuradhapura",
                locationText = "Medawachchiya, Anuradhapura",
                estimatedQuantityMax = 12.0,
                confidence = 72,
                status = "listed",
                visibility = "public",
                active = true,
                verified = true,
                createdAt = 1L,
                updatedAt = 1L
            ),
            HarvestListingEntity(
                id = "hl-2",
                farmId = "farm-2",
                cropType = "Maize",
                district = "Kurunegala",
                estimatedQuantityMax = 8.0,
                confidence = 40,
                status = "listed",
                visibility = "public",
                active = true,
                verified = true,
                createdAt = 1L,
                updatedAt = 1L
            ),
            HarvestListingEntity(
                id = "hl-3",
                farmId = "farm-3",
                cropType = "Rice",
                district = "Anuradhapura",
                estimatedQuantityMax = 10.0,
                confidence = 80,
                status = "listed",
                visibility = "public",
                active = true,
                verified = false,
                createdAt = 1L,
                updatedAt = 1L
            )
        )

        val found = HarvestListingFilter.discover(
            listings = listings,
            cropType = "Rice",
            districtOrLocation = "Anuradhapura",
            minConfidence = 50
        )

        assertEquals(1, found.size)
        assertEquals("hl-1", found.first().id)
        assertTrue(found.first().verified)
    }
}
