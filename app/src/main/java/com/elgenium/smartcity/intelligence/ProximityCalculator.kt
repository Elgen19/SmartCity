package com.elgenium.smartcity.intelligence

import android.app.Dialog
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import com.elgenium.smartcity.BuildConfig
import com.elgenium.smartcity.contextuals.ActivityPlaceRecommendation
import com.elgenium.smartcity.databinding.DialogActivitySuggestionsBinding
import com.elgenium.smartcity.models.ActivityDetails
import com.elgenium.smartcity.routes_network_request.LatLngMatrix
import com.elgenium.smartcity.routes_network_request.LocationMatrix
import com.elgenium.smartcity.routes_network_request.RouteMatrixDestination
import com.elgenium.smartcity.routes_network_request.RouteMatrixOrigin
import com.elgenium.smartcity.routes_network_request.RouteMatrixRequest
import com.elgenium.smartcity.routes_network_request.WaypointMatrix
import com.elgenium.smartcity.singletons.RoutesMatrixClientSingleton
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.net.PlacesClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProximityCalculator(
    private val context: Context,
    private val placesClient: PlacesClient
) {

    private val placeRecommendation = ActivityPlaceRecommendation(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()) // Lifecycle-aware scope

    fun prioritizeAndRecalculate(activityList: MutableList<ActivityDetails>) {
        val tag = this::class.java.simpleName // Get the class name dynamically

        // Ensure that there are at least two activities before proceeding with the comparison
        if (activityList.size < 2) {
            Log.d(tag, "Not enough activities for comparison. Skipping distance comparison.")
            return
        }

        activityList.forEachIndexed { index, activity ->
            val originLatLng = parseLatLng(activity.placeLatlng)

            Log.d(tag, "Processing activity: ${activity.activityName} at $originLatLng")

            // Convert the string like "[bakery]" back into a list of strings
            val placeTypesList = activity.placeTypes
                .removeSurrounding("[", "]") // Remove the square brackets
                .split(",") // Split by commas if there are multiple values
                .map { it.trim() } // Trim any whitespace around the items

            // Log the parsed place types
            Log.d(tag, "Place types for ${activity.activityName}: $placeTypesList")

            // Perform place recommendations
            placeRecommendation.performTextSearchWithActivityOrigin(
                placesClient = placesClient,
                originLatLng = originLatLng,
                placeTypes = placeTypesList
            ) { recommendations ->
                if (recommendations.isEmpty()) {
                    Log.d(tag, "No recommendations found for ${activity.activityName}")
                } else {
                    Log.d(tag, "Found ${recommendations.size} recommendations for ${activity.activityName}")
                }

                // Find the closest recommendation
                recommendations.minByOrNull { it.distance }?.let { recommendedPlace ->
                    Log.d(tag, "Recommended place for ${activity.activityName}: ${recommendedPlace.name} at ${recommendedPlace.placeLatlng}, Distance: ${recommendedPlace.distance} meters")

                    // If it's not the first activity, compare with the previous activity (Activity A)
                    if (index > 0) {
                        val previousActivity = activityList[index - 1]  // This is Activity A
                        val previousActivityLatLng = parseLatLng(previousActivity.placeLatlng)

                        Log.d(tag, "Comparing with previous activity: ${previousActivity.activityName} at $previousActivityLatLng")

                        // Compare distances for Activity A and the recommended place for Activity B
                        compareDistances(previousActivity, activity, parseFormattedLatLng(recommendedPlace.placeLatlng), previousActivityLatLng, context)
                    }
                } ?: Log.d(tag, "No recommended places found for ${activity.activityName}")
            }
        }
    }





    private fun compareDistances(
        activityA: ActivityDetails,  // Current Activity (Activity A)
        activityB: ActivityDetails,  // Next Activity (Activity B)
        recommendedLatLng: LatLng,   // Nearest recommended place (e.g., bakery)
        originLatLng: LatLng,        // Location of Activity A
        context: Context
    ) {
        val travelMode = "DRIVE"  // Travel mode to calculate the route (can also be WALK if preferred)

        // Prepare Route Matrix request to calculate distance from Activity A to nearest recommended place (e.g., bakery)
        val waypointOrigins = mutableListOf<RouteMatrixOrigin>()
        val waypointDestinations = mutableListOf<RouteMatrixDestination>()

        // Origin: Activity A's location
        val currentWaypoint = WaypointMatrix(
            location = LocationMatrix(
                latLng = LatLngMatrix(
                    originLatLng.latitude,
                    originLatLng.longitude
                )
            )
        )
        waypointOrigins.add(RouteMatrixOrigin(waypoint = currentWaypoint))

        // Destination: Nearest recommended place (e.g., bakery)
        val destinationWaypoint = WaypointMatrix(
            location = LocationMatrix(
                latLng = LatLngMatrix(
                    recommendedLatLng.latitude,
                    recommendedLatLng.longitude
                )
            )
        )
        waypointDestinations.add(RouteMatrixDestination(waypoint = destinationWaypoint))

        // Prepare Route Matrix request for the first distance (Activity A to recommended place)
        val routeMatrixRequest = RouteMatrixRequest(
            origins = waypointOrigins,
            destinations = waypointDestinations,
            travelMode = travelMode,
            routingPreference = "TRAFFIC_AWARE"
        )

        // Perform Route Matrix API call to get distance from Activity A to the recommended place (bakery)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val apiKey = BuildConfig.MAPS_API_KEY
                Log.d("ProximityCalculator", "Making RouteMatrix API call to get distance from Activity A to nearest bakery.")
                val routesMatrixResponse = RoutesMatrixClientSingleton.instance.computeRouteMatrix(
                    apiKey,
                    request = routeMatrixRequest
                )

                val distanceFromAtoRecommendedPlace = routesMatrixResponse.firstOrNull()?.distanceMeters

                if (distanceFromAtoRecommendedPlace != null) {
                    Log.d("ProximityCalculator", "Distance from Activity A to recommended place (bakery): $distanceFromAtoRecommendedPlace meters")

                    // Now calculate distance from Activity B's selected place to Activity A
                    val selectedPlaceLatLngB = parseLatLng(activityB.placeLatlng)

                    // Prepare Route Matrix request for the second distance (Activity B's place to Activity A)
                    waypointOrigins.clear()
                    waypointDestinations.clear()

                    // Origin: Activity B's selected place
                    val currentWaypointB = WaypointMatrix(
                        location = LocationMatrix(
                            latLng = LatLngMatrix(
                                selectedPlaceLatLngB.latitude,
                                selectedPlaceLatLngB.longitude
                            )
                        )
                    )
                    waypointOrigins.add(RouteMatrixOrigin(waypoint = currentWaypointB))

                    // Destination: Activity A's location
                    val destinationWaypointA = WaypointMatrix(
                        location = LocationMatrix(
                            latLng = LatLngMatrix(
                                originLatLng.latitude,
                                originLatLng.longitude
                            )
                        )
                    )
                    waypointDestinations.add(RouteMatrixDestination(waypoint = destinationWaypointA))

                    // Prepare Route Matrix request for the second distance (Activity B to Activity A)
                    val routeMatrixRequestB = RouteMatrixRequest(
                        origins = waypointOrigins,
                        destinations = waypointDestinations,
                        travelMode = travelMode,
                        routingPreference = "TRAFFIC_AWARE"
                    )

                    // Perform Route Matrix API call to get distance from Activity B's place to Activity A
                    val routesMatrixResponseB = RoutesMatrixClientSingleton.instance.computeRouteMatrix(
                        apiKey,
                        request = routeMatrixRequestB
                    )

                    val distanceFromBtoA = routesMatrixResponseB.firstOrNull()?.distanceMeters

                    if (distanceFromBtoA != null) {
                        Log.d("ProximityCalculator", "Distance from Activity B's place to Activity A: $distanceFromBtoA meters")

                        // Compare both distances
                        if (distanceFromBtoA > distanceFromAtoRecommendedPlace) {
                            Log.d("ProximityCalculator", "Activity B's place is farther from Activity A than the recommended bakery.")

                            // Fetch address for the recommended nearest place (e.g., bakery)
                            val placeAddress = fetchAddress(recommendedLatLng) ?: "Address not found"

                            // Show the dialog with the recommendation
                            withContext(Dispatchers.Main) {
                                showDistanceWarningDialog(
                                    context,
                                    activityA,
                                    activityB,
                                    recommendedLatLng,
                                    distanceFromAtoRecommendedPlace,
                                    placeAddress
                                )
                            }
                        } else {
                            Log.d("ProximityCalculator", "Activity B's place is closer or equal to the recommended bakery.")
                            // Update activity if necessary
                        }
                    } else {
                        Log.e("ProximityCalculator", "Distance value from Activity B's place to Activity A is null or missing.")
                    }
                } else {
                    Log.e("ProximityCalculator", "Distance value from Activity A to recommended place is null or missing.")
                }
            } catch (e: Exception) {
                Log.e("ProximityCalculator", "Error calculating proximity scores: ${e.message}", e)
            }
        }
    }



    private fun showDistanceWarningDialog(
        context: Context,
        activityA: ActivityDetails,
        activityB: ActivityDetails,
        recommendedLatLng: LatLng,
        distance: Int,
        placeAddress: String
    ) {
        val binding = DialogActivitySuggestionsBinding.inflate(LayoutInflater.from(context))
        val dialog = Dialog(context)
        dialog.setContentView(binding.root)

        binding.dialogTitle.text = "Time Conflict"
        binding.dialogMessage.text = "The distance from ${activityA.activityName} to ${activityB.activityName} is greater than the nearest bakery."
        binding.dialogMessage.append("\nRecommended Bakery: $recommendedLatLng")
        binding.dialogMessage.append("\nDistance: $distance meters")
        binding.dialogMessage.append("\nAddress: $placeAddress")

        // Handle user choice (update place or retain the current one)
        binding.btnDismiss.setOnClickListener {
            dialog.dismiss()
            // User opts to keep the current place, no action needed
        }

        // Handle option to change to recommended place
        binding.btnAction.setOnClickListener {
            // Update Activity B with the recommended place
            activityB.placeLatlng = recommendedLatLng.toString()
            dialog.dismiss()
        }

        dialog.show()
    }



    private suspend fun fetchAddress(latLng: LatLng): String? {
        // Example implementation for fetching address (replace with actual API call if needed)
        return withContext(Dispatchers.IO) {
            try {
                // Simulate fetching address from API (use actual implementation)
                "Sample Address for $latLng"
            } catch (e: Exception) {
                Log.e("ProximityCalculator", "Failed to fetch address: ${e.message}")
                null
            }
        }
    }

    private fun parseLatLng(latLngString: String): LatLng {
        val parts = latLngString.split(",")
        return LatLng(parts[0].toDouble(), parts[1].toDouble())
    }

    private fun parseFormattedLatLng(latLngString: String): LatLng {
        // Remove "lat/lng: (" and ")" to extract coordinates
        val cleanedString = latLngString
            .removePrefix("lat/lng: (")
            .removeSuffix(")")
        val parts = cleanedString.split(",")
        return LatLng(parts[0].toDouble(), parts[1].toDouble())
    }


    fun clear() {
        scope.cancel() // Cancel all coroutines on cleanup
    }
}
