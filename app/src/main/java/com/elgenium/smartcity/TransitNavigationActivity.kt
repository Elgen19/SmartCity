package com.elgenium.smartcity

import PlacesClientSingleton
import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.elgenium.smartcity.databinding.ActivityTransitNavigationBinding
import com.elgenium.smartcity.databinding.DialogTerminateNavigationBinding
import com.elgenium.smartcity.databinding.DialogTripRecapBinding
import com.elgenium.smartcity.intelligence.AIProcessor
import com.elgenium.smartcity.shared_preferences_keys.SettingsKeys
import com.elgenium.smartcity.singletons.ActivityNavigationUtils
import com.elgenium.smartcity.speech.SpeechRecognitionHelper
import com.elgenium.smartcity.speech.TextToSpeechHelper
import com.google.ai.client.generativeai.type.ResponseStoppedException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.CameraPerspective
import com.google.android.gms.maps.model.LatLng
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class TransitNavigationActivity : AppCompatActivity(){

    private val TAG = "TransitNavigationActivity"
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
    private var transitLatlng: ArrayList<String> = ArrayList()
    private var placeIds: ArrayList<String> = ArrayList()
    private var travelModeList: ArrayList<String> = ArrayList()
    private var transitInstructions: ArrayList<String> = ArrayList()
    private var isSimulated = false
    private lateinit var speechRecognitionHelper: SpeechRecognitionHelper
    private lateinit var textToSpeech: TextToSpeechHelper
    private lateinit var binding: ActivityTransitNavigationBinding
    private lateinit var aiProcessor: AIProcessor
    private var mMap: GoogleMap? = null
    private lateinit var sharedPreferences: SharedPreferences
    private var isAudioGuidanceEnabled = true
    private var isTrafficOverlayEnabled = false
    private var isRecomputeWaypointEnabled = false
    private var hasAlreadyArrivedAtFinalDestination = false
    private var DEFAULT_STOPS = 0
    private var maneuverCounter = 0
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var transitCoordinates: MutableList<LatLng> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransitNavigationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        clearList()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        initializer()

        sharedPreferences = getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
        isAudioGuidanceEnabled = sharedPreferences.getBoolean("set_audio", true)
        isTrafficOverlayEnabled = sharedPreferences.getBoolean("map_overlay", false)
        isRecomputeWaypointEnabled = sharedPreferences.getBoolean("recompute_waypoint", false)


        isSimulated = intent.getBooleanExtra("IS_SIMULATED", false)
        transitLatlng = intent.getStringArrayListExtra("TRANSIT_LATLNG_LIST") ?: ArrayList()
        transitInstructions = intent.getStringArrayListExtra("NAVIGATION_INSTRUCTIONS") ?: ArrayList()
        placeIds = intent.getStringArrayListExtra("PLACE_IDS") ?: ArrayList()
        travelModeList = intent.getStringArrayListExtra("TRAVEL_MODES") ?: ArrayList()


        // Convert transitLatlng strings to LatLng objects
        transitCoordinates = transitLatlng.map { latLngStr ->
            val parts = latLngStr.split(",")
            LatLng(parts[0].toDouble(), parts[1].toDouble())
        }.toMutableList()



        DEFAULT_STOPS = transitLatlng.size
        displayMessage("Starting transit navigation")
        Log.e("TransitNavigationActivity", "audio guidance at oncreate: $isAudioGuidanceEnabled" )
        Log.e("TransitNavigationActivity", "traffic overlay at oncreate: $isTrafficOverlayEnabled" )
        Log.e("TransitNavigationActivity", "Transit LatLng List: $transitLatlng")
        Log.e("TransitNavigationActivity", "Default stops is: $DEFAULT_STOPS")
        Log.e("TransitNavigationActivity", "Navigation instructions: $transitInstructions")
        Log.e("TransitNavigationActivity", "Transit Lat lng size is: $transitLatlng.size")


        requestLocationPermissions()
        showNavigationOptionsBottomSheet()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showNavigationTerminationDialog()
            }
        })
    }



    private fun showNavigationTerminationDialog() {
        val binding = DialogTerminateNavigationBinding.inflate(LayoutInflater.from(this))

        val dialog = AlertDialog.Builder(this)
            .setView(binding.root)
            .setCancelable(true) // Allows closing on back press or touch outside
            .create()

        // Set button actions
        binding.buttonShutdown.setOnClickListener {
            cleanup()
            Handler(Looper.getMainLooper()).postDelayed({
                ActivityNavigationUtils.navigateToActivity(this, PlacesActivity::class.java, true)
            }, 300)
        }

        binding.buttonCancel.setOnClickListener {
            dialog.dismiss()
        }

        // Show the dialog
        dialog.show()
    }



    private fun initializeSpeechRecognizerAndTextSpeech() {
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

        textToSpeech = TextToSpeechHelper()
        textToSpeech.initializeTTS(this)

    }

    private fun initializer() {
        aiProcessor = AIProcessor(this)

        initializeSpeechRecognizerAndTextSpeech()
    }

    private fun processUserQuery(query: String) {
        // Launch a coroutine in the lifecycleScope
        lifecycleScope.launch {
            try {
                // Call the processUserQuery method of AIProcessor
                textToSpeech.speakResponse("Searching, please wait.")
                val result = aiProcessor.processUserQuery(query)
                displayMessage("RESULT VALUE: $result")
                aiProcessor.intentClassification(aiProcessor.parseUserQuery(result))

                val placesInfo = aiProcessor.extractPlaceInfo()

                if (aiProcessor.hasPlaceIdAndIsValidPlace()) {
                    textToSpeech.speakResponse("Here's what I've got.")
                } else {
                    textToSpeech.speakResponse("Unfortunately, I couldn't find any information related to your query, '$query.' Please consider trying alternative keywords for your search.")
                }

                // Log the result
                Log.e("AIProcessor", result)
            } catch (e: ResponseStoppedException) {
                Log.e("AIProcessor", "Response generation stopped due to safety concerns: ${e.message}")
                textToSpeech.speakResponse("Unfortunately, I cannot find what you are looking for.")
            } catch (e: Exception) {
                Log.e("AIProcessor", "Error processing query: ${e.message}", e)
                textToSpeech.speakResponse("Unfortunately, I cannot find what you are looking for right now.")
            }
        }
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
        binding.voiceGuidanceSwitch.isChecked = isAudioGuidanceEnabled
        binding.enableRecomputeSwitch.isChecked = isRecomputeWaypointEnabled

        // Set up click listeners for the actions
        binding.assistantButton.setOnClickListener {
            speechRecognitionHelper.startListening()
        }

        binding.buttonShutdown.setOnClickListener {
            showNavigationTerminationDialog()
        }

        binding.voiceGuidanceSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Enable voice guidance when switch is on
                with(sharedPreferences.edit()) {
                    putBoolean(SettingsKeys.KEY_SET_AUDIO, true)
                    apply()
                }
                isAudioGuidanceEnabled = true
                displayMessage("Audio guidance will be enabled in a moment")
                Log.e("StartNavigationsActivity", "audio guidance at method: $isAudioGuidanceEnabled" )
            } else {
                // Disable voice guidance when switch is off
                with(sharedPreferences.edit()) {
                    putBoolean(SettingsKeys.KEY_SET_AUDIO, false)
                    apply()
                }
                isAudioGuidanceEnabled = false
                displayMessage("Audio guidance will be disabled in a moment")
                Log.e("StartNavigationsActivity", "audio guidance at method: $isAudioGuidanceEnabled" )
            }
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
            }
        }
        binding.tvTravelMode.text = travelModeList[maneuverCounter]
        binding.tvDirections.text = transitInstructions[maneuverCounter]
    }

    private fun initializeNavigationSdk() {
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
                    this@TransitNavigationActivity.navigator = navigator
                    navFragment = supportFragmentManager.findFragmentById(R.id.navigation_fragment) as SupportNavigationFragment
                    navFragment.setTripProgressBarEnabled(true)
                    navFragment.setSpeedometerEnabled(true)
                    navFragment.setSpeedLimitIconEnabled(true)
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

                        googleMap.isTrafficEnabled = isTrafficOverlayEnabled
                        googleMap.setIndoorEnabled(true)
                    }
                    navigateUsingTransit()
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

    private fun requestLocationPermissions() {
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
            initializeNavigationSdk()
//            startLocationUpdates()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        locationPermissionGranted = false
        if (requestCode == PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                locationPermissionGranted = true
                requestLocationPermissions()
            }
        }
    }

    private fun navigateUsingTransit() {
        val waypoints = mutableListOf<Waypoint>()

        try {
            // Process each transit lat-lng to create waypoints
            for (transit in transitLatlng) {
                val latLng = transit.split(",")
                val latitude = latLng[0].toDouble()
                val longitude = latLng[1].toDouble()

                // Build each waypoint using latitude and longitude
                val waypoint = Waypoint.builder()
                    .setLatLng(latitude, longitude)
                    .setVehicleStopover(true)
                    .build()

                waypoints.add(waypoint) // Add to waypoints list
            }

            val displayOptions = DisplayOptions().apply {
                showStopSigns(true)
                showTrafficLights(true)
            }

            // Always use DRIVING as the travel mode
            val routingOptions = RoutingOptions().apply {
                travelMode(TravelMode.WALKING)
                avoidFerries(true)
                avoidTolls(true)
            }

            // Set the destinations using waypoints and routing options
            val pendingRoute = navigator.setDestinations(waypoints, routingOptions, displayOptions)



            handleRouteResult(pendingRoute)

        } catch (e: Exception) {
            Log.e("StartNavigationsActivity", "Error while navigating using transit: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun handleRouteResult(pendingRoute: ListenableResultFuture<Navigator.RouteStatus>) {
        pendingRoute.setOnResultListener { code ->
            when (code) {
                Navigator.RouteStatus.OK -> {
                    displayMessage("Route successfully calculated with multiple stops!")

                    registerNavigationListeners()

                    if (isSimulated) {
                        navigator.simulator.simulateLocationsAlongExistingRoute(SimulationOptions().speedMultiplier(5F))
                    }

                    if (isAudioGuidanceEnabled) {
                        textToSpeech.speakResponse(transitInstructions[maneuverCounter])
                        maneuverCounter++
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
                hasAlreadyArrivedAtFinalDestination = true
            } else {
                if (maneuverCounter < transitInstructions.size){
                    Log.d(TAG, "Arrived at waypoint  with instruction: ${transitInstructions[maneuverCounter]}")

                    if (isAudioGuidanceEnabled)
                        textToSpeech.speakResponse(transitInstructions[maneuverCounter])

                    handleNextStop()  // Extract repeated logic into a function
                    binding.tvTravelMode.text = travelModeList[maneuverCounter]
                    binding.tvDirections.text = transitInstructions[maneuverCounter]
                    maneuverCounter++
                }
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

    private fun handleNextStop() {
        navigator.continueToNextDestination()
        navigator.startGuidance()
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

    private fun cleanup() {
        if (::speechRecognitionHelper.isInitialized || ::textToSpeech.isInitialized) {
            speechRecognitionHelper.stopListening()
            textToSpeech.stopResponse()
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
            navigator.simulator.unsetUserLocation()
            displayMessage("OnDestroy: Released navigation listeners.")
        }

        // Clear destinations and perform cleanup, ensuring navigator is initialized
        if (::navigator.isInitialized) {
            navigator.clearDestinations()
            navigator.cleanup()
        }
    }

    override fun onStop() {
        super.onStop()
        if (::speechRecognitionHelper.isInitialized || ::textToSpeech.isInitialized) {
            speechRecognitionHelper.stopListening()
            textToSpeech.stopResponse()
        }
        clearList()
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanup()
        clearList()

    }

    private fun clearList() {
        transitInstructions.clear()
        travelModeList.clear()
        transitLatlng.clear()
        transitCoordinates.clear()
    }


}

