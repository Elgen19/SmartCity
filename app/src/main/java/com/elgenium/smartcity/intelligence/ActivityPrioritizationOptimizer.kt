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
        const val TAG = "ActivityPrioritizationOptimizer"
    }

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)


    fun prioritizeActivities(
        activityList: List<ActivityDetails>,
        onCompletion: (List<ActivityDetails>) -> Unit
    ) {
        Log.d(TAG, "Starting prioritization process for ${activityList.size} activities")

        // Get the current time internally
        val currentTime = System.currentTimeMillis()

        // Calculate proximity scores
        calculateProximityScores(activityList, context) { proximityScores ->
            // Transform List<Pair<ActivityDetails, Double>> to Map<String, Double>
            val proximityScoresMap = proximityScores.associate { (activity, score) ->
                activity.activityName to score
            }

            // Prioritize activities based on dynamic weights, scores, and travel time
            val prioritizedList = activityList.sortedByDescending { activity ->
                // Extract the travel time for each activity (in minutes)
                val travelTimeMinutes = proximityScoresMap[activity.activityName] ?: 0.0

                // Calculate activity score including travel time
                calculateActivityScore(activity, proximityScoresMap, currentTime, travelTimeMinutes)
            }

            // Return the prioritized list via the callback
            onCompletion(prioritizedList)
        }
    }



    private fun calculateDynamicWeights(activity: ActivityDetails, currentTime: Long): Triple<Double, Double, Double> {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val activityTime = try {
            activity.startTime?.let { formatter.parse(it)?.time } ?: Long.MAX_VALUE
        } catch (e: Exception) {
            Long.MAX_VALUE
        }

        val timeDifference = (activityTime - currentTime) / 1000 // Time difference in seconds

        val timeWeight = when {
            timeDifference < 300 -> 90.0 // Critical weight for activities starting in less than 5 minutes
            timeDifference < 900 -> 70.0 // High weight if the activity starts within 15 minutes
            timeDifference < 3600 -> 50.0 // Moderate weight if it starts within an hour
            else -> 20.0 // Lower weight for activities far in the future
        }


        val proximityWeight = 20.0
        val priorityWeight = 30.0

        return Triple(timeWeight, proximityWeight, priorityWeight)
    }


    private fun calculateActivityScore(
        activity: ActivityDetails,
        proximityScores: Map<String, Double>,
        currentTime: Long,
        travelTimeMinutes: Double // Travel time in minutes, passed from proximityScores
    ): Double {
        val (timeWeight, proximityWeight, priorityWeight) = calculateDynamicWeights(activity, currentTime)

        val timeScore = calculateTimeScore(activity.startTime, currentTime)
        val proximityScore = proximityScores[activity.activityName]?.coerceIn(0.0, 1.0) ?: 0.0
        val priorityScore = calculatePriorityScore(activity.priorityLevel).coerceIn(0.0, 1.0)

        // Adjust feasibility check based on priority
        val feasibilityPenalty = when (activity.priorityLevel) {
            "High" -> {
                val isFeasible = isActivityFeasibleStrict(activity, proximityScore, currentTime, travelTimeMinutes)
                if (isFeasible) 1.0 else 0.0 // Penalize non-feasible high priority activities
            }
            "Medium" -> {
                val isFeasible = isActivityFeasibleFlexible(activity, proximityScore, currentTime, travelTimeMinutes)
                if (isFeasible) 1.0 else 0.5 // Penalize medium priority activities less
            }
            "Low" -> {
                1.0 // Low priority activities have no feasibility penalty
            }
            else -> 1.0 // Default to no penalty if the priority level is unknown
        }

        // Calculate the total score considering all factors
        val totalScore = ((timeWeight * timeScore).coerceAtMost(100.0)) +
                (proximityWeight * proximityScore) +
                ((priorityWeight * priorityScore).coerceAtMost(100.0)) * feasibilityPenalty

        Log.d(
            TAG,
            "Activity: ${activity.activityName}, Time Score: $timeScore, Priority Score: $priorityScore, " +
                    "Proximity Score: $proximityScore, Feasibility Penalty: $feasibilityPenalty, Total Score: $totalScore"
        )


        return totalScore
    }

    private fun isActivityFeasibleStrict(
        activity: ActivityDetails,
        proximityScore: Double,
        currentTime: Long,
        travelTimeMinutes: Double // Pass the calculated travel time in minutes
    ): Boolean {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val activityStartTime = try {
            activity.startTime?.let { formatter.parse(it)?.time } ?: return false
        } catch (e: Exception) {
            return false
        }

        val travelTimeMillis = (travelTimeMinutes * 60 * 1000).toLong()
        val bufferTimeMillis = 2 * 60 * 1000 // 2-minute buffer
        return currentTime + travelTimeMillis + bufferTimeMillis <= activityStartTime

    }

    private fun isActivityFeasibleFlexible(
        activity: ActivityDetails,
        proximityScore: Double,
        currentTime: Long,
        travelTimeMinutes: Double // Pass the calculated travel time in minutes
    ): Boolean {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val activityStartTime = try {
            activity.startTime?.let { formatter.parse(it)?.time } ?: return false
        } catch (e: Exception) {
            return false
        }

        // Estimate travel time in milliseconds
        val travelTimeMillis = (travelTimeMinutes * 60 * 1000).toLong()

        // Medium priority activities should reach before the end time
        val activityEndTime = activityStartTime + (travelTimeMinutes * 60 * 1000).toLong()
        return currentTime + travelTimeMillis <= activityEndTime
    }

