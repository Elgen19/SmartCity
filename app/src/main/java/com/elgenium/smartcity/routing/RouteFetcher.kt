package com.elgenium.smartcity.routing

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.elgenium.smartcity.network_reponses.RoutesResponse
import com.elgenium.smartcity.network_reponses.TravelAdvisory
import com.elgenium.smartcity.routes_network_request.ExtraComputation
import com.elgenium.smartcity.routes_network_request.RoutesRequest
import com.elgenium.smartcity.routes_network_request.Waypoint
import com.elgenium.smartcity.singletons.RetrofitClientRoutes
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.concurrent.TimeUnit

class RouteFetcher(
    context: Context,
    private val travelMode: String,
    private val latLngList: List<String>,
) {

    private val TAG = javaClass.simpleName  // Use the class name as the tag for logging
    private lateinit var routesResponse: RoutesResponse
    private lateinit var travelAdvisory: TravelAdvisory
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    fun fetchRoute(onRouteReady: () -> Unit) {
        if (latLngList.size < 2) {
            Log.e(TAG, "Insufficient data to fetch route.")
            return
        }

        val origin = createLocationFromLatLng(latLngList[0])
        val destination = createLocationFromLatLng(latLngList.last())
        val isStopOver = travelMode == "DRIVE" || travelMode == "TWO_WHEELER"

        val intermediates = createWaypoints(latLngList.subList(1, latLngList.size - 1), isStopOver)

        val routingPreference = if (travelMode == "DRIVE" || travelMode == "TWO_WHEELER") "TRAFFIC_AWARE" else null
        val extraComputations = if (travelMode == "DRIVE" || travelMode == "TWO_WHEELER") {
            listOf(ExtraComputation.TRAFFIC_ON_POLYLINE, ExtraComputation.HTML_FORMATTED_NAVIGATION_INSTRUCTIONS)
        } else null

        val request = RoutesRequest(
            origin = origin,
            destination = destination,
            intermediates = intermediates,
            travelMode = travelMode,
            routingPreference = routingPreference,
            computeAlternativeRoutes = true,
            extraComputations = extraComputations,
            optimizeWaypointOrder = false
        )

        Log.d(TAG, "Request body: $request")

        RetrofitClientRoutes.routesApi.getRoutes(request)
            .enqueue(object : Callback<RoutesResponse> {
                override fun onResponse(call: Call<RoutesResponse>, response: Response<RoutesResponse>) {
                    if (response.isSuccessful) {
                        routesResponse = response.body()!!
                        travelAdvisory = routesResponse.routes.firstOrNull()?.travelAdvisory!!
                        onRouteReady() // Invoke callback after data is ready
                    } else {
                        Log.e(TAG, "Error fetching route: ${response.errorBody()}")
                    }
                }

                override fun onFailure(call: Call<RoutesResponse>, t: Throwable) {
                    Log.e(TAG, "Failed to fetch route", t)
                }
            })
    }


    private fun createLocationFromLatLng(latLngString: String): com.elgenium.smartcity.routes_network_request.Location {
        val latLng = latLngString.split(",")
        val latitude = latLng[0].toDoubleOrNull() ?: 0.0
        val longitude = latLng[1].toDoubleOrNull() ?: 0.0
        return com.elgenium.smartcity.routes_network_request.Location(
            com.elgenium.smartcity.routes_network_request.LatLng(
                com.elgenium.smartcity.routes_network_request.Coordinates(latitude, longitude)
            )
        )
    }

    private fun createWaypoints(latLngList: List<String>, isStopOver: Boolean): List<Waypoint> {
        return latLngList.mapNotNull { latLngString ->
            val latLng = latLngString.split(",")
            if (latLng.size == 2) {
                val latitude = latLng[0].toDoubleOrNull()
                val longitude = latLng[1].toDoubleOrNull()
                if (latitude != null && longitude != null) {
                    Waypoint(
                        location = com.elgenium.smartcity.routes_network_request.LatLng(
                            com.elgenium.smartcity.routes_network_request.Coordinates(latitude, longitude)
                        ),
                        vehicleStopover = isStopOver
                    )
                } else {
                    Log.e(TAG, "Invalid coordinates: $latLngString")
                    null
                }
            } else {
                Log.e(TAG, "Invalid latLng format: $latLngString")
                null
            }
        }
    }

    fun getTotalDuration(): String {
        val totalDurationSeconds = routesResponse.routes.firstOrNull()?.legs?.sumOf {
            val sanitizedDuration = it.duration.replace("[^0-9]".toRegex(), "") // Remove non-numeric characters
            sanitizedDuration.toIntOrNull() ?: 0 // Safely parse to Int or fallback to 0
        } ?: 0

        val hours = TimeUnit.SECONDS.toHours(totalDurationSeconds.toLong())
        val minutes = TimeUnit.SECONDS.toMinutes(totalDurationSeconds.toLong()) % 60

        val duration = when {
            hours > 0 && minutes > 0 -> "$hours hours $minutes minutes"
            hours > 0 -> "$hours hours"
            minutes > 0 -> "$minutes minutes"
            else -> "0 minutes" // Fallback for edge case with no time
        }

        Log.d(TAG, "Total Duration in seconds: $totalDurationSeconds")
        return duration
    }


    // Method to get the total distance in a human-readable format
     fun getTotalDistance(): String {
        val totalDistanceMeters = routesResponse.routes.firstOrNull()?.legs?.sumOf { it.distanceMeters } ?: 0
        val totalDistanceKilometers = totalDistanceMeters / 1000.0
        val distance = String.format("%.2f km", totalDistanceKilometers)

        Log.d(TAG, "Total Distance in meters: $totalDistanceMeters")
        return distance
    }

    // Method to get the custom route token
     fun getCustomRouteToken(): String {
        val routeToken = routesResponse.routes.firstOrNull()?.routeToken ?: "No route token available"

        Log.d(TAG, "Custom Route Token: $routeToken")
        return routeToken
    }

     fun determineOverallTrafficCondition(): String {
        if (travelAdvisory == null) {
            return "No Traffic Data"
        }

        var normalCount = 0
        var slowCount = 0
        var trafficJamCount = 0

        travelAdvisory.speedReadingIntervals.forEach { interval ->
            when (interval.speed) {
                "NORMAL" -> normalCount++
                "SLOW" -> slowCount++
                "TRAFFIC_JAM" -> trafficJamCount++
            }
        }

        return when {
            trafficJamCount > slowCount && trafficJamCount > normalCount -> "Heavy Traffic"
            slowCount > normalCount -> "Moderate Traffic"
            else -> "Light Traffic"
        }
    }

    fun getCurrentLocation(context: Context, callback: (LatLng?) -> Unit) {
        // Check if the location permission is granted
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // Permission is granted, get the location
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    location?.let {
                        // Convert Location to LatLng
                        val latLng = LatLng(it.latitude, it.longitude)
                        callback(latLng) // Return the location through the callback
                    } ?: run {
                        Log.e("MealPlaceRecommendationManager", "Location is null.")
                        callback(null) // Handle case where location is null
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("MealPlaceRecommendationManager", "Failed to get location", exception)
                    callback(null) // Return null on failure
                }
        } else {
            // Permission is not granted, request it
            ActivityCompat.requestPermissions(
                context as Activity, // Ensure that context is an Activity
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1
            )
        }
    }

}
