package com.elgenium.smartcity.network_reponses


data class RoadsResponse(
    val snappedPoints: List<SnappedPoint>
)

data class SnappedPoint(
    val location: RoadLocation,
    val placeId: String,
    val roadId: String
)

data class RoadLocation(
    val latitude: Double,
    val longitude: Double
)
