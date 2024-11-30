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
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.elgenium.smartcity.contextuals.FuelStopsRecommendation
import com.elgenium.smartcity.contextuals.MealContextuals
import com.elgenium.smartcity.contextuals.PlaceOpeningHoursContextuals
import com.elgenium.smartcity.contextuals.RainLikelihoodCalculator
import com.elgenium.smartcity.databinding.ActivityStartNavigationsBinding
import com.elgenium.smartcity.databinding.BottomSheetAddStopBinding
import com.elgenium.smartcity.databinding.DialogRecomputeStopsBinding
import com.elgenium.smartcity.databinding.DialogTerminateNavigationBinding
import com.elgenium.smartcity.databinding.DialogTripRecapBinding
import com.elgenium.smartcity.intelligence.AIProcessor
import com.elgenium.smartcity.routes_network_request.LatLngMatrix
import com.elgenium.smartcity.routes_network_request.LocationMatrix
import com.elgenium.smartcity.routes_network_request.RouteMatrixDestination
import com.elgenium.smartcity.routes_network_request.RouteMatrixOrigin
import com.elgenium.smartcity.routes_network_request.RouteMatrixRequest
import com.elgenium.smartcity.routes_network_request.WaypointMatrix
import com.elgenium.smartcity.shared_preferences_keys.SettingsKeys
import com.elgenium.smartcity.singletons.ActivityNavigationUtils
import com.elgenium.smartcity.singletons.NavigationBarColorCustomizerHelper
import com.elgenium.smartcity.singletons.RoutesMatrixClientSingleton
import com.elgenium.smartcity.speech.SpeechRecognitionHelper
import com.elgenium.smartcity.speech.StreamingSpeechRecognition
import com.elgenium.smartcity.speech.TextToSpeechHelper
import com.google.ai.client.generativeai.type.ResponseStoppedException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.CameraPerspective
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PointOfInterest
import com.google.android.libraries.navigation.AlternateRoutesStrategy
import com.google.android.libraries.navigation.CustomRoutesOptions
import com.google.android.libraries.navigation.DisplayOptions
import com.google.android.libraries.navigation.ListenableResultFuture
import com.google.android.libraries.navigation.NavigationApi
import com.google.android.libraries.navigation.Navigator
import com.google.android.libraries.navigation.Navigator.ArrivalListener
import com.google.android.libraries.navigation.Navigator.AudioGuidance
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class StartNavigationsActivity : AppCompatActivity(), GoogleMap.OnPoiClickListener{

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
    private lateinit var textToSpeech: TextToSpeechHelper
    private lateinit var binding: ActivityStartNavigationsBinding
    private lateinit var aiProcessor: AIProcessor
    private var mMap: GoogleMap? = null
    private val markersList = mutableListOf<Marker>()
    private val markerPlaceIdMap = HashMap<Marker, String>()
    private var IS_ADDING_STOP = false
    private var NUM_STOPS = 1
    private lateinit var sharedPreferences: SharedPreferences
    private var isAudioGuidanceEnabled = true
    private var isTrafficOverlayEnabled = false
    private var hasAlreadyArrivedAtFinalDestination = false
    private var isNeedOptimization = false
    private var DEFAULT_STOPS = 0
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val allLatLngs = mutableListOf<LatLng>()
    private val rearrangedDestinations = mutableListOf<String>()
    private lateinit var placeDetailsRelatedContextuals: PlaceOpeningHoursContextuals
    private lateinit var mealPlaceRecommender: MealContextuals
    private lateinit var fuelStopsRecommendation: FuelStopsRecommendation
    private var hasExecutedSuggestions = false
    private lateinit var rainLikelihoodCalculator: RainLikelihoodCalculator
    private var isPlaceOpenNowClassifierFinished = false
    private var hasMealDialogDisplayed = false
    private var hasFuelStopDisplayed = false



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStartNavigationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        sharedPreferences = getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
        isAudioGuidanceEnabled = sharedPreferences.getBoolean("set_audio", true)
        isTrafficOverlayEnabled = sharedPreferences.getBoolean("map_overlay", false)
        placeDetailsRelatedContextuals = PlaceOpeningHoursContextuals(this)
        mealPlaceRecommender = MealContextuals(this)
        fuelStopsRecommendation = FuelStopsRecommendation(this)
        rainLikelihoodCalculator = RainLikelihoodCalculator(this)


        NavigationBarColorCustomizerHelper.setNavigationBarColor(this, R.color.primary_color)


        travelMode = intent.getStringExtra("TRAVEL_MODE") ?: ""
        routeToken = intent.getStringExtra("ROUTE_TOKEN") ?: "NO ROUTE TOKEN"
        placeIds = intent.getStringArrayListExtra("PLACE_IDS") ?: ArrayList()
        isSimulated = intent.getBooleanExtra("IS_SIMULATED", false)
        DEFAULT_STOPS = placeIds.size

        initializer()

        if (DEFAULT_STOPS != 0)
            NUM_STOPS = DEFAULT_STOPS

        Log.e("StartNavigationsActivity", "audio guidance at oncreate: $isAudioGuidanceEnabled" )
        Log.e("StartNavigationsActivity", "traffic overlay at oncreate: $isTrafficOverlayEnabled" )
        Log.e("StartNavigationsActivity", "Default stops is: $DEFAULT_STOPS")
        Log.e("StartNavigationsActivity", "Placeids size is: ${placeIds.size}")
        Log.e("StartNavigationsActivity", "TRAVEL MODE AT NAVIGATION: $travelMode" )
        Log.e("StartNavigationsActivity", "ROUTE TOKEN AT NAVIGATION: $routeToken" )


        showNavigationOptionsBottomSheet()
        if (routeToken == null)
            routeToken = "NO ROUTE TOKEN"

        requestLocationPermissions(routeToken!!, placeIds, travelMode)
        Log.e("WaypointOrder", "PLACEID AT ONCREATE AFTER CHANGES: $placeIds" )


        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showNavigationTerminationDialog()
            }
        })



        binding.clearMarkers.setOnClickListener {
            clearMarkers()
            binding.assistantButton.visibility = View.VISIBLE
            binding.clearMarkers.visibility = View.GONE
        }

    }

    private fun displayRainCheckBottomSheet() {
        getCurrentLocation { latLng ->
            if (latLng != null) {
                Log.d("PlacesActivity", "Current Location: ${latLng.latitude}, ${latLng.longitude}")
                // Call the method to fetch rain likelihood and show the dialog
                rainLikelihoodCalculator.fetchRainLikelihoodAndShowDialog(latLng.latitude, latLng.longitude)
            }

        }
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
                allLatLngs.clear()
                navigator.routeSegments.forEach { segment ->
                    // Add all LatLngs from the current segment to the list
                    allLatLngs.addAll(segment.latLngs)
                }
                Log.e("StartNavigationsActivity", "SEARCH STOP LATLNGS: $allLatLngs")
                Log.e("StartNavigationsActivity", "SEARCH STOP LATLNG SIZE: ${allLatLngs.size}")
                aiProcessor.intentClassification(aiProcessor.parseUserQuery(result), allLatLngs)

                val placesInfo = aiProcessor.extractPlaceInfo()
                Log.e("StartNavigationsActivity", "PLACE INFO SIZE: ${placesInfo.size}")


                if (aiProcessor.hasPlaceIdAndIsValidPlace()) {
                    textToSpeech.speakResponse("Here's what I've got.")
                    plotMarkers(placesInfo)
                } else {
                    displayMessage("No match for query: $query")
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

    private fun displayFuelStops(){
        allLatLngs.clear()
        navigator.routeSegments.forEach { segment ->
            // Add all LatLngs from the current segment to the list
            allLatLngs.addAll(segment.latLngs)
        }
        Log.e("StartNavigationsActivity", "FUEL STOP LATLNGS: $allLatLngs")
        Log.e("StartNavigationsActivity", "FUEL STOP LATLNG SIZE: ${allLatLngs.size}")


        fuelStopsRecommendation.performOptimizedTextSearch(
            placesClient,
            allLatLngs
        ) { places ->
            // Step 3: Log and check place details retrieved
            if (places.isEmpty()) {
                Log.e(TAG, "No places found")
                return@performOptimizedTextSearch
            } else {
                Log.e(TAG, "Found ${places.size} places")
            }

            // Step 4: Transform places into map-compatible format for markers
            val placesInfo = places.mapNotNull { place ->
                place.latLng?.let {
                    mapOf(
                        "latLng" to it,
                        "name" to place.name.orEmpty(),
                        "address" to place.address.orEmpty(),
                        "placeId" to place.id.orEmpty()
                    )
                }
            }
            Log.e(TAG, "PlaceInfo: $placesInfo")

            // Step 5: Plot markers on the map using the `plotMarkers` function
            plotMarkers(placesInfo)
        }
    }

    private fun onDialogProceed() {
        // Step 1: Determine the current meal time
        val mealTime = mealPlaceRecommender.getMealTime()

        // Step 2: Retrieve appropriate place types for the meal time
        val placeTypes = mealPlaceRecommender.mealTimePlaceMappings[mealTime] ?: emptyList()

        allLatLngs.clear()
        navigator.routeSegments.forEach { segment ->
            // Add all LatLngs from the current segment to the list
            allLatLngs.addAll(segment.latLngs)
        }
        Log.e("StartNavigationsActivity", "MEAL STOP LATLNGS: $allLatLngs")
        Log.e("StartNavigationsActivity", "MEAL STOP LATLNG SIZE: ${allLatLngs.size}")

        mealPlaceRecommender.performTextSearch(placesClient, placeTypes, allLatLngs, this) { places ->
            // Step 3: Log and check place details retrieved
            if (places.isEmpty()) {
                Log.e(TAG, "No places found for $mealTime")
                return@performTextSearch
            } else {
                Log.e(TAG, "Found ${places.size} places for $mealTime")
            }

            // Step 4: Transform places into map-compatible format for markers
            val placesInfo = places.mapNotNull { place ->
                place.latLng?.let {
                    mapOf(
                        "latLng" to it,
                        "name" to place.name.orEmpty(),
                        "address" to place.address.orEmpty(),
                        "placeId" to place.id.orEmpty()
                    )
                }
            }
            Log.e(TAG, "PlaceInfo: $placesInfo")

            // Step 5: Plot markers on the map using the `plotMarkers` function
            plotMarkers(placesInfo)
        }
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

                marker.showInfoWindow()
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

    private fun showAddStopBottomSheet(marker: Marker?, placeId: String) {
        val bottomSheetView = BottomSheetAddStopBinding.inflate(layoutInflater)

        val bottomSheetDialog = BottomSheetDialog(this)
        bottomSheetDialog.setContentView(bottomSheetView.root)

        if (marker != null){
            // Set the marker's title and snippet (name and address) in the text views
            bottomSheetView.textViewPlaceName.text = marker.title
            bottomSheetView.textViewPlaceAddress.text = marker.snippet
        } else {
            fetchPlaceDetailsFromAPI(placeId) { place ->
                if (place != null) {
                    bottomSheetView.textViewPlaceName.text = place.name
                    bottomSheetView.textViewPlaceAddress.text = place.address
                }
            }
        }

        // Handle Add Stop button click
        bottomSheetView.buttonAddStop.setOnClickListener {
            // Add the placeId at the first index of placeIds
            if (hasAlreadyArrivedAtFinalDestination) {
                NUM_STOPS = 0
                placeIds.removeAt(0)
            }

            if (DEFAULT_STOPS > 0) {
                NUM_STOPS = DEFAULT_STOPS
                DEFAULT_STOPS = 0
            }
            stopConfig(placeId)

            binding.assistantButton.visibility = View.VISIBLE
            binding.clearMarkers.visibility = View.GONE

            // Call recalculateWaypointOrder and handle completion
            recalculateWaypointOrder {
                Log.e("StartNavigationsActivity", "Rearranged list: $rearrangedDestinations")
                Log.e("StartNavigationsActivity", "Placeids: $placeIds")
                Log.e("StartNavigationsActivity", "Is need of optimization: ${(rearrangedDestinations != placeIds)}")

                if (placeIds != rearrangedDestinations) {
                    showRecomputeStopsDialog() // Show dialog if optimization is needed
                    Log.e("StartNavigationsActivity", "Is need optimization should be true")
                } else {
                    navigationConfig() // Proceed with navigation if no optimization is needed
                    Log.e("StartNavigationsActivity", "Is need optimization should be false")
                }
            }

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

    private fun showRecomputeStopsDialog() {
        // Inflate the binding to get the custom layout
        val binding = DialogRecomputeStopsBinding.inflate(layoutInflater)

        // Create an AlertDialog using AlertDialog.Builder
        val dialogBuilder = AlertDialog.Builder(this)
            .setView(binding.root) // Set the custom layout
            .setCancelable(false) // Prevent closing by tapping outside

        // Create the AlertDialog instance
        val alertDialog = dialogBuilder.create()

        // Set button listeners
        binding.buttonCancel.setOnClickListener {
            Log.e("StartNavigationsActivity", "Cancel button fires" )
            navigationConfig()
            alertDialog.dismiss() // Dismiss the dialog when "Cancel" is clicked
        }

        binding.buttonConfirm.setOnClickListener {
            // Handle confirmation logic here
            replacePlaceIds()
            navigationConfig()
            Log.e("StartNavigationsActivity", "Confirm button fires" )
            Log.e("StartNavigationsActivity", "PlaceIDs size at confirm: ${placeIds.size}" )

            alertDialog.dismiss()
        }

        // Show the dialog
        alertDialog.show()
    }

    private fun showNavigationTerminationDialog() {
        val binding = DialogTerminateNavigationBinding.inflate(LayoutInflater.from(this))

        val dialog = AlertDialog.Builder(this)
            .setView(binding.root)
            .setCancelable(true) // Allows closing on back press or touch outside
            .create()

        binding.dialogTitle.text = "Terminate Navigation"
        binding.errorAnimation.setAnimation(R.raw.location)

        // Set button actions
        binding.buttonShutdown.setOnClickListener {
            cleanup()
            Handler(Looper.getMainLooper()).postDelayed({
                ActivityNavigationUtils.navigateToActivity(this, PlacesActivity::class.java, true)
            }, 500)
            dialog.dismiss()
        }

        binding.buttonCancel.setOnClickListener {
            dialog.dismiss()
        }

        // Show the dialog
        dialog.show()
    }

    private fun showNavigationOptionsBottomSheet() {
        // Inflate the bottom sheet layout using view binding
        val bottomSheet = binding.bottomSheet

        // Retrieve the BottomSheetBehavior and configure it
        val behavior = BottomSheetBehavior.from(bottomSheet)
        // Get the screen height in pixels
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels

        // Calculate 20% of the screen height for peekHeight
        val peekHeight = (screenHeight * 0.10).toInt()  // 10% of the screen height

        behavior.peekHeight = peekHeight  // Set the dynamic peek height
        behavior.isHideable = false  // Prevent it from being fully dismissed
        behavior.state = BottomSheetBehavior.STATE_COLLAPSED

        fetchPlaceDetailsForCard(DEFAULT_STOPS)

        binding.continueToNextDestinationLayout.visibility = if (NUM_STOPS == 1) View.GONE else View.VISIBLE
        binding.spacer1.visibility = if (NUM_STOPS == 1 ) View.GONE else View.VISIBLE
        binding.voiceGuidanceSwitch.isChecked = isAudioGuidanceEnabled

        // Set up click listeners for the actions
        binding.assistantButton.setOnClickListener {
//            speechRecognitionHelper.startListening()
            val streamingSpeechRecognition = StreamingSpeechRecognition(
                languageCode = "en-US",
                activity = this,
                transcriptionCallback = { transcription ->
                    if (transcription.isNotEmpty()){
                        processUserQuery(transcription)
                        Log.d("SpeechRecognizer", "Transcription received at START NAVIGATIONS: $transcription")
                    } else {
                        textToSpeech.speakResponse("Unfortunately, I did not get your query. Please try again.")
                        displayMessage("Query is empty.")
                    }
                }
            )

            // Start streaming when the button is clicked
            streamingSpeechRecognition.startStreaming()
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
                navigator.setAudioGuidance(AudioGuidance.VOICE_ALERTS_AND_GUIDANCE)
                isAudioGuidanceEnabled = true
                displayMessage("Audio guidance will be enabled in a moment")
                Log.e("StartNavigationsActivity", "audio guidance at method: $isAudioGuidanceEnabled" )
            } else {
                // Disable voice guidance when switch is off
                with(sharedPreferences.edit()) {
                    putBoolean(SettingsKeys.KEY_SET_AUDIO, false)
                    apply()
                }
                navigator.setAudioGuidance(AudioGuidance.SILENT)
                isAudioGuidanceEnabled = false
                displayMessage("Audio guidance will be disabled in a moment")
                Log.e("StartNavigationsActivity", "audio guidance at method: $isAudioGuidanceEnabled" )
            }
        }


        binding.continueToNextDestinationLayout.setOnClickListener {
           if (NUM_STOPS != 1) {
               handleNextStop()
               NUM_STOPS -= 1
               placeIds.removeAt(0)
               displayMessage("NUM STOPS VALUE: $NUM_STOPS")
               Log.e("StartNavigationsActivity", "PlaceIDs size at confirm: ${placeIds.size}" )
               fetchPlaceDetailsForCard(NUM_STOPS)
           }
            binding.continueToNextDestinationLayout.visibility = if (NUM_STOPS == 1) View.GONE else View.VISIBLE
            binding.spacer1.visibility = if (NUM_STOPS == 1 ) View.GONE else View.VISIBLE
        }

        binding.viewZoomedOutLayout.setOnClickListener {
            navFragment.showRouteOverview()
        }
    }

    private fun stopConfig(placeId: String) {
        placeIds.add(0, placeId)
        IS_ADDING_STOP = true
        NUM_STOPS += 1
        clearMarkers()
        binding.continueToNextDestinationLayout.visibility = if (NUM_STOPS == 1) View.GONE else View.VISIBLE
        binding.spacer1.visibility = if (NUM_STOPS == 1 ) View.GONE else View.VISIBLE
        Log.e(TAG, "Place ID $placeId added to placeIds at index 0")
        Log.e(TAG, "IS ADDING STOP VALUE: $IS_ADDING_STOP")


    }

    private fun navigationConfig(){
        navigator.stopGuidance()
        navigator.removeArrivalListener(mArrivalListener)
        navigator.removeRemainingTimeOrDistanceChangedListener(mRemainingTimeOrDistanceChangedListener)
        navigateWithMultipleStops("NO ROUTE TOKEN", placeIds, travelMode)
        fetchPlaceDetailsForCard(NUM_STOPS)
    }

    private fun replacePlaceIds() {
        // Output the final rearranged waypoints after all iterations are complete
        Log.e("StartNavigationsActivity", "Rearranged Waypoints: $rearrangedDestinations")
        placeIds.clear()
        placeIds.addAll(rearrangedDestinations)
        Log.e("StartNavigationsActivity", "PlaceIDS: $placeIds")

        Log.e("StartNavigationsActivity", "IS NEED OPTIMIZATION: $isNeedOptimization")

    }

    private fun recalculateWaypointOrder(onCompletion: () -> Unit) {
        // Initialize lists for origins and destinations
        val waypointOrigins = mutableListOf<RouteMatrixOrigin>()
        val waypointDestinations = mutableListOf<RouteMatrixDestination>()
        rearrangedDestinations.clear()

        // Get the current location
        getCurrentLocation { latLng ->
            latLng?.let {
                // Convert LatLng to WaypointMatrix
                val currentWaypoint = WaypointMatrix(location = LocationMatrix(latLng = LatLngMatrix(it.latitude, it.longitude)))

                // Create a RouteMatrixOrigin using the current waypoint
                var currentOrigin = RouteMatrixOrigin(waypoint = currentWaypoint)
                waypointOrigins.add(currentOrigin)

                // Create a mutable copy of placeIds to remove as waypoints are added
                val remainingWaypoints = placeIds.toMutableList()

                // Log the added origin for debugging
                Log.e("WaypointOrder", "Current location added as origin: Lat: ${currentWaypoint.location?.latLng?.latitude}, Lng: ${currentWaypoint.location?.latLng?.longitude}")

                // Use Coroutine to perform the rearrangement asynchronously
                CoroutineScope(Dispatchers.IO).launch {
                    while (remainingWaypoints.isNotEmpty()) {
                        var closestDestination: String? = null
                        var minDistance = Double.MAX_VALUE

                        // Prepare destinations for the remaining waypoints
                        waypointDestinations.clear()
                        for (placeId in remainingWaypoints) {
                            // Create a WaypointMatrix using the Place ID
                            val destinationWaypoint = WaypointMatrix(placeId = placeId)
                            waypointDestinations.add(RouteMatrixDestination(waypoint = destinationWaypoint))
                        }

                        // Create the RouteMatrixRequest
                        val routeMatrixRequest = RouteMatrixRequest(
                            origins = waypointOrigins,
                            destinations = waypointDestinations,
                            travelMode = travelMode,
                            routingPreference = "TRAFFIC_AWARE"
                        )

                        // Make the API call asynchronously using Retrofit
                        val apiKey = BuildConfig.MAPS_API_KEY

                        // Ensure computeRouteMatrix is a suspend function in your Retrofit service
                        try {
                            val routesMatrixResponse = RoutesMatrixClientSingleton.instance.computeRouteMatrix(apiKey, request = routeMatrixRequest)

                            // Find the nearest waypoint
                            routesMatrixResponse.forEach { routeMatrixElement ->
                                if (routeMatrixElement.distanceMeters < minDistance) {
                                    minDistance = routeMatrixElement.distanceMeters.toDouble()
                                    closestDestination = remainingWaypoints[routeMatrixElement.destinationIndex]
                                }
                            }

                            // Add the closest waypoint to the rearranged destinations
                            closestDestination?.let { nearestWaypoint ->
                                rearrangedDestinations.add(nearestWaypoint)
                                remainingWaypoints.remove(nearestWaypoint)

                                // Log the nearest waypoint
                                Log.e("WaypointOrder", "Closest Waypoint: $nearestWaypoint, Distance: $minDistance")

                                // Update the current origin to be the closest destination
                                val newOriginWaypoint = WaypointMatrix(placeId = nearestWaypoint)
                                currentOrigin = RouteMatrixOrigin(waypoint = newOriginWaypoint)

                                // Reset origins for the next iteration
                                waypointOrigins.clear()
                                waypointOrigins.add(currentOrigin)
                            }
                        } catch (e: Exception) {
                            Log.e("WaypointOrder", "Error: ${e.message}")
                            onCompletion()
                        }
                    }
                    // After rearrangement is complete, update the isNeedOptimization variable
                    Log.e("WaypointOrder", "Full rearranged destinations: $rearrangedDestinations")
                    Log.e("WaypointOrder", "Placeid before: $placeIds")

                    // Check if the rearranged destinations differ from the original placeIds
                    isNeedOptimization = placeIds != rearrangedDestinations
                    Log.e("StartNavigationsActivity", "Rearranged list at recalculate: $rearrangedDestinations")
                    Log.e("StartNavigationsActivity", "Placeids at recalculate: $placeIds")
                    Log.e("StartNavigationsActivity", "Are they the same: ${placeIds == rearrangedDestinations}")
                    Log.e("StartNavigationsActivity", "Recalculate method waypoint is need optmization: $isNeedOptimization")

                    withContext(Dispatchers.Main) {
                        onCompletion() // Call onCompletion to signal that rearrangement is done
                    }
                }
            } ?: run {
                Log.e("WaypointOrder", "Failed to obtain current location.")
            }
        }
    }

    private fun getCurrentLocation(callback: (LatLng?) -> Unit) {
        // Check if the location permission is granted
        if (ContextCompat.checkSelfPermission(
                this,  // Use 'this' if inside an Activity or Fragment
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
                        Log.e("WaypointOrder", "Location is null.")
                        callback(null) // Handle case where location is null
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("WaypointOrder", "Failed to get location", exception)
                    callback(null) // Return null on failure
                }
        } else {
            // Permission is not granted, request it
            ActivityCompat.requestPermissions(
                this, // Ensure that 'this' is an Activity
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1
            )
        }
    }

    private fun fetchPlaceDetailsForCard(stop: Int){
        fetchPlaceDetailsFromAPI(placeIds[0]) { place ->
            if (place != null) {
                binding.tvPlaceName.text = place.name
                binding.tvPlaceAddress.text = place.address
                binding.tvRemainingStop.text = "Remaining destination(s): ${stop}"
            }

        }
    }

    private fun clearMarkers() {
        // Remove markers from the map
        binding.assistantButton.visibility = View.GONE
        binding.clearMarkers.visibility = View.VISIBLE
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
                        googleMap.setOnPoiClickListener(this@StartNavigationsActivity)

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

        val pendingRoute = if (IS_ADDING_STOP == true || routeToken == "NO ROUTE TOKEN") {
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

                    placeDetailsRelatedContextuals.isPlaceOpenNow(placeIds[0]) {
                        placeDetailsRelatedContextuals.showClosedPlaceDialog(
                            onCancel = {
                                Toast.makeText(this, "Navigation terminated", Toast.LENGTH_SHORT).show()
                                cleanup()
                                Handler(Looper.getMainLooper()).postDelayed({
                                    ActivityNavigationUtils.navigateToActivity(this, PlacesActivity::class.java, true)
                                }, 500)
                            },
                            onProceed = {
                                isPlaceOpenNowClassifierFinished = true
                                displayMessage("Route successfully calculated with multiple stops!")
                                if (isAudioGuidanceEnabled) {
                                    navigator.setAudioGuidance(AudioGuidance.VOICE_ALERTS_AND_GUIDANCE)
                                    displayMessage("AUDIO GUIDANCE: $isAudioGuidanceEnabled")
                                } else
                                    displayMessage("AUDIO GUIDANCE: $isAudioGuidanceEnabled")


                                Log.e(TAG, "isPlaceOpenNowClassifierFinished: $isPlaceOpenNowClassifierFinished")
                                if (isPlaceOpenNowClassifierFinished) {
                                    displayRainCheckBottomSheet()
                                    Log.e(TAG, "THE RAIN CHECK WAS EXECUTED")
                                    hasExecutedSuggestions = true
                                }

                                registerNavigationListeners()

                                if (isSimulated) {
                                    navigator.simulator.simulateLocationsAlongExistingRoute(
                                        SimulationOptions().speedMultiplier(5F))
                                }

                                navigator.startGuidance()
                                startTrip()
                            }
                        )
                    }
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
                // Determine which stop counter to use
                val stopsCount = if (IS_ADDING_STOP) NUM_STOPS else DEFAULT_STOPS

                // Proceed only if there are more stops remaining
                if (stopsCount > 1) {
                    handleNextStop()  // Extract repeated logic into a function

                    // Update stop counter
                    if (IS_ADDING_STOP) {
                        NUM_STOPS -= 1
                    } else {
                        DEFAULT_STOPS -= 1
                    }
                    placeIds.removeAt(0)
                    fetchPlaceDetailsForCard(stopsCount - 1)
                }

                // Update the UI based on the remaining stops
                val stopsRemaining = if (IS_ADDING_STOP) NUM_STOPS else DEFAULT_STOPS
                binding.continueToNextDestinationLayout.visibility = if (stopsRemaining == 1) View.GONE else View.VISIBLE
                binding.spacer1.visibility = if (stopsRemaining == 1) View.GONE else View.VISIBLE
            }


        }

        // Listens for arrival at a waypoint.
        navigator.addArrivalListener(mArrivalListener)


        // Listener for remaining time or distance changes
        val totalRouteDistanceInMeters = navigator.currentTimeAndDistance.meters // Assume you retrieve this on route setup
        val fuelStopTriggerDistance = 1000

        val mealStopTriggerDistance = 500

        mRemainingTimeOrDistanceChangedListener = RemainingTimeOrDistanceChangedListener {
            val remainingDistanceInMeters = navigator.currentTimeAndDistance.meters
            val traveledDistance = totalRouteDistanceInMeters - remainingDistanceInMeters
            Log.e("Contextuals", "TOTAL DISTANCE: $totalRouteDistanceInMeters")
            Log.e("Contextuals", "REMAINING DISTANCE: $remainingDistanceInMeters")
            Log.e("Contextuals", "TRAVELED DISTANCE: $traveledDistance")

            if (totalRouteDistanceInMeters > 8000){
                if (!hasFuelStopDisplayed && traveledDistance >= fuelStopTriggerDistance) {
                    Log.e("Contextuals", "fuel stop executed")

                    hasFuelStopDisplayed = true
                    fuelStopsRecommendation.showPlaceDialog {
                        displayFuelStops()
                    }
                }
            }

            if (!hasMealDialogDisplayed && totalRouteDistanceInMeters >= 5000 && traveledDistance >= mealStopTriggerDistance) {
                // Silence the navigation audio guidance
                navigator.setAudioGuidance(AudioGuidance.SILENT)

                // Display the meal place recommendation dialog
                mealPlaceRecommender.showPlaceDialogIfNeeded(
                    onProceedClicked = {
                        onDialogProceed()
                    }
                )
                navigator.setAudioGuidance(AudioGuidance.VOICE_ALERTS_AND_GUIDANCE)
                hasMealDialogDisplayed = true
            }


            // Update UI as needed
            runOnUiThread {
                binding.tvJourneyTime.text = formatTime(navigator.currentTimeAndDistance.seconds)
                binding.tvTotalKilometers.text = String.format("Distance: %.1f km", remainingDistanceInMeters / 1000.0)
                binding.tvEta.text = "ETA: ${calculateETA(navigator.currentTimeAndDistance.seconds)}"
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
            .addOnFailureListener {
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

    override fun onDestroy() {
        super.onDestroy()
        cleanup()
    }

    override fun onPoiClick(p0: PointOfInterest?) {
        if (p0 != null) {
            showAddStopBottomSheet(null, p0.placeId)
        }
    }


}

