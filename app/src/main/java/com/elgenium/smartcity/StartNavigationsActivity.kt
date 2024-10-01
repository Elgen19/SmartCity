package com.elgenium.smartcity

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.elgenium.smartcity.databinding.DialogTripRecapBinding
import com.google.android.gms.maps.GoogleMap.CameraPerspective
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.navigation.AlternateRoutesStrategy
import com.google.android.libraries.navigation.CustomRoutesOptions
import com.google.android.libraries.navigation.DisplayOptions
import com.google.android.libraries.navigation.ForceNightMode.FORCE_NIGHT
import com.google.android.libraries.navigation.ListenableResultFuture
import com.google.android.libraries.navigation.NavigationApi
import com.google.android.libraries.navigation.Navigator
import com.google.android.libraries.navigation.Navigator.ArrivalListener
import com.google.android.libraries.navigation.Navigator.RemainingTimeOrDistanceChangedListener
import com.google.android.libraries.navigation.Navigator.RouteChangedListener
import com.google.android.libraries.navigation.RoadSnappedLocationProvider
import com.google.android.libraries.navigation.RoutingOptions
import com.google.android.libraries.navigation.RoutingOptions.TravelMode
import com.google.android.libraries.navigation.SimulationOptions
import com.google.android.libraries.navigation.StylingOptions
import com.google.android.libraries.navigation.SupportNavigationFragment
import com.google.android.libraries.navigation.Waypoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class StartNavigationsActivity : AppCompatActivity() {

    private val TAG = "StartNavigationsActivity"
    private lateinit var navigator: Navigator
    private lateinit var navFragment: SupportNavigationFragment
    private var mArrivalListener: ArrivalListener? = null
    private var mRouteChangedListener: RouteChangedListener? = null
    private var mRemainingTimeOrDistanceChangedListener: RemainingTimeOrDistanceChangedListener? = null
    private var mLocationListener: RoadSnappedLocationProvider.LocationListener? = null
    private var mRoadSnappedLocationProvider: RoadSnappedLocationProvider? = null
    private var startTime: Long = 0
    private val PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1
    private var locationPermissionGranted = false
    private var routeToken: String? = null
    private lateinit var travelMode: String
    private lateinit var placeIds: ArrayList<String>
    private var isSimulated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start_navigations)

        travelMode = intent.getStringExtra("TRAVEL_MODE") ?: ""
        routeToken = intent.getStringExtra("ROUTE_TOKEN") ?: "NO ROUTE TOKEN"
        placeIds = intent.getStringArrayListExtra("PLACE_IDS") ?: ArrayList()
        isSimulated = intent.getBooleanExtra("IS_SIMULATED", false)

        Log.e("StartNavigationsActivity", "TRAVEL MODE AT NAVIGATION: $travelMode" )
        Log.e("StartNavigationsActivity", "ROUTE TOKEN AT NAVIGATION: $routeToken" )


        if (routeToken == null)
            routeToken = "NO ROUTE TOKEN"
        // Use the placeIds and routeToken for further navigation logic
        requestLocationPermissions(routeToken!!, placeIds, travelMode)
    }

    private fun initializeNavigationSdk(routeToken: String, placeIds: ArrayList<String>, travelMode: String) {
        // Request location permission.
        if (ContextCompat.checkSelfPermission(
                applicationContext, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED) {
            locationPermissionGranted = true
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION
            )
        }

        if (!locationPermissionGranted) {
            displayMessage("Error loading Navigation SDK: The user has not granted location permission.")
            return
        }

        // Get a navigator.
        NavigationApi.getNavigator(
            this,
            object : NavigationApi.NavigatorListener {
                override fun onNavigatorReady(navigator: Navigator) {
                    displayMessage("Navigator ready.")
                    this@StartNavigationsActivity.navigator = navigator
                    navFragment = supportFragmentManager.findFragmentById(R.id.navigation_fragment) as SupportNavigationFragment
                    navFragment.setForceNightMode(FORCE_NIGHT)
                    navFragment.setTripProgressBarEnabled(true)
                    navFragment.setSpeedometerEnabled(true)
                    navFragment.setSpeedLimitIconEnabled(true)
                    navFragment.setStylingOptions(StylingOptions()
                        .headerGuidanceRecommendedLaneColor(resources.getColor(R.color.brand_color))
                        .primaryDayModeThemeColor(resources.getColor(R.color.secondary_color)))

                    // Set the camera to follow the device location with 'TILTED' driving view.
                    navFragment.getMapAsync { googleMap ->
                        try {
                            if (ContextCompat.checkSelfPermission(
                                    applicationContext,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                googleMap.followMyLocation(CameraPerspective.TILTED)
                            } else {
                                displayMessage("Location permission is not granted. Unable to follow location.")
                            }
                        } catch (e: SecurityException) {
                            displayMessage("Error accessing location: ${e.message}")
                        }
                    }

                    navigateWithMultipleStops(routeToken, placeIds, travelMode)
                }

                override fun onError(@NavigationApi.ErrorCode errorCode: Int) {
                    when (errorCode) {
                        NavigationApi.ErrorCode.NOT_AUTHORIZED -> displayMessage("Your API key is invalid or not authorized to use the Navigation SDK.")
                        NavigationApi.ErrorCode.TERMS_NOT_ACCEPTED -> displayMessage("Please accept the terms and conditions to continue.")
                        NavigationApi.ErrorCode.NETWORK_ERROR -> displayMessage("Error loading Navigation SDK: Network error.")
                        NavigationApi.ErrorCode.LOCATION_PERMISSION_MISSING -> displayMessage("Error loading Navigation SDK: Location permission is missing.")
                        else -> displayMessage("Error loading Navigation SDK: $errorCode")
                    }
                    finish()
                }
            }
        )
    }

    private fun requestLocationPermissions(routeToken: String, placeIds: ArrayList<String>, travelMode: String) {
        // List of permissions to request
        val permissionsToRequest = mutableListOf<String>()

        // Check ACCESS_FINE_LOCATION permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        // Check ACCESS_COARSE_LOCATION permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        // Check FOREGROUND_SERVICE_LOCATION permission
        if (ContextCompat.checkSelfPermission(this, "android.permission.FOREGROUND_SERVICE_LOCATION")
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add("android.permission.FOREGROUND_SERVICE_LOCATION")
        }

        // Check POST_NOTIFICATIONS permission for Android 13 (API level 33) and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Request all permissions if any are not granted
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION
            )
        } else {
            // Permissions are already granted, proceed with the service or navigation
            initializeNavigationSdk(routeToken, placeIds, travelMode)
        }
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        locationPermissionGranted = false
        if (requestCode == PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                locationPermissionGranted = true
            }
        }
        if (requestCode == PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                routeToken?.let { requestLocationPermissions(it, placeIds, travelMode) }
            }
        }
    }


    private fun navigateWithMultipleStops(routeToken: String, placesIds: List<String>, travelMode: String) {
        // Create a list of waypoints based on the provided place IDs
        val waypoints = mutableListOf<Waypoint>()

        try {
            for (placeId in placesIds) {
                // Build each waypoint using the placeId and add to the list
                val waypoint = Waypoint.builder()
                    .setPlaceIdString(placeId)
                    .setVehicleStopover(true)
                    .build()
                waypoints.add(waypoint)
            }
        } catch (e: Waypoint.UnsupportedPlaceIdException) {
            displayMessage("Error starting navigation: One or more Place IDs are not supported.")
            return
        }

        // Set display options for stop signs and traffic lights
        val displayOptions = DisplayOptions().apply {
            showStopSigns(true)
            showTrafficLights(true)
        }

        val pendingRoute = if (travelMode == "WALK" || travelMode == "TRANSIT") {
            val routingOptions = RoutingOptions().apply {
                travelMode(if (travelMode == "WALK") TravelMode.WALKING else TravelMode.DRIVING)
                avoidFerries(true)
                avoidTolls(true)
                alternateRoutesStrategy(AlternateRoutesStrategy.SHOW_ALL)
            }
            Log.e("StartNavigationsActivity", "ROUTE TOKEN: $routeToken")
            navigator.setDestinations(waypoints, routingOptions, displayOptions)
        } else {
            val travelModeTraffic = if (travelMode == "DRIVE") CustomRoutesOptions.TravelMode.DRIVING else CustomRoutesOptions.TravelMode.TWO_WHEELER

            val customRoutesOptions = CustomRoutesOptions.builder()
                .setRouteToken(routeToken)
                .setTravelMode(travelModeTraffic)
                .build()

            Log.e("StartNavigationsActivity", "ROUTE TOKEN AT ELSE: $routeToken")

            navigator.setDestinations(waypoints, customRoutesOptions, displayOptions)

        }

// Handle route result
        handleRouteResult(pendingRoute)
    }

    private fun handleRouteResult(pendingRoute: ListenableResultFuture<Navigator.RouteStatus>) {
        pendingRoute.setOnResultListener { code ->
            when (code) {
                Navigator.RouteStatus.OK -> {
                    displayMessage("Route successfully calculated with multiple stops!")
                    navigator.setAudioGuidance(Navigator.AudioGuidance.VOICE_ALERTS_AND_GUIDANCE)
                    registerNavigationListeners()

                    if (isSimulated) {
                        navigator.simulator.simulateLocationsAlongExistingRoute(SimulationOptions().speedMultiplier(5F))
                    }
                    navigator.startGuidance()
                    startTrip()
                }
                Navigator.RouteStatus.NO_ROUTE_FOUND -> displayMessage("Error starting navigation: No route found.")
                Navigator.RouteStatus.NETWORK_ERROR -> displayMessage("Error starting navigation: Network error.")
                Navigator.RouteStatus.ROUTE_CANCELED -> displayMessage("Error starting navigation: Route canceled.")
                else -> displayMessage("Error starting navigation: $code")
            }
        }
    }

    private fun registerNavigationListeners() {
        mArrivalListener = ArrivalListener { arrivalEvent ->

            if (arrivalEvent.isFinalDestination) {
                displayMessage("onArrival: You've arrived at the final destination.")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                showTripSummaryDialog(computeTotalDistance(), computeTotalTime(), computeArrivalTime(), computeAverageSpeed(),getTrafficConditions())
                navigator.simulator.unsetUserLocation()
            } else {
                // Continue to the next waypoint if not at the final destination
                navigator.continueToNextDestination()
                navigator.startGuidance()
            }

        }

        // Listens for arrival at a waypoint.
        navigator.addArrivalListener(mArrivalListener)
    }

    private fun displayMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        Log.e(TAG, message)
    }

    private fun calculateTotalDistanceCovered(): Float {
        val traveledRoute = navigator.traveledRoute
        var totalDistance = 0f

        for (i in 0 until traveledRoute.size - 1) {
            totalDistance += calculateDistance(traveledRoute[i], traveledRoute[i + 1])
        }

        return totalDistance
    }

    private fun calculateDistance(start: LatLng, end: LatLng): Float {
        val results = FloatArray(1)
        Location.distanceBetween(start.latitude, start.longitude, end.latitude, end.longitude, results)
        return results[0]
    }

    private fun computeTotalDistance(): String {
        val distanceCovered = calculateTotalDistanceCovered() // This should return distance in meters
        return String.format("%.2f km", distanceCovered / 1000) // Convert to kilometers
    }

    private fun startTrip() {
        startTime = System.currentTimeMillis() // Record start time
    }

    private fun computeTotalTime(): String {
        val endTime = System.currentTimeMillis() // Get the current time
        val totalTimeInMillis = endTime - startTime // Calculate elapsed time
        val totalSeconds = totalTimeInMillis / 1000 // Convert milliseconds to seconds

        // Calculate hours and minutes
        val totalMinutes = totalSeconds / 60
        val totalHours = totalMinutes / 60

        // Remaining minutes after calculating hours
        val remainingMinutes = totalMinutes % 60

        // Format the total time as a string based on the values of totalHours and remainingMinutes
        return when {
            totalHours > 0 && remainingMinutes > 0 -> String.format("%d hr %d min", totalHours, remainingMinutes)
            totalHours > 0 -> String.format("%d hr", totalHours)
            remainingMinutes > 0 -> String.format("%d min", remainingMinutes)
            else -> "0 min" // If both hours and minutes are 0, display "0 min" (optional)
        }
    }


    private fun computeArrivalTime(): String {
        val currentTime = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return dateFormat.format(Date(currentTime)) // Format current time as "HH:MM AM/PM"
    }

    private fun computeAverageSpeed(): String {
        val totalDistanceCovered = calculateTotalDistanceCovered() // In meters
        val totalTimeInMillis = System.currentTimeMillis() - startTime
        val averageSpeed = if (totalTimeInMillis > 0) {
            (totalDistanceCovered / (totalTimeInMillis / 1000)) * 3.6 // Convert m/s to km/h
        } else {
            0f
        }
        return String.format("%.2f km/h", averageSpeed)
    }

    private fun getTrafficConditions(): String {
        // Implement logic based on current speed or API response
        // Example: If average speed is significantly lower than expected, assume heavy traffic
        val averageSpeed = computeAverageSpeed().removeSuffix(" km/h").toFloat()
        return when {
            averageSpeed < 20 -> "Heavy"
            averageSpeed < 40 -> "Moderate"
            else -> "Light"
        }
    }


    private fun showTripSummaryDialog(totalDistance: String, totalTime: String, arrivalTime: String, averageSpeed: String, trafficConditions: String) {
        // Inflate the custom layout using View Binding
        val binding = DialogTripRecapBinding.inflate(layoutInflater)

        // Populate the TextViews in the binding object
        binding.totalDistanceTextView.text = "Total Distance: $totalDistance"
        binding.totalTimeTextView.text = "Total Time: $totalTime"
        binding.arrivalTimeTextView.text = "Arrival Time: $arrivalTime"
        binding.averageSpeedTextView.text = "Average Speed: $averageSpeed"
        binding.trafficConditionsTextView.text = "Traffic: $trafficConditions"
        // Regular expression to extract place name and address
        val fullText = navigator.currentRouteSegment.destinationWaypoint.title
        val regex = Regex("""^(.*?)(?:,\s*(.*))?$""") // Match everything before the first comma and everything after

        val matchResult = regex.find(fullText)
        if (matchResult != null) {
            val placeName = matchResult.groups[1]?.value?.trim() ?: ""
            val address = matchResult.groups[2]?.value?.trim() ?: ""

            binding.placeNameTextView.text = placeName // Set only the place name
            binding.addressTextView.text = address // If you have an address TextView
        } else {
            // Handle unexpected format
            binding.placeNameTextView.text = fullText // Fallback
        }


        // Create the AlertDialog
        val alertDialog = AlertDialog.Builder(this)
            .setView(binding.root) // Use the root view from the binding
            .setCancelable(true)
            .create()

        // Handle Done button
        binding.doneButton.setOnClickListener {
            alertDialog.dismiss() // Close the dialog
            finish()
        }

        binding.closeButton.setOnClickListener {
            alertDialog.dismiss()
        }

        // Show the dialog
        alertDialog.show()
    }

    override fun onDestroy() {
        super.onDestroy()

        // Stop any ongoing navigation
        if (::navigator.isInitialized) {
            navigator.stopGuidance()
        }

        // Remove navigation fragment if it exists
        supportFragmentManager.findFragmentById(R.id.navigation_fragment)?.let { fragment ->
            supportFragmentManager.beginTransaction().remove(fragment).commit()
        }

        // Check if the activity is finishing before removing listeners
        if (isFinishing) {
            // Use safe calls to avoid NullPointerException
            mArrivalListener?.let { navigator.removeArrivalListener(it) }
            mRouteChangedListener?.let { navigator.removeRouteChangedListener(it) }
            mRemainingTimeOrDistanceChangedListener?.let { navigator.removeRemainingTimeOrDistanceChangedListener(it) }
            mRoadSnappedLocationProvider?.removeLocationListener(mLocationListener)
            displayMessage("OnDestroy: Released navigation listeners.")
        }

        // Clear destinations and perform cleanup, ensuring navigator is initialized
        if (::navigator.isInitialized) {
            navigator.clearDestinations()
            navigator.cleanup()
        }
    }


}
