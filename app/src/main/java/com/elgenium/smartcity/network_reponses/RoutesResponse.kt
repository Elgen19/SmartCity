package com.elgenium.smartcity.network_reponses


import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class RoutesResponse(
    val routes: List<Routes>
) : Parcelable

@Parcelize
data class Routes(
    val legs: List<Legs> = emptyList(),
    val distanceMeters: Int,
    val duration: String,
    val polyline: Polyline,
    val description: String?,
    val travelAdvisory: TravelAdvisory? = null,
    val routeToken: String,
    val optimizedIntermediateWaypointIndex: List<Int> = emptyList()
) : Parcelable

@Parcelize
data class Legs(
    val distanceMeters: Int,
    val duration: String,
    val polyline: Polyline,
    val steps: List<Step> = emptyList(),
    val travelAdvisory: TravelAdvisory? = null
) : Parcelable

@Parcelize
data class Step(
    val distanceMeters: Int,
    val staticDuration: String,
    val polyline: Polyline,
    val navigationInstruction: NavigationInstruction,
    val startLocation: Locations,
    val endLocation: Locations,
    val transitDetails: TransitDetails,
    val travelMode: RouteTravelMode
) : Parcelable


enum class RouteTravelMode {
    TRAVEL_MODE_UNSPECIFIED,
    DRIVE,
    BICYCLE,
    WALK,
    TWO_WHEELER,
    TRANSIT
}


@Parcelize
data class TransitDetails(
    val stopDetails: TransitStopDetails,
    val headsign: String,
    val headway: String?,
    val transitLine: TransitLine?,
    val stopCount: Int,
    val tripShortText: String?
) : Parcelable

@Parcelize
data class TransitLine(
    val name: String?,
    val uri: String?,
    val color: String?,
    val iconUri: String?,
    val nameShort: String?,
    val textColor: String?,
) : Parcelable


@Parcelize
data class TransitStopDetails(
   val arrivalStop: TransitStop
) : Parcelable

@Parcelize
data class TransitStop(
    val name: String,
    val location: Locations
) : Parcelable

@Parcelize
data class NavigationInstruction(
    val maneuver: String,
    val instructions: String
) : Parcelable

@Parcelize
data class Polyline(
    val encodedPolyline: String
) : Parcelable

@Parcelize
data class TravelAdvisory(
    val speedReadingIntervals: List<SpeedReadingInterval> = emptyList()
) : Parcelable

@Parcelize
data class SpeedReadingInterval(
    val startPolylinePointIndex: Int,
    val endPolylinePointIndex: Int,
    val speed: String
) : Parcelable

@Parcelize
data class Locations(
    val latLng: LatLng,
    val heading: Int
) : Parcelable

@Parcelize
data class LatLng(
    val latitude: Number,
    val longitude: Number
) : Parcelable
