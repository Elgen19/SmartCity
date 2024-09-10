package com.elgenium.smartcity.models

data class SavedPlace(
    val id: String = "",
    val name: String? = "",
    val address: String? = "",
    val phoneNumber: String? = "",
    val latLng: String? = "", // Could be a custom LatLng object instead of a String
    val openingDays: String? = "",
    val openingHours: String? = "", // Store as a formatted string or custom object
    val rating: String? = "",
    val websiteUri: String? = "",
    val distance: String? = "", // Formatted distance string
    val openingStatus: String? = "", // e.g., "Open" or "Closed"
)
