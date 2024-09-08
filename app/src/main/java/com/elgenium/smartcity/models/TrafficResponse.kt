package com.elgenium.smartcity.models

data class TrafficResponse(
    val flowSegmentData: FlowSegmentData?
)

data class FlowSegmentData(
    val frc: String?,
    val currentSpeed: Double?,
    val freeFlowSpeed: Double?,
    val currentTravelTime: Int?,
    val freeFlowTravelTime: Int?,
    val confidence: Double?,
    val roadClosure: Boolean?,
    val coordinates: Coordinates? // Updated to match response structure
)

data class Coordinates(
    val coordinate: List<Coordinate>? // Nested list of coordinates
)

data class Coordinate(
    val latitude: Double,
    val longitude: Double
)


