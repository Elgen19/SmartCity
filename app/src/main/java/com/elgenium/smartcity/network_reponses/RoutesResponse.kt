package com.elgenium.smartcity.network_reponses

data class RoutesResponse(
    val routes: List<Routes>
)

data class Routes(
    val legs: List<Legs>,
    val distanceMeters: Int,
    val duration: String,
    val polyline: Polyline,
    val description: String?,
    val travelAdvisory: TravelAdvisory?,
)

data class Legs(
    val distanceMeters: Int, //check
    val duration: String, //check
    val polyline: Polyline, //check
    val steps: List<Step>,
    val travelAdvisory: TravelAdvisory?,
)

data class Step(
    val distanceMeters: Int, //check
    val staticDuration: String, //check
    val polyline: Polyline, //check
    val navigationInstruction: NavigationInstruction,
    val startLocation: Locations,
    val endLocation: Locations
)

data class NavigationInstruction(
    val maneuver: String,
    val instructions: String
)


data class Polyline(
    val encodedPolyline: String
)

data class TravelAdvisory(
    val speedReadingIntervals: List<SpeedReadingInterval>
)

data class SpeedReadingInterval(
    val startPolylinePointIndex: Int,
    val endPolylinePointIndex: Int,
    val speed: String
)

data class Locations (
    val latLng: LatLng,
    val heading: Int
)

data class LatLng (
    val latitude: Number,
    val longitude: Number
)
