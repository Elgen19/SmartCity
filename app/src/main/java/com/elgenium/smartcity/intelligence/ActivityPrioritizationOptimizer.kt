package com.elgenium.smartcity.intelligence

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.elgenium.smartcity.BuildConfig
import com.elgenium.smartcity.models.ActivityDetails
import com.elgenium.smartcity.routes_network_request.LatLngMatrix
import com.elgenium.smartcity.routes_network_request.LocationMatrix
import com.elgenium.smartcity.routes_network_request.RouteMatrixDestination
import com.elgenium.smartcity.routes_network_request.RouteMatrixOrigin
import com.elgenium.smartcity.routes_network_request.RouteMatrixRequest
import com.elgenium.smartcity.routes_network_request.WaypointMatrix
import com.elgenium.smartcity.singletons.RoutesMatrixClientSingleton
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class ActivityPrioritizationOptimizer(activityContext: Context) {
    private val context = activityContext
    companion object {
        private const val TAG = "ActivityPrioritizationOptimizer"
        // Weights for the prioritization
        private const val TIME_WEIGHT = 50
        private const val PROXIMITY_WEIGHT = 30
        private const val PRIORITY_WEIGHT = 20
    }

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    /**
     * Prioritize activities based on dynamic factors such as time, proximity, and priority level.
     */
    fun prioritizeActivities(
        activityList: List<ActivityDetails>,
        currentTime: Long,
        onCompletion: (List<ActivityDetails>) -> Unit
    ) {
        Log.d(TAG, "Starting prioritization process for ${activityList.size} activities")

        // Calculate proximity scores (current location is handled internally)
        calculateProximityScores(activityList, context) { proximityScores ->
            // Once proximity scores are available, prioritize activities
            val prioritizedList = activityList.sortedByDescending { activity ->
                calculateActivityScore(activity, proximityScores, currentTime)
            }

            Log.d(TAG, "Prioritization complete. Returning prioritized list of activities.")
            onCompletion(prioritizedList)
        }
    }


    /**
     * Calculate the prioritization score for an activity.
     */
    private fun calculateActivityScore(
        activity: ActivityDetails,
        proximityScores: Map<String, Double>,
        currentTime: Long
    ): Double {
        Log.d(TAG, "Calculating score for activity: ${activity.activityName}")

        val timeScore = calculateTimeScore(activity.startTime, currentTime)
        val proximityScore = proximityScores[activity.activityName] ?: 0.0
        val priorityScore = calculatePriorityScore(activity.priorityLevel)

        val totalScore = (TIME_WEIGHT * timeScore) +
                (PROXIMITY_WEIGHT * proximityScore) +
                (PRIORITY_WEIGHT * priorityScore)

        Log.d(
            TAG, "Scores for ${activity.activityName} -> Time: $timeScore, " +
                    "Proximity: $proximityScore, Priority: $priorityScore, Total: $totalScore"
        )
        return totalScore
    }

    /**
     * Calculate the time score (higher score for activities closer to the current time).
     */
    private fun calculateTimeScore(startTime: String?, currentTime: Long): Double {
        if (startTime.isNullOrEmpty()) {
            Log.d(TAG, "No start time available. Time score = 0.0")
            return 0.0
        }

        return try {
            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val activityTime = formatter.parse(startTime)?.time ?: return 0.0

            val difference = (activityTime - currentTime).coerceAtLeast(1)
            val timeScore = 1.0 / difference

            Log.d(TAG, "Calculated time score: $timeScore")
            timeScore
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing start time: $startTime", e)
            0.0
        }
    }

    /**
     * Calculate the priority score based on the activity's priority level.
     */
    private fun calculatePriorityScore(priorityLevel: String?): Double {
        val priorityScore = when (priorityLevel?.lowercase(Locale.getDefault())) {
            "high" -> 3.0
            "medium" -> 2.0
            "low" -> 1.0
            else -> 0.0
        }

        Log.d(TAG, "Calculated priority score: $priorityScore for priority level: $priorityLevel")
        return priorityScore
    }



    private fun calculateProximityScores(
        activityList: List<ActivityDetails>,
        context: Context,
        onScoresCalculated: (Map<String, Double>) -> Unit
    ) {
        // First, get the current location asynchronously
        getCurrentLocation(context) { currentLocation ->
            if (currentLocation != null) {
                val travelMode = "DRIVE"
                val waypointOrigins = mutableListOf<RouteMatrixOrigin>()
                val waypointDestinations = mutableListOf<RouteMatrixDestination>()

                // Log: Current location as origin
                Log.d(TAG, "Current location: Lat: ${currentLocation.latitude}, Lng: ${currentLocation.longitude}")

                // Log each activity and its associated destination (place)
                activityList.forEach { activity ->
                    Log.d(TAG, "Preparing destination for activity: ${activity.activityName}, Place: ${activity.placeName}")
                }

                // Current location as origin
                val currentWaypoint = WaypointMatrix(
                    location = LocationMatrix(latLng = LatLngMatrix(currentLocation.latitude, currentLocation.longitude))
                )
                waypointOrigins.add(RouteMatrixOrigin(waypoint = currentWaypoint))

                // Prepare destinations for all activities
                activityList.forEach { activity ->
                    Log.d(TAG, "Adding destination for activity: ${activity.activityName}, Place: ${activity.placeName}")
                    val destinationWaypoint = WaypointMatrix(placeId = activity.placeId)
                    waypointDestinations.add(RouteMatrixDestination(waypoint = destinationWaypoint))
                }

                // Create the RouteMatrixRequest
                val routeMatrixRequest = RouteMatrixRequest(
                    origins = waypointOrigins,
                    destinations = waypointDestinations,
                    travelMode = travelMode,
                    routingPreference = "TRAFFIC_AWARE"
                )

                // Log: Request creation for RouteMatrix API
                Log.d(TAG, "RouteMatrixRequest created with ${waypointOrigins.size} origins and ${waypointDestinations.size} destinations.")

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val apiKey = BuildConfig.MAPS_API_KEY
                        // Log: Making API request
                        Log.d(TAG, "Making RouteMatrix API call with API key.")

                        val routesMatrixResponse = RoutesMatrixClientSingleton.instance.computeRouteMatrix(apiKey, request = routeMatrixRequest)

                        // Map to hold activity name -> proximity score
                        val proximityScores = mutableMapOf<String, Double>()

                        // Log: API response received
                        Log.d(TAG, "API response received. Calculating proximity scores.")

                        // Map activityList to destinationIndex to ensure correct mapping
                        val destinationIndexToActivityMap = activityList.mapIndexed { index, activity ->
                            index to activity
                        }.toMap()

            // Calculate proximity scores using destinationIndex
                        routesMatrixResponse.forEach { routeMatrixElement ->
                            val destinationIndex = routeMatrixElement.destinationIndex
                            val activity = destinationIndexToActivityMap[destinationIndex] ?: return@forEach

                            val distanceMeters = routeMatrixElement.distanceMeters.toDouble()
                            val proximityScore = 1.0 / distanceMeters.coerceAtLeast(1.0) // Avoid division by zero

                            // Assign the score to the correct activity
                            proximityScores[activity.activityName] = proximityScore

                            // Log the details for debugging
                            Log.d(TAG, "Activity: ${activity.activityName}, Distance: $distanceMeters meters, Score: $proximityScore")
                        }

                        // Log final proximity scores for debugging
                        Log.d(TAG, "Proximity scores: $proximityScores")


                        // Return scores on the main thread
                        withContext(Dispatchers.Main) {
                            onScoresCalculated(proximityScores)
                            Log.d(TAG, "Proximity scores returned to callback.")
                        }
                    } catch (e: Exception) {
                        // Log: Error during the API request
                        Log.e(TAG, "Error calculating proximity scores: ${e.message}", e)
                    }
                }
            } else {
                Log.e(TAG, "Unable to retrieve current location.")
                onScoresCalculated(emptyMap()) // Return an empty map if location is null
            }
        }
    }




    private fun getCurrentLocation(context: Context, callback: (LatLng?) -> Unit) {
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
                        Log.e("WeatherBasedPlaceRecommendation", "Location: $latLng")

                        callback(latLng) // Return the location through the callback
                    } ?: run {
                        Log.e("WeatherBasedPlaceRecommendation", "Location is null.")
                        callback(null) // Handle case where location is null
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("WeatherBasedPlaceRecommendation", "Failed to get location", exception)
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

