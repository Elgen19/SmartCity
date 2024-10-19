package com.elgenium.smartcity

import PlacesClientSingleton
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.elgenium.smartcity.databinding.ActivityStartNavigationsBinding
import com.elgenium.smartcity.databinding.BottomSheetAddStopBinding
import com.elgenium.smartcity.databinding.DialogTripRecapBinding
import com.elgenium.smartcity.intelligence.AIProcessor
import com.elgenium.smartcity.shared_preferences_keys.SettingsKeys
import com.elgenium.smartcity.speech.SpeechRecognitionHelper
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.CameraPerspective
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.navigation.AlternateRoutesStrategy
import com.google.android.libraries.navigation.CustomRoutesOptions
import com.google.android.libraries.navigation.DisplayOptions
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
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class StartNavigationsActivity : AppCompatActivity() {

    private val TAG = "StartNavigationsActivity"
    private lateinit var navigator: Navigator
    private lateinit var navFragment: SupportNavigationFragment
    private var mArrivalListener: ArrivalListener? = null
    private var mRouteChangedListener: RouteChangedListener? = null
    private val placesClient by lazy { PlacesClientSingleton.getClient(this) }
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
    private lateinit var speechRecognitionHelper: SpeechRecognitionHelper
    private lateinit var binding: ActivityStartNavigationsBinding
    private lateinit var aiProcessor: AIProcessor
    private lateinit var textToSpeech: TextToSpeech
    private var mMap: GoogleMap? = null
    private val markersList = mutableListOf<Marker>()
    private val markerPlaceIdMap = HashMap<Marker, String>()
    private var IS_ADDING_STOP = false
    private var NUM_STOPS = 1
    private lateinit var sharedPreferences: SharedPreferences
    private var isAudioGuidanceEnabled = true


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStartNavigationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
        isAudioGuidanceEnabled = sharedPreferences.getBoolean("set_audio", true)


        Log.e("StartNavigationsActivity", "audio guidance at oncreate: $isAudioGuidanceEnabled" )


        travelMode = intent.getStringExtra("TRAVEL_MODE") ?: ""
        routeToken = intent.getStringExtra("ROUTE_TOKEN") ?: "NO ROUTE TOKEN"
        placeIds = intent.getStringArrayListExtra("PLACE_IDS") ?: ArrayList()
        isSimulated = intent.getBooleanExtra("IS_SIMULATED", false)

        initializer()

        Log.e("StartNavigationsActivity", "TRAVEL MODE AT NAVIGATION: $travelMode" )
        Log.e("StartNavigationsActivity", "ROUTE TOKEN AT NAVIGATION: $routeToken" )


        if (routeToken == null)
            routeToken = "NO ROUTE TOKEN"
        // Use the placeIds and routeToken for further navigation logic
        requestLocationPermissions(routeToken!!, placeIds, travelMode)
    }

    private fun initializeSpeechRecognizer() {
        speechRecognitionHelper = SpeechRecognitionHelper(
            activity = this,
            onResult = { transcription ->
                // Handle the recognized speech text
                processUserQuery(transcription)
                displayMessage("Recognized Speech: $transcription")
                Toast.makeText(this, "You said: $transcription", Toast.LENGTH_SHORT).show()
            },
            onError = { error ->
                // Handle any errors
               displayMessage("Speech Recognition Error: $error")
                Toast.makeText(this, "Error: $error", Toast.LENGTH_SHORT).show()
            }
        )

    }

    private fun initializer() {
        aiProcessor = AIProcessor(this)
        initializeTTS()
        initializeSpeechRecognizer()
    }

    private fun initializeTTS() {
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTS", "Language not supported")
                }
            } else {
                Log.e("TTS", "Initialization failed")
            }
        }
    }

    private fun processUserQuery(query: String) {
        // Launch a coroutine in the lifecycleScope
        lifecycleScope.launch {
            try {
                // Call the processUserQuery method of AIProcessor
                speakResponse("Searching, please wait.")
                val result = aiProcessor.processUserQuery(query)
                aiProcessor.intentClassification(aiProcessor.parseUserQuery(result))
                displayMessage("Searching... Please wait.")

                val placesInfo = aiProcessor.extractPlaceInfo()

                if (placesInfo.isNotEmpty()) {
                    speakResponse("Here's what I've got.")
                    plotMarkers(placesInfo)
                } else
                    speakResponse("Unfortunately, I cannot find what you are looking for.")

                // Log the result
                Log.e("AIProcessor", result)


            } catch (e: Exception) {
                // Handle any exceptions that might occur during processing
                Log.e("AI Error", "Error processing query", e)
            }
        } }

    private fun speakResponse(response: String) {
        // Speak the response
        textToSpeech.speak(response, TextToSpeech.QUEUE_FLUSH, null, null)
    }


    @SuppressLint("PotentialBehaviorOverride")
    private fun plotMarkers(placesInfo: List<Map<String, Any>>) {
        // Clear existing markers from the map
        clearMarkers()

        // Initialize LatLngBounds.Builder to calculate bounds for all markers
        val boundsBuilder = LatLngBounds.Builder()

        Log.e(TAG, "it worked here")

        // Log the size of placesInfo
        Log.e(TAG, "Number of places info: ${placesInfo.size}")

        // Iterate through the places info and add markers to the map
        placesInfo.forEach { placeInfo ->
            // Extracting values from the map
            val latLng = placeInfo["latLng"] as LatLng
            val name = placeInfo["name"] as String
            val address = placeInfo["address"] as String
            val placeId = placeInfo["placeId"] as String

            // Add a marker with a visible icon
            val markerOptions = MarkerOptions()
                .position(latLng)
                .title(name) // Set the title to show in the info window
                .snippet(address) // Optional: show address in the info window
                .alpha(1f)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN)) // Use a visible color

            // Add the visible marker to the map and store it in the markers list
            val marker = mMap?.addMarker(markerOptions)
            if (marker != null) {
                markersList.add(marker) // Keep track of the marker
                // Include this marker's position in the bounds
                boundsBuilder.include(marker.position)

                // Store the marker and placeId mapping
                markerPlaceIdMap[marker] = placeId
            }

            mMap?.setOnMarkerClickListener { marker2 ->
                // Retrieve the placeId using the marker
                val clickedPlaceId = markerPlaceIdMap[marker2] ?: "NO PLACE ID"
                Log.e(TAG, "Marker clicked: ${marker2.title}, PlaceId: $clickedPlaceId")

                // When a marker is clicked, show the bottom sheet
                showAddStopBottomSheet(marker2,clickedPlaceId)

                true // Return true to indicate the click was handled
            }

            // Log the place information for debugging
            Log.e(TAG, "Added marker for: Name: $name, Address: $address, LatLng: $latLng, PlaceId: $placeId")
        }

        // After adding all markers, move and zoom the camera to show all markers
        if (markersList.isNotEmpty()) {
            val bounds = boundsBuilder.build()
            val padding = 100 // Padding around the bounds (in pixels)

            // Animate the camera to fit the bounds with padding
            mMap?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
        }
    }


    private fun showAddStopBottomSheet(marker: Marker, placeId: String) {
        val bottomSheetView = BottomSheetAddStopBinding.inflate(layoutInflater)

        val bottomSheetDialog = BottomSheetDialog(this)
        bottomSheetDialog.setContentView(bottomSheetView.root)

        // Set the marker's title and snippet (name and address) in the text views
        bottomSheetView.textViewPlaceName.text = marker.title
        bottomSheetView.textViewPlaceAddress.text = marker.snippet

        // Handle Add Stop button click
        bottomSheetView.buttonAddStop.setOnClickListener {
            // Add the placeId at the first index of placeIds
            placeIds.add(0, placeId)
            displayMessage("PLACEIDS: $placeIds")
            IS_ADDING_STOP = true
            NUM_STOPS += 1
            clearMarkers()
            binding.continueToNextDestinationLayout.visibility = if (NUM_STOPS == 1) View.GONE else View.VISIBLE
            binding.spacer1.visibility = if (NUM_STOPS == 1 ) View.GONE else View.VISIBLE
            Log.e(TAG, "Place ID $placeId added to placeIds at index 0")
            Log.e(TAG, "IS ADDING STOP VALUE: $IS_ADDING_STOP")

            navigateWithMultipleStops("NO ROUTE TOKEN", placeIds, travelMode)
            fetchPlaceDetailsForCard()


            // Dismiss the bottom sheet
            bottomSheetDialog.dismiss()
        }

        // Handle Cancel button click
        bottomSheetView.buttonCancel.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        // Show the BottomSheetDialog
        bottomSheetDialog.show()
    }

    private fun showNavigationOptionsBottomSheet() {
        // Inflate the bottom sheet layout using view binding
        val bottomSheet = binding.bottomSheet

        // Retrieve the BottomSheetBehavior and configure it
        val behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.peekHeight = 160  // Set the desired peek height
        behavior.isHideable = false  // Prevent it from being fully dismissed
        behavior.state = BottomSheetBehavior.STATE_COLLAPSED

        fetchPlaceDetailsForCard()
        binding.continueToNextDestinationLayout.visibility = if (NUM_STOPS == 1) View.GONE else View.VISIBLE
        binding.spacer1.visibility = if (NUM_STOPS == 1 ) View.GONE else View.VISIBLE
        binding.voiceGuidanceSwitch.isChecked = isAudioGuidanceEnabled

        // Set up click listeners for the actions
        binding.assistantButton.setOnClickListener {
            speechRecognitionHelper.startListening()
        }

        binding.voiceGuidanceSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Enable voice guidance when switch is on
                with(sharedPreferences.edit()) {
                    putBoolean(SettingsKeys.KEY_SET_AUDIO, true)
                    apply()
                }
                navigator.setAudioGuidance(Navigator.AudioGuidance.VOICE_ALERTS_AND_GUIDANCE)
                isAudioGuidanceEnabled = true
                displayMessage("Audio guidance will be enabled in a moment")
                Log.e("StartNavigationsActivity", "audio guidance at method: $isAudioGuidanceEnabled" )
            } else {
                // Disable voice guidance when switch is off
                with(sharedPreferences.edit()) {
                    putBoolean(SettingsKeys.KEY_SET_AUDIO, false)
                    apply()
                }
                navigator.setAudioGuidance(Navigator.AudioGuidance.SILENT)
                isAudioGuidanceEnabled = false
                displayMessage("Audio guidance will be disabled in a moment")
                Log.e("StartNavigationsActivity", "audio guidance at method: $isAudioGuidanceEnabled" )
            }
        }




        binding.continueToNextDestinationLayout.setOnClickListener {
           if (NUM_STOPS != 1) {
               navigator.continueToNextDestination()
               NUM_STOPS -= 1
               placeIds.removeAt(0)
               displayMessage("NUM STOPS VALUE: $NUM_STOPS")
               fetchPlaceDetailsForCard()
           }
            binding.continueToNextDestinationLayout.visibility = if (NUM_STOPS == 1) View.GONE else View.VISIBLE
            binding.spacer1.visibility = if (NUM_STOPS == 1 ) View.GONE else View.VISIBLE
        }

        binding.viewZoomedOutLayout.setOnClickListener {
            navFragment.showRouteOverview()
        }



    }


    private fun fetchPlaceDetailsForCard(){
        fetchPlaceDetailsFromAPI(placeIds[0]) { place ->
            if (place != null) {
                binding.tvPlaceName.text = place.name
                binding.tvPlaceAddress.text = place.address
                binding.tvRemainingStop.text = "Remaining destination(s): ${NUM_STOPS}"
            }

        }
    }





    // Method to clear existing markers from the map
    private fun clearMarkers() {
        // Remove markers from the map
        markersList.forEach { it.remove() }
        // Clear the list
        markersList.clear()
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
                    navFragment.setTripProgressBarEnabled(true)
                    navFragment.setSpeedometerEnabled(true)
                    navFragment.setSpeedLimitIconEnabled(true)
                    navFragment.setTrafficIncidentCardsEnabled(true)
                    navFragment.setTrafficPromptsEnabled(true)
                    navFragment.setEtaCardEnabled(true)
                    navFragment.setStylingOptions(StylingOptions()
                        .headerGuidanceRecommendedLaneColor(resources.getColor(R.color.brand_color))
                        .primaryDayModeThemeColor(resources.getColor(R.color.secondary_color)))

                    // Set the camera to follow the device location with 'TILTED' driving view.
                    navFragment.getMapAsync { googleMap ->
                        mMap = googleMap
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
                    showNavigationOptionsBottomSheet()

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

        val pendingRoute = if (IS_ADDING_STOP == true && routeToken == "NO ROUTE TOKEN") {
            val routingOptions = RoutingOptions().apply {
                travelMode(if (travelMode == "WALK") TravelMode.WALKING else if (travelMode == "TWO_WHEELER")  TravelMode.TWO_WHEELER else TravelMode.DRIVING)
                avoidFerries(true)
                avoidTolls(true)
                alternateRoutesStrategy(AlternateRoutesStrategy.SHOW_ALL)
            }
            Log.e("StartNavigationsActivity", "ROUTE TOKEN: $routeToken")
            Log.e("StartNavigationsActivity", "TRAVEL MODE: $travelMode")
            Log.e("StartNavigationsActivity", "IS ADDING STOP VALUE: $IS_ADDING_STOP")

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

        handleRouteResult(pendingRoute)
    }

    private fun handleRouteResult(pendingRoute: ListenableResultFuture<Navigator.RouteStatus>) {
        pendingRoute.setOnResultListener { code ->
            when (code) {
                Navigator.RouteStatus.OK -> {
                    displayMessage("Route successfully calculated with multiple stops!")
                    if (isAudioGuidanceEnabled)
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


        // Listener for remaining time or distance changes
        mRemainingTimeOrDistanceChangedListener = RemainingTimeOrDistanceChangedListener {
            // Get the current time and distance to the next destination
            val timeAndDistance = navigator.currentTimeAndDistance

            // Get the remaining time in seconds and distance in meters
            val remainingTimeInSeconds = timeAndDistance.seconds
            val remainingDistanceInMeters = timeAndDistance.meters

            // Update UI with the new remaining time and distance
            runOnUiThread {
                binding.tvJourneyTime.text = formatTime(remainingTimeInSeconds) // Convert seconds to a readable format
                binding.tvTotalKilometers.text = String.format("Distance: %.1f km", remainingDistanceInMeters / 1000.0) // Convert meters to kilometers
                binding.tvEta.text = "ETA: ${calculateETA(remainingTimeInSeconds)}" // Calculate and display ETA
            }
        }

// Register the remaining time or distance changed listener
        navigator.addRemainingTimeOrDistanceChangedListener(5, 10, mRemainingTimeOrDistanceChangedListener) // Change thresholds as needed

    }

    private fun formatTime(seconds: Int): String {
        val minutes = seconds / 60
        return if (minutes > 0) {
            "$minutes mins"
        } else {
            "$seconds secs"
        }
    }

    private fun calculateETA(seconds: Int): String {
        // You can implement this based on your requirements, here's a simple version
        val currentTime = System.currentTimeMillis()
        val etaTime = currentTime + (seconds * 1000)
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return sdf.format(Date(etaTime))
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

    private fun fetchPlaceDetailsFromAPI(placeId: String, callback: (Place?) -> Unit) {
        val fields = listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.ADDRESS
        )
        val request = FetchPlaceRequest.builder(placeId, fields).build()

        placesClient.fetchPlace(request)
            .addOnSuccessListener { response ->
                val place = response.place
                callback(place)
            }
            .addOnFailureListener { exception ->
               displayMessage("Error fetching place details")
                callback(null) // Return null if the API call fails
            }
    }


    override fun onDestroy() {
        super.onDestroy()

        if (::speechRecognitionHelper.isInitialized || ::textToSpeech.isInitialized) {
            speechRecognitionHelper.stopListening()
            textToSpeech.stop()
            textToSpeech.shutdown()
        }

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

