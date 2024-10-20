package com.elgenium.smartcity.routes_network_request

data class RouteMatrixRequest(
    val origins: List<RouteMatrixOrigin>,
    val destinations: List<RouteMatrixDestination>,
    val travelMode: String = "DRIVE",  // Example: DRIVE, TRANSIT, WALK
    val routingPreference: String = "TRAFFIC_AWARE", // Optional: TRAFFIC_UNAWARE, TRAFFIC_AWARE, etc.
    val units: String = "METRIC" // Optional: METRIC or IMPERIAL
)

data class RouteMatrixOrigin(
    val waypoint: WaypointMatrix
)

data class RouteMatrixDestination(
    val waypoint: WaypointMatrix
)

data class WaypointMatrix(
    val location: LocationMatrix? = null, // For latitude/longitude
    val placeId: String? = null // For place ID
)

data class LocationMatrix(
    val latLng: LatLngMatrix
)

data class LatLngMatrix(
    val latitude: Double,
    val longitude: Double
)
