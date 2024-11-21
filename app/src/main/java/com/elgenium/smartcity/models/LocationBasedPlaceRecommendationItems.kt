package com.elgenium.smartcity.models

data class LocationBasedPlaceRecommendationItems(
    val name: String,
    val address: String,
    val placeId: String,
    val placeLatlng: String,
    val ratings: String,
    val distance: String,
)
