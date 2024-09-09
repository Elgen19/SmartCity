package com.elgenium.smartcity.network_reponses

data class PlaceDistanceResponse(
    val routes: List<Route>
)

data class Route(
    val legs: List<Leg>
)

data class Leg(
    val distance: Distance
)

data class Distance(
    val text: String
)

