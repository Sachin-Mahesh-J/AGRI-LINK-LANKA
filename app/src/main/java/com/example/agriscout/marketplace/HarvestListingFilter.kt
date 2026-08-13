package com.example.agriscout.marketplace

import com.example.agriscout.data.local.HarvestListingEntity

/**
 * Simple client-side discovery filters for harvest listings.
 * Keeps buyer search practical without a separate search service.
 */
object HarvestListingFilter {
    fun discover(
        listings: List<HarvestListingEntity>,
        cropType: String? = null,
        districtOrLocation: String? = null,
        minQuantity: Double? = null,
        maxQuantity: Double? = null,
        status: String? = "listed",
        minConfidence: Int? = null,
        publicOnly: Boolean = true
    ): List<HarvestListingEntity> {
        return listings.filter { listing ->
            if (publicOnly) {
                if (!listing.active) return@filter false
                if (listing.visibility.equals("hidden", ignoreCase = true)) return@filter false
                if (!listing.verified) return@filter false
            }
            if (!status.isNullOrBlank() &&
                !listing.status.equals(status, ignoreCase = true)
            ) {
                return@filter false
            }
            if (!cropType.isNullOrBlank() &&
                !listing.cropType.contains(cropType.trim(), ignoreCase = true)
            ) {
                return@filter false
            }
            if (!districtOrLocation.isNullOrBlank()) {
                val needle = districtOrLocation.trim()
                val locationMatch =
                    listing.district.contains(needle, ignoreCase = true) ||
                        listing.locationText.contains(needle, ignoreCase = true)
                if (!locationMatch) return@filter false
            }
            val qty = listing.estimatedQuantityMax ?: listing.estimatedQuantityMin
            if (minQuantity != null && (qty == null || qty < minQuantity)) return@filter false
            if (maxQuantity != null && (qty == null || qty > maxQuantity)) return@filter false
            if (minConfidence != null && (listing.confidence ?: 0) < minConfidence) {
                return@filter false
            }
            true
        }
    }
}
