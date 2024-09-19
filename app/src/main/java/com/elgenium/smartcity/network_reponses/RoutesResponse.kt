package com.elgenium.smartcity.network_reponses

data class RoutesResponse(
    val routes: List<Routes>
)

data class Routes(
    val distanceMeters: Int,
    val duration: String,
    val polyline: Polyline
)

data class Polyline(
    val encodedPolyline: String
)
