package com.elgenium.smartcity.network_reponses

data class GeocodingResponse(
    val results: List<Result>,
    val status: String
)

data class Result(
    val geometry: Geometries
)

data class Geometries(
    val location: PlaceLocation
)

data class PlaceLocation(
    val lat: Double,
    val lng: Double
)

