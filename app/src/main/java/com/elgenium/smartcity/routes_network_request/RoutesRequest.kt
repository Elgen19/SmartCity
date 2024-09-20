package com.elgenium.smartcity.routes_network_request


data class RoutesRequest(
    val origin: Location,
    val destination: Location,
    val travelMode: String = "DRIVE",
    val routingPreference: String? = null, // Nullable
    val computeAlternativeRoutes: Boolean = false,
    val routeModifiers: RouteModifiers = RouteModifiers(),
    val languageCode: String = "en-US",
    val units: String = "IMPERIAL",
    val extraComputations: List<ExtraComputation>? = null)

data class Location(val location: LatLng)
data class LatLng(val latLng: Coordinates)
data class Coordinates(val latitude: Double, val longitude: Double)

data class RouteModifiers(
    val avoidTolls: Boolean = false,
    val avoidHighways: Boolean = false,
    val avoidFerries: Boolean = false
)

enum class ExtraComputation {
    EXTRA_COMPUTATION_UNSPECIFIED,
    TOLLS,
    FUEL_CONSUMPTION,
    TRAFFIC_ON_POLYLINE,
    HTML_FORMATTED_NAVIGATION_INSTRUCTIONS
}


