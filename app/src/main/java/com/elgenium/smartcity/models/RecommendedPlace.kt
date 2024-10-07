package com.elgenium.smartcity.models

import com.google.android.libraries.places.api.model.PhotoMetadata

data class RecommendedPlace(
    val placeId: String,
    val name: String,
    val address: String,
    val placeTypes: List<String>,
    var score: Double = 0.0, // Default score
    var rating: Double = 0.0, // Rating of the place
    var numReviews: Int = 0, // Number of reviews
    var distance: Double = 0.0, // Distance to the user (in meters)
    val photoMetadata: PhotoMetadata?
)
