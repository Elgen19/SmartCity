package com.elgenium.smartcity.models


data class PlacesResponse(
    val results: List<PlaceResult>
)

data class PlaceResult(
    val geometry: Geometry,
    val name: String
)

data class Geometry(
    val location: Location
)

data class Location(
    val lat: Double,
    val lng: Double
)