//    private fun calculateProximityScores(
//        activityList: List<ActivityDetails>,
//        context: Context,
//        onScoresCalculated: (Map<String, Double>) -> Unit
//    ) {
//        getCurrentLocation(context) { currentLocation ->
//            if (currentLocation != null) {
//                val travelMode = "DRIVE"
//                val waypointOrigins = mutableListOf<RouteMatrixOrigin>()
//                val waypointDestinations = mutableListOf<RouteMatrixDestination>()
//
//                // Log: Current location as origin
//                Log.d(TAG, "Current location: Lat: ${currentLocation.latitude}, Lng: ${currentLocation.longitude}")
//
//                activityList.forEach { activity ->
//                    Log.d(TAG, "Preparing destination for activity: ${activity.activityName}, Place: ${activity.placeName}")
//                }
//
//                // Current location as origin
//                val currentWaypoint = WaypointMatrix(
//                    location = LocationMatrix(latLng = LatLngMatrix(currentLocation.latitude, currentLocation.longitude))
//                )
//                waypointOrigins.add(RouteMatrixOrigin(waypoint = currentWaypoint))
//
//                // Prepare destinations for all activities
//                activityList.forEach { activity ->
//                    Log.d(TAG, "Adding destination for activity: ${activity.activityName}, Place: ${activity.placeName}")
//                    val destinationWaypoint = WaypointMatrix(placeId = activity.placeId)
//                    waypointDestinations.add(RouteMatrixDestination(waypoint = destinationWaypoint))
//                }
//
//                // Create the RouteMatrixRequest
//                val routeMatrixRequest = RouteMatrixRequest(
//                    origins = waypointOrigins,
//                    destinations = waypointDestinations,
//                    travelMode = travelMode,
//                    routingPreference = "TRAFFIC_AWARE"
//                )
//
//                CoroutineScope(Dispatchers.IO).launch {
//                    try {
//                        val apiKey = BuildConfig.MAPS_API_KEY
//                        Log.d(TAG, "Making RouteMatrix API call with API key.")
//                        val routesMatrixResponse = RoutesMatrixClientSingleton.instance.computeRouteMatrix(apiKey, request = routeMatrixRequest)
//
//                        val proximityScores = mutableMapOf<String, Double>()
//                        Log.d(TAG, "API response received. Calculating proximity scores.")
//
//                        // Map activityList to destinationIndex to ensure correct mapping
//                        val destinationIndexToActivityMap = activityList.mapIndexed { index, activity ->
//                            index to activity
//                        }.toMap()
//
//                        routesMatrixResponse.forEach { routeMatrixElement ->
//                            val destinationIndex = routeMatrixElement.destinationIndex
//                            val activity = destinationIndexToActivityMap[destinationIndex] ?: return@forEach
//
//                            val distanceMeters = routeMatrixElement.distanceMeters.toDouble()
//
//                            // Extract duration string and convert to minutes
//                            val durationString = routeMatrixElement.duration
//                            val durationInSeconds = durationString.replace("s", "").toLong()
//                            val travelTimeMinutes = durationInSeconds / 60.0
//
//                            // Log the travel time for debugging
//                            Log.d(TAG, "Activity: ${activity.activityName}, Distance: $distanceMeters meters, Travel Time: $travelTimeMinutes minutes")
//
//                            // Calculate proximity score (using distance)
//                            val proximityScore = 1.0 / distanceMeters.coerceAtLeast(1.0)
//
//                            proximityScores[activity.activityName] = proximityScore
//                        }
//
//                        // Return scores on the main thread
//                        withContext(Dispatchers.Main) {
//                            onScoresCalculated(proximityScores)
//                            Log.d(TAG, "Proximity scores returned to callback.")
//                        }
//                    } catch (e: Exception) {
//                        Log.e(TAG, "Error calculating proximity scores: ${e.message}", e)
//                    }
//                }
//            } else {
//                Log.e(TAG, "Unable to retrieve current location.")
//                onScoresCalculated(emptyMap())
//            }
//        }
//    }

    private fun calculateProximityScores(
        activityList: List<ActivityDetails>,
        context: Context,
        onScoresCalculated: (List<Pair<ActivityDetails, Double>>) -> Unit
    ) {
        getCurrentLocation(context) { currentLocation ->
            if (currentLocation != null) {
                val travelMode = "DRIVE"
                val remainingActivities = activityList.toMutableList()
                val orderedActivities = mutableListOf<Pair<ActivityDetails, Double>>()

                var currentOrigin = WaypointMatrix(
                    location = LocationMatrix(latLng = LatLngMatrix(currentLocation.latitude, currentLocation.longitude))
                )

                Log.d(TAG, "Current location retrieved: Lat=${currentLocation.latitude}, Lng=${currentLocation.longitude}")
                Log.d(TAG, "Starting proximity score calculation for ${activityList.size} activities.")

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val apiKey = BuildConfig.MAPS_API_KEY
                        while (remainingActivities.isNotEmpty()) {
                            Log.d(TAG, "Remaining activities to evaluate: ${remainingActivities.size}")

                            // Prepare destinations for the remaining activities
                            val waypointDestinations = remainingActivities.map { activity ->
                                Log.d(TAG, "Adding activity '${activity.activityName}' ${activity.placeLatlng} as a destination.")
                                RouteMatrixDestination(
                                    waypoint = WaypointMatrix(placeId = activity.placeId)
                                )
                            }

                            // Create RouteMatrixRequest for current origin to all remaining destinations
                            val routeMatrixRequest = RouteMatrixRequest(
                                origins = listOf(RouteMatrixOrigin(waypoint = currentOrigin)),
                                destinations = waypointDestinations,
                                travelMode = travelMode,
                                routingPreference = "TRAFFIC_AWARE"
                            )

                            Log.d(TAG, "Sending RouteMatrixRequest from origin to ${waypointDestinations.size} destinations.")

                            // Make the API call
                            val routesMatrixResponse = RoutesMatrixClientSingleton.instance.computeRouteMatrix(apiKey, request = routeMatrixRequest)

                            Log.d(TAG, "RouteMatrixResponse received with ${routesMatrixResponse.size} elements.")

                            // Map activities to distances and calculate proximity scores
                            val activityDistanceMap = mutableMapOf<ActivityDetails, Double>()
                            routesMatrixResponse.forEachIndexed { index, routeMatrixElement ->
                                val rawDistance = routeMatrixElement.distanceMeters.toString() // Convert to string for parsing
                                val distanceMeters = rawDistance
                                    .replace("m", "") // Remove the "m" suffix
                                    .replace(",", "") // Handle any unexpected commas
                                    .toDoubleOrNull() // Convert to a Double

                                if (distanceMeters == null || distanceMeters < 0) {
                                    Log.e(TAG, "Invalid distance for activity '${remainingActivities[index].activityName}': $rawDistance")
                                    return@forEachIndexed // Skip this activity if the distance is invalid
                                }

                                val activity = remainingActivities[routeMatrixElement.destinationIndex] // Ensure the activity is correctly matched to the distance
                                activityDistanceMap[activity] = distanceMeters

                                Log.d(
                                    TAG,
                                    "Activity '${activity.activityName}': Raw Distance='$rawDistance', Parsed Distance=${distanceMeters}m"
                                )
                            }

                            // Calculate proximity score (inverse of distance, for example)
                            val orderedList = activityDistanceMap.entries.sortedBy { it.value }
                            orderedActivities.addAll(orderedList.map { activity ->
                                val distanceMeters = activity.value
                                // Example of a simple inverse score: closer activities get a higher score
                                val proximityScore = if (distanceMeters > 0) 1 / distanceMeters else 0.0
                                Pair(activity.key, proximityScore)  // Return the activity with the calculated score
                            })

                            // Log the ordered activities after each iteration
                            Log.d(TAG, "Ordered activities so far: ${orderedActivities.map { it.first.activityName }}")

                            // Remove the closest activity from remaining activities
                            val closestActivity = orderedList.first().key
                            remainingActivities.remove(closestActivity)

                            // Log remaining activities after removal
                            Log.d(TAG, "Remaining activities after removal: ${remainingActivities.map { it.activityName }}")

                            // Update the origin for the next iteration
                            currentOrigin = WaypointMatrix(placeId = closestActivity.placeId)
                        }

                        // Return the ordered list with scores on the main thread
                        withContext(Dispatchers.Main) {
                            Log.e(TAG, "Ordered list: $orderedActivities")
                            onScoresCalculated(orderedActivities)  // Return the list of activities with proximity scores
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error calculating proximity scores: ${e.message}", e)
                    }
                }
            } else {
                Log.e(TAG, "Unable to retrieve current location.")
                onScoresCalculated(emptyList())
            }
        }
    }










    /**
     * Calculate the time score (higher score for activities closer to the current time).
     */
    private fun calculateTimeScore(startTime: String?, currentTime: Long): Double {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val activityTime = try {
            formatter.parse(startTime)?.time ?: return 0.0
        } catch (e: Exception) {
            return 0.0
        }

        val timeDifferenceMinutes = (activityTime - currentTime) / (1000 * 60.0)
        return when {
            timeDifferenceMinutes < 0 -> 0.0 // Already past
            timeDifferenceMinutes < 15 -> 1.0 // Immediate urgency
            timeDifferenceMinutes < 60 -> 1.0 - (timeDifferenceMinutes / 60.0) // Proportional drop
            else -> 0.0
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

