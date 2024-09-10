package com.elgenium.smartcity.network_reponses

data class PlacesResponse(
    val results: List<Place>
)

data class Place(
    val place_id: String?, // Ensure this field matches the API response
    val name: String,
    val geometry: Geometry
)

data class Geometry(
    val location: Location
)

data class Location(
    val lat: Double,
    val lng: Double
)

