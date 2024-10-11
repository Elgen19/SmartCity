package com.elgenium.smartcity.models


data class ViewedPlace(
    val placeId: String? = "",
    val placeName: String? = "",
    val placeAddress: String? = "",
    val placeLatLng: String? = "", // Store as String for Firebase
    val timestamp: Long = 0, // Timestamp for when it was viewed
    val type: List<String> = emptyList() // List of types for the place
)

