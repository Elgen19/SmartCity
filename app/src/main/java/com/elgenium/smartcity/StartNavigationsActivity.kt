package com.elgenium.smartcity

import PlacesClientSingleton
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.Html
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.elgenium.smartcity.contextuals.FuelStopsRecommendation
import com.elgenium.smartcity.contextuals.MealContextuals
import com.elgenium.smartcity.contextuals.PlaceOpeningHoursContextuals
import com.elgenium.smartcity.contextuals.RainLikelihoodCalculator
import com.elgenium.smartcity.databinding.ActivityStartNavigationsBinding
import com.elgenium.smartcity.databinding.BottomSheetAddStopBinding
import com.elgenium.smartcity.databinding.BottomSheetEditFuelLevelBinding
import com.elgenium.smartcity.databinding.BottomSheetPlaceRecommendationListBinding
import com.elgenium.smartcity.databinding.BottomSheetSinglePlaceRecommendationBinding
import com.elgenium.smartcity.databinding.DialogActivitySuggestionsBinding
import com.elgenium.smartcity.databinding.DialogRecomputeStopsBinding
import com.elgenium.smartcity.databinding.DialogTerminateNavigationBinding
import com.elgenium.smartcity.databinding.DialogTripRecapBinding
import com.elgenium.smartcity.geofences.GeofenceManager
import com.elgenium.smartcity.intelligence.AIProcessor
import com.elgenium.smartcity.intelligence.FuelSetupManager
import com.elgenium.smartcity.models.ActivityDetails
import com.elgenium.smartcity.network.PlaceDistanceService
import com.elgenium.smartcity.network_reponses.PlaceDistanceResponse
import com.elgenium.smartcity.recyclerview_adapter.PlaceRecommendationsAdapter
import com.elgenium.smartcity.routes_network_request.LatLngMatrix
import com.elgenium.smartcity.routes_network_request.LocationMatrix
import com.elgenium.smartcity.routes_network_request.RouteMatrixDestination
import com.elgenium.smartcity.routes_network_request.RouteMatrixOrigin
import com.elgenium.smartcity.routes_network_request.RouteMatrixRequest
import com.elgenium.smartcity.routes_network_request.WaypointMatrix
import com.elgenium.smartcity.routing.RouteFetcher
import com.elgenium.smartcity.shared_preferences_keys.SettingsKeys
import com.elgenium.smartcity.singletons.ActivityNavigationUtils
import com.elgenium.smartcity.singletons.NavigationBarColorCustomizerHelper
import com.elgenium.smartcity.singletons.RoutesMatrixClientSingleton
import com.elgenium.smartcity.speech.StreamingSpeechRecognition
import com.elgenium.smartcity.speech.TextToSpeechHelper
import com.google.ai.client.generativeai.type.ResponseStoppedException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.CameraPerspective
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
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
import com.google.android.material.chip.Chip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale


class StartNavigationsActivity : AppCompatActivity(), GoogleMap.OnPoiClickListener{

    private var totalRouteDistanceInMeters = 0
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
    private lateinit var textToSpeech: TextToSpeechHelper
    private lateinit var binding: ActivityStartNavigationsBinding
    private lateinit var aiProcessor: AIProcessor
    private var mMap: GoogleMap? = null
    private val markersList = mutableListOf<Marker>()
    private val markerPlaceIdMap = HashMap<Marker, String>()
    private var IS_ADDING_STOP = false
    private var NUM_STOPS = 1
    private var hasMealPlaceInDestination = false
    private lateinit var sharedPreferences: SharedPreferences
    private var isAudioGuidanceEnabled = true
    private var isMultiplePlaceEnabled = false
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
    private lateinit var activityList: ArrayList<ActivityDetails>
    private var bottomSheetDialog: BottomSheetDialog? = null
    private var placesInfo: List<Map<String, Any>> = listOf()
    private lateinit var geofenceManager: GeofenceManager



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStartNavigationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        sharedPreferences = getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
        isAudioGuidanceEnabled = sharedPreferences.getBoolean("set_audio", true)
        isMultiplePlaceEnabled = sharedPreferences.getBoolean("multiple_place", false)
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
        activityList = intent.getSerializableExtra("ACTIVITY_LIST") as? ArrayList<ActivityDetails>
            ?: arrayListOf()
        DEFAULT_STOPS = placeIds.size

        initializer()

        if (DEFAULT_STOPS != 0)
            NUM_STOPS = DEFAULT_STOPS

        Log.e("StartNavigationsActivity", "audio guidance at oncreate: $isAudioGuidanceEnabled")
        Log.e("StartNavigationsActivity", "traffic overlay at oncreate: $isTrafficOverlayEnabled")
        Log.e("StartNavigationsActivity", "Default stops is: $DEFAULT_STOPS")
        Log.e("StartNavigationsActivity", "Placeids size is: ${placeIds.size}")
        Log.e("StartNavigationsActivity", "TRAVEL MODE AT NAVIGATION: $travelMode")
        Log.e("StartNavigationsActivity", "ROUTE TOKEN AT NAVIGATION: $routeToken")


        showNavigationOptionsBottomSheet()
        if (routeToken == null)
            routeToken = "NO ROUTE TOKEN"

        requestLocationPermissions(routeToken!!, placeIds, travelMode)
        Log.e("WaypointOrder", "PLACEID AT ONCREATE AFTER CHANGES: $placeIds")


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

        checkPlaceTypes()

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
                withContext(Dispatchers.Main) {
                    showLoadingDialog(true)
                }
                allLatLngs.clear()
                navigator.routeSegments.forEach { segment ->
                    allLatLngs.addAll(segment.latLngs)
                }

                aiProcessor.performSearch(query, allLatLngs)
                placesInfo = aiProcessor.extractPlaceInfo()
                Log.e("PLACE_INFO", "$placesInfo")


                if (aiProcessor.hasPlaceIdAndIsValidPlace()) {
                    textToSpeech.speakResponse("Here's what I've got.")
                    withContext(Dispatchers.Main) {
                        showLoadingDialog(false)
                    }

                   if (isMultiplePlaceEnabled)
                       showPlaceRecommendationsBottomSheet(
                           "Nearby places found",
                           "Here's what I've found within your route")
                    else
                        showSinglePlaceRecommendation()

                } else {
                    withContext(Dispatchers.Main) {
                        showLoadingDialog(false)
                    }

                    aiProcessor.setOnPlaceSelectedCallback { selectedPlaceId ->
                        addStopDirectly(false, selectedPlaceId)
                        Log.e("OFF ROUTE PLACE", "PLACE ID IS: $selectedPlaceId")
                    }


                    textToSpeech.speakResponse("Unfortunately, I couldn't find any information related to your query, '$query.' Please consider trying alternative keywords for your search.")
                }
            } catch (e: ResponseStoppedException) {
                Log.e("AIProcessor", "Response generation stopped due to safety concerns: ${e.message}")
                textToSpeech.speakResponse("Unfortunately, I cannot find what you are looking for.")
                withContext(Dispatchers.Main) {
                    showLoadingDialog(false)
                }
            } catch (e: Exception) {
                Log.e("AIProcessor", "Error processing query: ${e.message}", e)
                textToSpeech.speakResponse("Unfortunately, I cannot find what you are looking for right now.")
                withContext(Dispatchers.Main) {
                    showLoadingDialog(false)
                }
            }
        }
    }

    private fun showPlaceRecommendationsBottomSheet(
        title: String,
        body: String
    ) {
        // Create a BottomSheetDialog
        val dialog = BottomSheetDialog(this)
        val sheetBinding = BottomSheetPlaceRecommendationListBinding.inflate(LayoutInflater.from(this))

        showLoadingDialog(false)

        sheetBinding.titleLabel.text = title
        sheetBinding.supportingLabel.text = body

        // Original unfiltered list
        val originalPlacesInfo = placesInfo.toMutableList()

        Log.e("ORIGINAL_LIST", "Displaying places and their ratings:")
        originalPlacesInfo.forEach { place ->
            Log.e("PLACE_INFO", "$place")}


        // Function to apply filters and update the RecyclerView
        fun applyFilter(filterType: String) {
            val filteredList = when (filterType) {
                "ShowAll" -> originalPlacesInfo
                "Nearest" -> {
                    // Find the single place with the lowest distance
                    listOfNotNull(originalPlacesInfo.minByOrNull {
                        (it["distanceValue"] as? Double) ?: Double.MAX_VALUE
                    })
                }
                "Popular" -> {
                    val filteredPlaces = originalPlacesInfo.filter {
                        // Only consider places with non-null ratings
                        val rating = it["rating"] as? Double
                        rating != null && rating >= 4.0 // Include only places with ratings >= 4.0
                    }

                    Log.e("FILTERED_LIST", "Filtered places with rating >= 4.0: $filteredPlaces")
                    filteredPlaces
                }




                else -> originalPlacesInfo
            }

            Log.e("FILTERED_LIST", "Filtered places: $filteredList")

            val adapter = PlaceRecommendationsAdapter(filteredList) { selectedPlace ->
                val name = selectedPlace["name"] as? String ?: "Unknown"
                val placeId = selectedPlace["placeId"] as? String ?: "No ID"
                Log.e("TAG", "Place clicked: Name=$name, PlaceId=$placeId")

                plotSingleMarker(title, body, selectedPlace)
                dialog.dismiss()
            }

            sheetBinding.placeRecommendationsRecyclerview.layoutManager = LinearLayoutManager(this)
            sheetBinding.placeRecommendationsRecyclerview.adapter = adapter
        }

        sheetBinding.chipShowAll.chipBackgroundColor =
            ColorStateList.valueOf(ContextCompat.getColor(this, R.color.brand_color))

        // Set up individual click listeners for each chip
        sheetBinding.chipShowAll.setOnClickListener {
            applyFilter("ShowAll")
            sheetBinding.chipShowAll.chipBackgroundColor =
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.brand_color))
            resetChipBackground(sheetBinding.chipNearest, sheetBinding.chipPopular)
        }

        sheetBinding.chipNearest.setOnClickListener {
            applyFilter("Nearest")
            sheetBinding.chipNearest.chipBackgroundColor =
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.brand_color))
            resetChipBackground(sheetBinding.chipShowAll, sheetBinding.chipPopular)
        }

        sheetBinding.chipPopular.setOnClickListener {
            applyFilter("Popular")
            sheetBinding.chipPopular.chipBackgroundColor =
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.brand_color))
            resetChipBackground(sheetBinding.chipShowAll, sheetBinding.chipNearest)
        }

        // Initialize the RecyclerView with the full list
        applyFilter("ShowAll")

        // Close button listener
        sheetBinding.btnClose.setOnClickListener {
            dialog.dismiss()
            placesInfo = emptyList()
            clearMarkers()
            binding.clearMarkers.visibility = View.GONE
            binding.assistantButton.visibility = View.VISIBLE
        }

        // Bottom sheet behavior customization
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheet = (dialogInterface as BottomSheetDialog).findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.isHideable = false // Prevent hiding when swiped down
                behavior.peekHeight = (resources.displayMetrics.heightPixels * 0.40).toInt() // Set peek height to 40% of screen height
            }
        }

        // Calculate distances and ratings if needed before showing the list
        getCurrentLocation { currentLocation ->
            if (currentLocation != null) {
                val placesWithDistances = placesInfo.toMutableList()
                var distancesCalculated = 0

                placesWithDistances.forEachIndexed { index, placeInfo ->
                    val placeLatLng = placeInfo["latLng"] as? LatLng
                    if (placeLatLng != null) {
                        checkPlaceDistance(currentLocation, placeLatLng) { distance ->
                            val distanceValue = distance.replace(" km", "").toDoubleOrNull() ?: Double.MAX_VALUE
                            val rating = (placeInfo["rating"] as? Double)?.toDouble() ?: 0.0

                            placesWithDistances[index] = placeInfo.toMutableMap().apply {
                                this["distanceValue"] = distanceValue
                                this["rating"] = rating
                                this["distance"] = distance
                            }

                            distancesCalculated++

                            // Once all distances are calculated, update the original list
                            if (distancesCalculated == placesInfo.size) {
                                originalPlacesInfo.clear()
                                originalPlacesInfo.addAll(placesWithDistances)
                                applyFilter("ShowAll")
                            }
                        }
                    }
                }
            } else {
                Log.e("TAG", "Unable to get current location")
            }
        }

        // Set the bottom sheet content and show the dialog
        dialog.setContentView(sheetBinding.root)
        dialog.show()
    }

    private fun resetChipBackground(vararg chips: Chip) {
        chips.forEach { chip ->
            chip.chipBackgroundColor =
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary_color))
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

            // Step 5: Plot markers on the map using the plotMarkers function
            if (isMultiplePlaceEnabled)
                showPlaceRecommendationsBottomSheet(
                    "Fuel stops found",
                    "Check out these fuel stops within your route.")
            else
                showSinglePlaceRecommendation()
        }
    }

    private fun onDialogProceed() {
        showLoadingDialog(true)

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
             placesInfo = places.mapNotNull { place ->
                place.latLng?.let {
                    mapOf(
                        "latLng" to it,
                        "name" to place.name.orEmpty(),
                        "address" to place.address.orEmpty(),
                        "placeId" to place.id.orEmpty(),
                        "rating" to place.rating
                    )
                }
            }
            Log.e(TAG, "PlaceInfo: $placesInfo")

            // Step 5: Plot markers on the map using the `plotMarkers` function
            if (isMultiplePlaceEnabled)
                showPlaceRecommendationsBottomSheet(
                    "Meal places found",
                    "Here are the meal places within the set route.")
            else
                showSinglePlaceRecommendation()
        }
    }

    @SuppressLint("PotentialBehaviorOverride")
    private fun plotSingleMarker(title: String, message: String, placeInfo: Map<String, Any>) {
        // Clear existing markers from the map
        clearMarkers()

        // Extract the latLng of the selected place
        val latLng = placeInfo["latLng"] as LatLng
        val name = placeInfo["name"] as String
        val placeId = placeInfo["placeId"] as String

        // Add a marker for the selected place
        val markerOptions = MarkerOptions()
            .position(latLng)
            .title(name)
            .alpha(1f)
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN)) // Visible color

        // Add the marker to the map
        val marker = mMap?.addMarker(markerOptions)
        if (marker != null) {
            markersList.add(marker) // Keep track of the marker
            marker.showInfoWindow()
            markerPlaceIdMap[marker] = placeId
        }

        // Display the route overview
       navFragment.showRouteOverview()

        // Handle marker click event
        mMap?.setOnMarkerClickListener { marker2 ->
            val clickedPlaceId = markerPlaceIdMap[marker2] ?: "NO PLACE ID"
            Log.e(TAG, "Marker clicked: ${marker2.title}, PlaceId: $clickedPlaceId")
            // Show the bottom sheet for the selected place
            showAddStopBottomSheet(clickedPlaceId, title, message, placeInfo)
            true // Return true to indicate the click was handled
        }
    }

    private fun showAddStopBottomSheet(
        placeId: String,
        title: String?,
        body: String?,
        placeInfo: Map<String, Any>?
    ) {
        val bottomSheetView = BottomSheetAddStopBinding.inflate(layoutInflater)
        val bottomSheetDialog = BottomSheetDialog(this)

        // Set the content view for the bottom sheet
        bottomSheetDialog.setContentView(bottomSheetView.root)

        // Remove the dim overlay
        bottomSheetDialog.window?.setDimAmount(0f)

        // Prevent the dialog from closing when swiping down
        bottomSheetDialog.setOnShowListener { dialogInterface ->
            val bottomSheet = (dialogInterface as BottomSheetDialog).findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.isHideable = false // Prevent hiding on swipe down
                behavior.state = BottomSheetBehavior.STATE_EXPANDED // Ensure it's fully expanded
            }
        }

        // Make the dialog non-modal to allow interaction with the parent layout
        bottomSheetDialog.window?.apply {
            setFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            )
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }

        // Prevent the dialog from closing when touching outside
        bottomSheetDialog.setCancelable(false)
        bottomSheetDialog.setCanceledOnTouchOutside(false)

        fetchPlaceDetailsFromAPI(placeId) { place ->
            if (place != null) {
                bottomSheetView.textViewPlaceName.text = place.name
                bottomSheetView.textViewPlaceAddress.text = place.address
            }
        }


        // Handle Add Stop button click
        // Handle Add Stop button click
        bottomSheetView.buttonAddStop.setOnClickListener {

            // Disable the button to prevent multiple clicks while processing
            bottomSheetView.buttonAddStop.isEnabled = false

            // Fetch the place details and handle stop location asynchronously
            fetchPlaceDetailsFromAPI(placeId) { place ->
                if (place != null) {
                    place.latLng?.let { stopLatLng ->

                        // Call the delay calculation and handle logic
                        calculateAdHocTaskDelay(stopLatLng, placeId) { hasDelay ->
                            if (hasDelay) {
                                // Handle delay
                                Log.d("AdHocTaskDelay", "There is a delay!")
                            } else {
                                // Handle no delay
                                if (activityList.isEmpty())
                                    addStopDirectly(true, placeId)
                                else
                                    addStopDirectly(false, placeId)

                                placesInfo = emptyList()
                                Log.d("AdHocTaskDelay", "No delay detected.")
                            }

                            // Dismiss the bottom sheet after processing is complete
                            bottomSheetDialog.dismiss()

                            // Re-enable the button after the task is done
                            bottomSheetView.buttonAddStop.isEnabled = true
                        }
                    } ?: run {
                        // Handle the case where latLng is null
                        Log.e("AdHocTaskDelay", "Failed to retrieve latLng from place")
                        bottomSheetDialog.dismiss() // Dismiss the dialog
                        bottomSheetView.buttonAddStop.isEnabled = true // Re-enable button if no latLng
                    }
                } else {
                    Log.e("AdHocTaskDelay", "Failed to fetch place details.")
                    bottomSheetDialog.dismiss() // Dismiss the dialog
                    bottomSheetView.buttonAddStop.isEnabled = true // Re-enable button if place is null
                }
            }
        }



        // Handle Cancel button click
        bottomSheetView.buttonCancel.setOnClickListener {
            bottomSheetDialog.dismiss()
            clearMarkers()
            if (title != null && body != null && placeInfo != null){
                if (isMultiplePlaceEnabled)
                    showPlaceRecommendationsBottomSheet(
                        "Fuel stops found",
                        "Check out these fuel stops within your route.")
                else
                    showSinglePlaceRecommendation()
            }

        }

        // Show the BottomSheetDialog
        bottomSheetDialog.show()
    }


    private fun addStopDirectly(recalculateStops: Boolean, placeId: String) {
        if (hasAlreadyArrivedAtFinalDestination) {
            NUM_STOPS = 0
            placeIds.removeAt(0)
            if (activityList.isNotEmpty())
                activityList.removeAt(0)
        }

        if (DEFAULT_STOPS > 0) {
            NUM_STOPS = DEFAULT_STOPS
            DEFAULT_STOPS = 0
        }
        stopConfig(placeId)
        Log.e("OFF ROUTE PLACE", "RECALCULATE STOPS: $recalculateStops")


        binding.assistantButton.visibility = View.VISIBLE
        binding.clearMarkers.visibility = View.GONE

        // Call recalculateWaypointOrder and handle completion
        if (recalculateStops){
            recalculateWaypointOrder {
                Log.e("StartNavigationsActivity", "Rearranged list: $rearrangedDestinations")
                Log.e("StartNavigationsActivity", "PlaceIds: $placeIds")
                Log.e("StartNavigationsActivity", "Is need of optimization: ${(rearrangedDestinations != placeIds)}")

                if (placeIds != rearrangedDestinations) {
                    showRecomputeStopsDialog() // Show dialog if optimization is needed
                    Log.e("StartNavigationsActivity", "Is need optimization should be true")
                } else {
                    navigationConfig() // Proceed with navigation if no optimization is needed
                    Log.e("StartNavigationsActivity", "Is need optimization should be false")
                }
            }
        } else
            navigationConfig()
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

    private fun showSinglePlaceRecommendation() {
        val dialog = BottomSheetDialog(this)
        val sheetBinding = BottomSheetSinglePlaceRecommendationBinding.inflate(LayoutInflater.from(this))

        showLoadingDialog(false)

        dialog.setOnShowListener { dialogInterface ->
            val bottomSheet = (dialogInterface as BottomSheetDialog).findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.isHideable = false // Prevent hiding when swiped down
                behavior.peekHeight = (resources.displayMetrics.heightPixels * 0.40).toInt() // Set peek height to 40% of the screen height
            }
        }

        sheetBinding.btnClose.setOnClickListener {
            dialog.dismiss()
            placesInfo = emptyList()
            clearMarkers()
            binding.clearMarkers.visibility = View.GONE
            binding.assistantButton.visibility = View.VISIBLE
        }

        // Get the user's current location
        getCurrentLocation { currentLocation ->
            if (currentLocation != null) {
                var nearestPlace: Map<String, Any>? = null
                var shortestDistance = Double.MAX_VALUE
                var shortestDistanceFormatted = ""

                val remainingPlaces = placesInfo.size
                var processedPlaces = 0

                placesInfo.forEach { placeInfo ->
                    val placeLatLng = placeInfo["latLng"] as? LatLng
                    if (placeLatLng != null) {
                        checkPlaceDistance(currentLocation, placeLatLng) { distance ->
                            val distanceValue = distance.replace(" km", "").toDoubleOrNull() ?: Double.MAX_VALUE

                            // Update if this place is the nearest so far
                            if (distanceValue < shortestDistance) {
                                shortestDistance = distanceValue
                                shortestDistanceFormatted = distance
                                nearestPlace = placeInfo
                            }

                            processedPlaces++
                            if (processedPlaces == remainingPlaces) {
                                // Display the nearest place in the CardView
                                nearestPlace?.let { place ->
                                    sheetBinding.tvPlaceName.text = place["name"] as? String ?: "Unknown"
                                    sheetBinding.tvPlaceAddress.text = place["address"] as? String ?: "No address available"
                                    sheetBinding.tvDistance.text = shortestDistanceFormatted
                                    sheetBinding.tvRatings.text = place["rating"]?.toString() ?: "No rating available"


                                    sheetBinding.nearestPlaceCard.setOnClickListener{
                                        dialog.dismiss()
                                        plotSingleMarker("", "", place)
                                    }

                                }
                            }
                        }
                    }



                }


            } else {
                Log.e("TAG", "Unable to get current location")
            }
        }

        // Set the bottom sheet content and show the dialog
        dialog.setContentView(sheetBinding.root)
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
        binding.multiplePlaceSwitch.isChecked = isMultiplePlaceEnabled

        binding.editFuelInfo.setOnClickListener {
            showEditFuelLevelBottomSheet()
        }

        // Set up click listeners for the actions
        binding.assistantButton.setOnClickListener {
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

        binding.multiplePlaceSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                with(sharedPreferences.edit()) {
                    putBoolean(SettingsKeys.KEY_MULTIPLE_PLACE, true)
                    apply()
                }
                isMultiplePlaceEnabled = true
                displayMessage("Setting multiple place recommendations")
            } else {
                with(sharedPreferences.edit()) {
                    putBoolean(SettingsKeys.KEY_MULTIPLE_PLACE, false)
                    apply()
                }
                isMultiplePlaceEnabled = false
                displayMessage("Setting single place recommendation")
            }
        }


        binding.continueToNextDestinationLayout.setOnClickListener {
           if (NUM_STOPS != 1) {
               handleNextStop()
               NUM_STOPS -= 1
               placeIds.removeAt(0)
               if (activityList.isNotEmpty())
                   activityList.removeAt(0)
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

    private fun showLoadingDialog(isShown: Boolean) {
        // Ensure you are using a persistent dialog object
        if (bottomSheetDialog == null) {
            // Create the dialog only if it's null (i.e., if it's the first time)
            bottomSheetDialog = BottomSheetDialog(this)
            val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_loading_interface, null)
            bottomSheetDialog?.setContentView(bottomSheetView)
        }

        // Show or dismiss the dialog based on the isShown argument
        if (isShown) {
            bottomSheetDialog?.show()
        } else {
            bottomSheetDialog?.dismiss()
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
        checkPlaceTypes()
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

    private fun checkPlaceTypes() {
        hasMealPlaceInDestination = false
        var pendingRequests = placeIds.size

        for (placeId in placeIds) {
            fetchPlaceDetailsFromAPI(placeId) { place ->
                place?.let {
                    val foodRelatedKeywords = listOf(
                        "food", "food_store", "restaurant", "cafe", "bar",
                        "convenience_store", "shopping_mall"
                    )

                    if (it.placeTypes?.any { type ->
                            foodRelatedKeywords.any { keyword -> type.contains(keyword, ignoreCase = true) }
                        } == true) {
                        hasMealPlaceInDestination = true
                        Log.e("MEAL_PLACE_CHECK", "Meal place found: ${it.name}")
                    }

                    Log.e("MEAL_PLACE_CHECK", "Checked place: ${it.name}, Result: $hasMealPlaceInDestination")
                }

                // Decrement the counter and log final result if all requests are complete
                pendingRequests--
                if (pendingRequests == 0) {
                    Log.e("MEAL_PLACE_CHECK", "hasMealPlaceInDestination is: $hasMealPlaceInDestination")
                }
            }
        }
    }

    private fun fetchPlaceDetailsForCard(stop: Int) {
        // Fetch place details from the API
        fetchPlaceDetailsFromAPI(placeIds[0]) { place ->
            place?.let {
                binding.tvPlaceName.text = it.name
                binding.tvPlaceAddress.text = it.address
                binding.tvRemainingStop.text = "Remaining destination(s): $stop"
            }
        }



        // Handle activity details
        if (activityList.isNotEmpty()) {
            val currentActivity = activityList[0]

            binding.headerActivityLabel.visibility = View.VISIBLE
            binding.activityCard.visibility = View.VISIBLE

            // Set activity name
            binding.tvActivityName.text = currentActivity.activityName
            binding.tvActivityName.visibility =
                if (currentActivity.activityName.isEmpty()) View.GONE else View.VISIBLE

            // Handle priority level and time constraint for tvPriority
            when (currentActivity.priorityLevel) {
                "Low" -> {
                    // Hide tvPriority
                    binding.tvPriority.text = "Activity at Low priority. No time constraint."
                    binding.tvActivityName.setTextColor(resources.getColor(R.color.green))
                }
                "High" -> {
                    // Set tvPriority for High priority
                    binding.tvPriority.visibility = View.VISIBLE
                    binding.tvPriority.text =
                        formatTimeRange(currentActivity.startTime, currentActivity.endTime)
                    binding.tvActivityName.setTextColor(resources.getColor(R.color.red))

                }
                "Medium" -> {
                    // Set tvPriority for Medium priority
                    binding.tvPriority.visibility = View.VISIBLE
                    binding.tvPriority.text =
                        formatTimeRange(currentActivity.startTime, currentActivity.endTime)
                    binding.tvActivityName.setTextColor(resources.getColor(R.color.bronze))
                }
                else -> {
                    // Default case: hide tvPriority
                    binding.tvPriority.visibility = View.GONE
                }
            }
        } else {
            // No activity available, hide the priority and set default text
            binding.tvActivityName.text = "No activity available"
            binding.tvActivityName.visibility = View.GONE
            binding.tvPriority.visibility = View.GONE
            binding.headerActivityLabel.visibility = View.GONE
            binding.activityCard.visibility = View.GONE
        }
    }

    private fun formatTimeRange(startTime: String?, endTime: String?): String {
        if (startTime.isNullOrEmpty() || endTime.isNullOrEmpty()) {
            Log.e("TIME_FORMAT", "Invalid startTime or endTime: startTime=$startTime, endTime=$endTime")
            return ""
        }

        try {
            // Adjust input format to include the date
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val outputFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

            val start = inputFormat.parse(startTime)
            val end = inputFormat.parse(endTime)

            return if (start != null && end != null) {
                "${outputFormat.format(start)} - ${outputFormat.format(end)}"
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.e("TIME_FORMAT", "Error formatting time: ${e.message}")
            return ""
        }
    }

    private fun clearMarkers() {
        // Remove markers from the map and clear the markerPlaceIdMap
        binding.assistantButton.visibility = View.GONE
        binding.clearMarkers.visibility = View.VISIBLE

        // Log to confirm clearing
        Log.e("MARKERS", "Clearing markers")

        // Remove all markers and clear the map
        markersList.forEach {
            it.remove()  // This will remove the marker from the map
            Log.e("MARKERS", "Removed marker: ${it.title}")
        }
        markersList.clear()

        // Clear the markerPlaceIdMap by removing all entries
        markerPlaceIdMap.clear()

        aiProcessor.clearPreviousData()

        // Log to confirm clearing
        Log.e("MARKERS", "Markers and Place ID map cleared")
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
        pendingRoute.setOnResultListener { routeStatus ->
            when (routeStatus) {
                Navigator.RouteStatus.OK -> {
                    // Initialize the FuelSetupManager (new fused class)
                    val fuelSetupManager = FuelSetupManager(this, travelMode)

                    // Check if fuel preferences are already set
                    if (fuelSetupManager.hasSetFuelPreferences()) {
                        proceedWithNavigation()
                    } else {
                        // Show fuel setup prompt and proceed once preferences are set
                        fuelSetupManager.showFuelSetupPrompt(){
                            proceedWithNavigation()
                        }


                    }

                    navigator.routeSegments.forEach { segment ->
                        allLatLngs.addAll(segment.latLngs)
                    }
                }
                Navigator.RouteStatus.NO_ROUTE_FOUND -> displayMessage("Error starting navigation: No route found.")
                Navigator.RouteStatus.NETWORK_ERROR -> displayMessage("Error starting navigation: Network error.")
                Navigator.RouteStatus.ROUTE_CANCELED -> displayMessage("Error starting navigation: Route canceled.")
                else -> displayMessage("Error starting navigation: $routeStatus")
            }
        }
    }

    private fun proceedWithNavigation() {
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

                    // Enable audio guidance if needed
                    if (isAudioGuidanceEnabled) {
                        navigator.setAudioGuidance(AudioGuidance.VOICE_ALERTS_AND_GUIDANCE)
                    }

                    // Check for rain check logic
                    if (isPlaceOpenNowClassifierFinished && !hasExecutedSuggestions) {
                        displayRainCheckBottomSheet()
                        Log.e(TAG, "THE RAIN CHECK WAS EXECUTED")
                        hasExecutedSuggestions = true
                    }

                    // Register navigation listeners
                    registerNavigationListeners()

                    // If simulation is enabled, simulate the route
                    if (isSimulated) {
                        navigator.simulator.simulateLocationsAlongExistingRoute(SimulationOptions().speedMultiplier(5F))
                    }

                    // Start guidance
                    navigator.startGuidance()
                    startTrip()
                }
            )
        }
    }

    private fun calculateAdHocTaskDelay(
        stopLatLng: LatLng,
        stopPlaceId: String,
        callback: (Boolean) -> Unit // Callback to notify if there is a delay
    ) {
        if (activityList.isEmpty()) {
            callback(false) // Return false if there are no activities
            return
        }

        // Step 1: Retrieve the first activity in the list and check its priority
        val activity = activityList.firstOrNull()
        val activityPriority = activity?.priorityLevel ?: "Low"  // Default to "Low" if null

        // Check if the activity priority is high or medium
        if (activityPriority !in listOf("High", "Medium")) {
            Log.e("AdHocTaskDelay", "Activity priority is not high or medium. Skipping delay calculation.")
            callback(false) // Return false if no delay
            return
        }

        // Step 2: Get the current location
        getCurrentLocation { currentLocation ->
            // Check if currentLocation is null
            if (currentLocation == null) {
                Log.e("AdHocTaskDelay", "Current location is null")
                callback(false) // Return false if location is unavailable
                return@getCurrentLocation
            }

            // Step 3: Retrieve the destination latLng from the first activity in the list
            val destinationLatLng = if (activity != null && activity.placeLatlng.isNotEmpty()) {
                val latLngParts = activity.placeLatlng.split(",")
                LatLng(latLngParts[0].toDouble(), latLngParts[1].toDouble())
            } else {
                Log.e("AdHocTaskDelay", "Activity or destination latLng is missing.")
                callback(false) // Return false if destination is missing
                return@getCurrentLocation
            }

            // Step 4: Use RouteFetcher to calculate the duration (including travel time)
            val latLngList = listOf(
                "${currentLocation.latitude},${currentLocation.longitude}", // Current location
                "${stopLatLng.latitude},${stopLatLng.longitude}", // Stop location
                "${destinationLatLng.latitude},${destinationLatLng.longitude}" // Destination location
            )

            val routeFetcher = RouteFetcher(
                context = this,
                travelMode = travelMode, // Assuming travel by car, adjust as necessary
                latLngList = latLngList
            )

            // Step 5: Fetch the route and calculate the total duration
            routeFetcher.fetchRoute {
                // Get the total travel duration
                val totalDuration = routeFetcher.getTotalDuration()

                // Log or process the total duration
                Log.d("AdHocTaskDelay", "Total travel duration (including stop): $totalDuration")

                // Step 6: Calculate the delay in minutes
                val estimatedTaskDuration = 30 // Replace with actual task duration (in minutes)
                val totalDurationInMinutes = totalDuration.split(" ").first().toInt() // Extract hours or minutes

                val delayInMinutes = totalDurationInMinutes + estimatedTaskDuration // Add task duration to total travel time

                // Log the delay
                Log.d("AdHocTaskDelay", "Estimated delay: $delayInMinutes minutes")

                // Step 7: Parse the activity's start and end times
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                val activityStartTime = LocalDateTime.parse(activity.startTime, formatter)
                val activityEndTime = LocalDateTime.parse(activity.endTime, formatter)

                // Step 8: Add the delay to the start time to calculate the new end time
                val delayDuration = Duration.ofMinutes(delayInMinutes.toLong())
                val delayEndTime = activityStartTime.plus(delayDuration)

                // Step 9: Check if the delay causes overlap with the current activity's time
                if (delayEndTime.isAfter(activityEndTime)) {
                    // There is overlap, show a dialog
                    val delayDurationFormatted = Duration.between(activityEndTime, delayEndTime).toMinutes()
                    showActivityConflictDialog(
                        "Activity Schedule Conflict",
                        "An estimated delay of $delayDurationFormatted minutes will cause an overlap with the scheduled end time of the next activity. Would you like to adjust the schedule or proceed with the current plan?",
                        stopPlaceId
                    )
                    callback(true) // Return true if there is a delay
                } else {
                    // No overlap, show toast with time left
                    val remainingTime = Duration.between(delayEndTime, activityEndTime).toMinutes()
                    displayMessage("Everything on schedule. Time left before next activity is $remainingTime minutes")
                    callback(false) // Return false if no delay
                }
            }
        }
    }




    private fun showActivityConflictDialog( title: String, message: String, stopPlaceId: String) {
        // Inflate the layout using data binding
        val dialogBinding = DialogActivitySuggestionsBinding.inflate(LayoutInflater.from(this))

        // Set the title and message dynamically
        dialogBinding.dialogTitle.text = title
        dialogBinding.dialogMessage.text = message

        // Create and set up the dialog
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnDismiss.text = "Cancel"
        dialogBinding.btnDismiss.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnAction.text = "Proceed"
        dialogBinding.btnAction.visibility = View.VISIBLE
        dialogBinding.btnAction.setOnClickListener {
            addStopDirectly(false, stopPlaceId)
            dialog.dismiss()
        }

        // Show the dialog
        dialog.show()
    }


    private fun registerNavigationListeners() {
        mArrivalListener = ArrivalListener { arrivalEvent ->

            if (arrivalEvent.isFinalDestination) {
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
                    if (activityList.isNotEmpty())
                        activityList.removeAt(0)
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
        totalRouteDistanceInMeters = navigator.currentTimeAndDistance.meters // Assume you retrieve this on route setup
        val mealStopTriggerDistance = 300

        // Display the fuel stop recommendation check
        if (travelMode != "WALK"){
            fuelStopRecommendationCheck()
            binding.gasLabelHeader.visibility = View.VISIBLE
            binding.gasInfoCard.visibility = View.VISIBLE
        } else {
            binding.gasLabelHeader.visibility = View.GONE
            binding.gasInfoCard.visibility = View.GONE
        }



        mRemainingTimeOrDistanceChangedListener = RemainingTimeOrDistanceChangedListener {
            val remainingDistanceInMeters = navigator.currentTimeAndDistance.meters
            val traveledDistance = totalRouteDistanceInMeters - remainingDistanceInMeters
            Log.e("Contextuals", "TOTAL DISTANCE: $totalRouteDistanceInMeters")
            Log.e("Contextuals", "REMAINING DISTANCE: $remainingDistanceInMeters")
            Log.e("Contextuals", "TRAVELED DISTANCE: $traveledDistance")


            if (!hasMealPlaceInDestination){
                if (!hasMealDialogDisplayed  && traveledDistance >= mealStopTriggerDistance && totalRouteDistanceInMeters > 5000) {
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

    private fun fuelStopRecommendationCheck() {
        val fuelSetupManager = FuelSetupManager(this, travelMode)
        fuelSetupManager.fetchVehicleDataAndCalculateFuel(
            context = this,
            totalRouteDistance = totalRouteDistanceInMeters / 1000.0,  // Convert meters to kilometers
            onCalculationCompleted = { remainingRange, isRefuelRequired, requiredFuel, vehicleType, fuelEfficiencyOfVehicle, vehicleTankCapacity, refuelingThreshold, refuelingThresholdVolume, fuelLevel ->

                // Populate TextViews with vehicle details
                binding.tvVehicle.text = vehicleType
                binding.tvFuelEffieciency.text = "$fuelEfficiencyOfVehicle km/L"
                binding.tvFuelLevel.text = "$fuelLevel liters"
                binding.tvCapacity.text = "$vehicleTankCapacity liters"
                binding.tvThreshold.text = "${String.format("%.2f", refuelingThresholdVolume)} liters"

                // Flag to prevent multiple dialogs
                var dialogShown = false

                if (isRefuelRequired) {
                    // Display fuel required and remaining range
                    binding.tvDistance.text = Html.fromHtml(
                        "You do not have enough fuel to continue this route. You need <font color='#FF0000'>${"%.2f".format(requiredFuel)} liters</font> of fuel to cover the ${remainingRange} km distance left.",
                        Html.FROM_HTML_MODE_LEGACY
                    )
                    fuelStopsRecommendation.showPlaceDialog(
                        "Fuel Insufficiency Alert",
                        "Your current fuel level is insufficient to complete the plotted route. We recommend refueling to ensure a smooth and uninterrupted journey. Please consider stopping at a nearby fuel station."
                    ) {
                        displayFuelStops()
                    }
                    dialogShown = true // Set flag to true after showing the dialog
                }

                // Check if the user has reached the refueling threshold
                if (refuelingThreshold && !dialogShown) {
                    fuelStopsRecommendation.showPlaceDialog(
                        "Fuel Threshold Alert",
                        "Your fuel level has reached the recommended refueling threshold. To ensure a smooth and uninterrupted journey, we advise refueling at your earliest convenience. Please consider stopping at a nearby fuel station."
                    ) {
                        displayFuelStops()
                    }
                    dialogShown = true // Set flag to true after showing the dialog
                }


                // If the fuel level is sufficient, but no nearby gas stations are found at the destination
                if (!isRefuelRequired && !dialogShown) {
                    binding.tvDistance.text = "You have enough fuel for the route."
                    fuelStopsRecommendation.checkForNearbyGasStationsAtDestination(
                        placesClient,
                        allLatLngs.last()
                    ) { shouldRecommendRefueling ->
                        if (shouldRecommendRefueling && !dialogShown) {
                            // Show refueling recommendation if there are no fuel stations at the destination
                            fuelStopsRecommendation.showPlaceDialog(
                                "Refueling Advisory",
                                "Although you have enough fuel for the current route, there are no nearby fuel stations at your destination. To avoid potential inconvenience, we recommend refueling at a nearby station before you reach your destination."
                            ) {
                                displayFuelStops()
                            }
                            dialogShown = true // Set flag to true after showing the dialog
                        }

                        Log.i("FUEL_STOPS", "LATLNG AT LAST: ${allLatLngs.last()}")
                    }
                }
            }
        )
    }

    private fun showEditFuelLevelBottomSheet() {
        // Inflate the layout using View Binding
        val sheetBinding = BottomSheetEditFuelLevelBinding.inflate(LayoutInflater.from(this))

        // Create the BottomSheetDialog
        val bottomSheetDialog = BottomSheetDialog(this)
        bottomSheetDialog.setContentView(sheetBinding.root)

        // Get tank capacity from the TextView (strip "L" and convert to Double)
        val tankCapacityStr = binding.tvCapacity.text.toString().replace("L", "").trim()
        val tankCapacity = tankCapacityStr.toDoubleOrNull() ?: 50.0  // Default to 50L if invalid

        // Set SeekBar max value to 100 (percentage)
        sheetBinding.fuelLevelSlider.max = 100

        val currentFuelLevel = binding.tvFuelLevel.text.toString().replace("liters", "").trim().toDoubleOrNull()

        Log.i("EDIT_FUEL_STOPS", "current fuel is: $currentFuelLevel")
        if (currentFuelLevel != null) {
            // Calculate the progress as a percentage of the tank capacity
            val fuelLevelPercentage = (currentFuelLevel / tankCapacity) * 100

            // Set the progress of the SeekBar based on the percentage
            sheetBinding.fuelLevelSlider.progress = fuelLevelPercentage.toInt()
            Log.i("EDIT_FUEL_STOPS", "current fuel is not null: $fuelLevelPercentage")

            // Update the TextView to display the fuel level in liters
            sheetBinding.tvFuelLevelValue.text = "${currentFuelLevel.toInt()}L"
        }

        //Initially set the refueling threshold
        val stringedFuelThresholdInLiters = binding.tvThreshold.text.toString().replace("liters", "").trim()
        val currentThresholdPercentage = ((stringedFuelThresholdInLiters.toDouble() * 100  ) / tankCapacity).toInt()
        sheetBinding.etRefuelingPercentage.setText(currentThresholdPercentage.toString())

        // Listen for SeekBar value changes and update the TextView
        sheetBinding.fuelLevelSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Calculate the fuel level in liters from the percentage progress
                val fuelLevelInLiters = (progress.toDouble() / 100) * tankCapacity
                // Update the TextView with the new fuel level in liters
                sheetBinding.tvFuelLevelValue.text = "${fuelLevelInLiters.toInt()}L"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Optionally handle touch start
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Optionally handle touch stop
            }
        })


        val colorStateList = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),  // Checked state
                intArrayOf(-android.R.attr.state_checked) // Unchecked state
            ),
            intArrayOf(
                ContextCompat.getColor(this, R.color.brand_color), // Checked color
                ContextCompat.getColor(this, R.color.gray)         // Unchecked color
            )
        )

        sheetBinding.rbAnalog.buttonTintList = colorStateList


        // Toggle between Analog and Liters input fields
        sheetBinding.radioGroupFuelLevel.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbAnalog -> {
                    // Show analog slider, hide liters input
                    sheetBinding.tvFuelLevelValue.visibility = View.VISIBLE
                    sheetBinding.rbAnalog.buttonTintList = colorStateList
                    sheetBinding.analogLayout.visibility = View.VISIBLE
                    sheetBinding.litersLayout.visibility = View.GONE
                }
                R.id.rbLiters -> {
                    // Show liters input, hide analog slider
                    sheetBinding.tvFuelLevelValue.visibility = View.GONE
                    sheetBinding.rbLiters.buttonTintList = colorStateList
                    sheetBinding.analogLayout.visibility = View.GONE
                    sheetBinding.litersLayout.visibility = View.VISIBLE
                }
            }
        }

        // Handle Save Button Click
        sheetBinding.btnSaveFuelLevel.setOnClickListener {
            // Get fuel level value (either from analog slider or liters input)
            val fuelLevel: Double = if (sheetBinding.rbAnalog.isChecked) {
                // Get value from slider (percentage)
                val percentage = sheetBinding.fuelLevelSlider.progress.toDouble() / 100
                percentage * tankCapacity
            } else {
                // Get value from liters input field
                val litersStr = sheetBinding.etFuelLiters.text.toString()
                val enteredLiters = litersStr.toDoubleOrNull() ?: 0.0

                // Validate if the entered liters exceeds tank capacity
                if (enteredLiters > tankCapacity) {
                    // If it exceeds, show a validation error (example: Toast or Error message)
                    Toast.makeText(this, "Fuel level cannot exceed tank capacity of $tankCapacity L.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener // Prevent saving
                }
                enteredLiters
            }
            val roundedFuelLevel = String.format("%.2f", fuelLevel).toDouble()
            val refuelPercentageStr = sheetBinding.etRefuelingPercentage.text.toString()

            // Default refuel percentage to 0 if not specified
            val refuelPercentage = if (refuelPercentageStr.isEmpty()) {
                0
            } else {
                refuelPercentageStr.toInt()
            }

            // Validate the refuel percentage if it's not empty
            if (refuelPercentage < 0 || refuelPercentage > 95) {
                // If the percentage is not valid, show a Toast message and return
                Toast.makeText(this, "Please enter a valid refuel percentage (0 - 95%).", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val fuelSetupManager = FuelSetupManager(this, travelMode)
            fuelSetupManager.updateFuelLevelAndRefuelingThreshold(this, roundedFuelLevel, refuelPercentage)
            binding.tvFuelLevel.text = "${roundedFuelLevel} liters"
            binding.tvThreshold.text = "${(refuelPercentageStr.toDouble() / 100) * tankCapacity} liters"

            // Dismiss bottom sheet after saving
            bottomSheetDialog.dismiss()
        }

        // Handle Cancel Button Click
        sheetBinding.btnCancel.setOnClickListener {
            // Close the bottom sheet without saving
            bottomSheetDialog.dismiss()
        }

        // Show the bottom sheet
        bottomSheetDialog.show()
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
        val sheetBinding = DialogTripRecapBinding.inflate(layoutInflater)

        // Populate the TextViews in the binding object
        sheetBinding.totalDistanceTextView.text = "Total Distance: $totalDistance"
        sheetBinding.totalTimeTextView.text = "Total Time: $totalTime"
        sheetBinding.arrivalTimeTextView.text = "Arrival Time: $arrivalTime"
        sheetBinding.averageSpeedTextView.text = "Average Speed: $averageSpeed"
        sheetBinding.trafficConditionsTextView.text = "Traffic: $trafficConditions"
        // Regular expression to extract place name and address
        val fullText = navigator.currentRouteSegment.destinationWaypoint.title
        val regex = Regex("""^(.*?)(?:,\s*(.*))?$""") // Match everything before the first comma and everything after

        val matchResult = regex.find(fullText)
        if (matchResult != null) {
            val placeName = matchResult.groups[1]?.value?.trim() ?: ""
            val address = matchResult.groups[2]?.value?.trim() ?: ""

            sheetBinding.placeNameTextView.text = placeName // Set only the place name
            sheetBinding.addressTextView.text = address // If you have an address TextView
        } else {
            // Handle unexpected format
            sheetBinding.placeNameTextView.text = fullText // Fallback
        }

        val fuelSetupManager = FuelSetupManager(this, travelMode)
        fuelSetupManager.updateFuelLevelViaDistanceCovered(
            context = this,
            traveledDistanceInMeters = calculateTotalDistanceCovered().toDouble(),
            onUpdateCompleted = { success, message ->
                if (success) {
                    binding.tvFuelLevel.text = message
                } else {
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                }
            }
        )
        // Create the AlertDialog
        val alertDialog = AlertDialog.Builder(this)
            .setView(sheetBinding.root) // Use the root view from the binding
            .setCancelable(true)
            .create()

        // Handle Done button
        sheetBinding.doneButton.setOnClickListener {
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
            Place.Field.ADDRESS,
            Place.Field.PHOTO_METADATAS,
            Place.Field.LAT_LNG,
            Place.Field.RATING,
            Place.Field.TYPES,
            Place.Field.PRIMARY_TYPE
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
        if (::textToSpeech.isInitialized) {
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
            displayMessage("Navigation terminated")
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
            showAddStopBottomSheet(p0.placeId, null, null, null)
        }
    }


    private fun checkPlaceDistance(
        currentLocation: LatLng?, // User's current location
        placeLatLng: LatLng?, // LatLng of the place
        distanceCallback: (String) -> Unit // Callback to return the distance or an error
    ) {
        if (currentLocation != null && placeLatLng != null) {
            // Build the API request URL parameters.
            val apiKey = BuildConfig.MAPS_API_KEY
            val origin = "${currentLocation.latitude},${currentLocation.longitude}"
            val destination = "${placeLatLng.latitude},${placeLatLng.longitude}"

            // Create a Retrofit instance for API requests.
            val retrofit = Retrofit.Builder()
                .baseUrl("https://maps.googleapis.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val api = retrofit.create(PlaceDistanceService::class.java)

            // Enqueue the API request to get directions and distance.
            api.getDirections(origin, destination, apiKey)
                .enqueue(object : Callback<PlaceDistanceResponse> {
                    override fun onResponse(
                        call: Call<PlaceDistanceResponse>,
                        response: Response<PlaceDistanceResponse>
                    ) {
                        // Handle the response from the API.
                        if (response.isSuccessful && response.body() != null) {
                            val directionsResponse = response.body()
                            if (directionsResponse?.routes?.isNotEmpty() == true) {
                                // Extract the distance from the response.
                                val distance = directionsResponse.routes[0].legs[0].distance.text

                                // Return distance through the callback
                                distanceCallback(distance)
                            } else {
                                // Return "Distance not available" if no routes are found.
                                distanceCallback("Distance not available")
                            }
                        } else {
                            // Return "Error calculating distance" if the API response fails.
                            distanceCallback("Error calculating distance")
                        }
                    }

                    override fun onFailure(call: Call<PlaceDistanceResponse>, t: Throwable) {
                        // Log the error and return "Error calculating distance"
                        Log.e("RetrofitError", "Error fetching directions: ${t.message}")
                        distanceCallback("Error calculating distance")
                    }
                })
        } else {
            // Return "Invalid locations" if either currentLocation or placeLatLng is null.
            distanceCallback("Invalid locations")
        }
    }


//    @SuppressLint("PotentialBehaviorOverride")
//    private fun plotMarkers(placesInfo: List<Map<String, Any>>) {
//        // Clear existing markers from the map
//        clearMarkers()
//
//        // Initialize LatLngBounds.Builder to calculate bounds for all markers
//        val boundsBuilder = LatLngBounds.Builder()
//
//        Log.e(TAG, "it worked here")
//
//        // Log the size of placesInfo
//        Log.e(TAG, "Number of places info: ${placesInfo.size}")
//
//        // Iterate through the places info and add markers to the map
//        placesInfo.forEach { placeInfo ->
//            // Extracting values from the map
//            val latLng = placeInfo["latLng"] as LatLng
//            val name = placeInfo["name"] as String
//            val address = placeInfo["address"] as String
//            val placeId = placeInfo["placeId"] as String
//
//            // Add a marker with a visible icon
//            val markerOptions = MarkerOptions()
//                .position(latLng)
//                .title(name) // Set the title to show in the info window
//                .snippet(address) // Optional: show address in the info window
//                .alpha(1f)
//                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN)) // Use a visible color
//
//            // Add the visible marker to the map and store it in the markers list
//            val marker = mMap?.addMarker(markerOptions)
//            if (marker != null) {
//                markersList.add(marker) // Keep track of the marker
//                // Include this marker's position in the bounds
//                boundsBuilder.include(marker.position)
//
//                // Store the marker and placeId mapping
//                markerPlaceIdMap[marker] = placeId
//
//                marker.showInfoWindow()
//            }
//
//            mMap?.setOnMarkerClickListener { marker2 ->
//                // Retrieve the placeId using the marker
//                val clickedPlaceId = markerPlaceIdMap[marker2] ?: "NO PLACE ID"
//                Log.e(TAG, "Marker clicked: ${marker2.title}, PlaceId: $clickedPlaceId")
//
//                // When a marker is clicked, show the bottom sheet
//                showAddStopBottomSheet(marker2,clickedPlaceId)
//
//                true // Return true to indicate the click was handled
//            }
//
//            // Log the place information for debugging
//            Log.e(TAG, "Added marker for: Name: $name, Address: $address, LatLng: $latLng, PlaceId: $placeId")
//        }
//
//        // After adding all markers, move and zoom the camera to show all markers
//        if (markersList.isNotEmpty()) {
//            val bounds = boundsBuilder.build()
//            val padding = 100 // Padding around the bounds (in pixels)
//
//            // Animate the camera to fit the bounds with padding
//            mMap?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
//        }
//    }





//    private fun processUserQuery(query: String) {
//        // Launch a coroutine in the lifecycleScope
//        lifecycleScope.launch {
//            try {
//                // Call the processUserQuery method of AIProcessor
//                textToSpeech.speakResponse("Searching, please wait.")
//                withContext(Dispatchers.Main) {
//                    showLoadingDialog(true)
//                }
//                val result = aiProcessor.processUserQuery(query)
//                allLatLngs.clear()
//                navigator.routeSegments.forEach { segment ->
//                    // Add all LatLngs from the current segment to the list
//                    allLatLngs.addAll(segment.latLngs)
//                }
//                Log.e("MARKERS", "MARKER LIST: ${markersList.size}")
//                Log.e("MARKERS", "MARKER PLACE ID MAP: ${markerPlaceIdMap.size}")
//
//                Log.e("MARKERS", "SEARCH STOP LATLNGS: $allLatLngs")
//                Log.e("MARKERS", "SEARCH STOP LATLNG SIZE: ${allLatLngs.size}")
//                aiProcessor.intentClassification(aiProcessor.parseUserQuery(result), allLatLngs)
//
//                placesInfo = aiProcessor.extractPlaceInfo()
//                Log.e("MARKERS", "PLACE INFO SIZE: ${placesInfo.size}")
//
//                if (aiProcessor.hasPlaceIdAndIsValidPlace()) {
//                    textToSpeech.speakResponse("Here's what I've got.")
//                    withContext(Dispatchers.Main) {
//                        showLoadingDialog(false)
//                    }
//                    showPlaceRecommendationsBottomSheet(
//                        "Nearby places found",
//                        "Here's what I've found within your route")
//                } else {
//                    withContext(Dispatchers.Main) {
//                        showLoadingDialog(false)
//                    }
//
//                    aiProcessor.setOnPlaceSelectedCallback { selectedPlaceId ->
//                        addStopDirectly(false, selectedPlaceId)
//                        Log.e("OFF ROUTE PLACE", "PLACE ID IS: $selectedPlaceId")
//                    }
//
//
//                    textToSpeech.speakResponse("Unfortunately, I couldn't find any information related to your query, '$query.' Please consider trying alternative keywords for your search.")
//                }
//            } catch (e: ResponseStoppedException) {
//                Log.e("AIProcessor", "Response generation stopped due to safety concerns: ${e.message}")
//                textToSpeech.speakResponse("Unfortunately, I cannot find what you are looking for.")
//                withContext(Dispatchers.Main) {
//                    showLoadingDialog(false)
//                }
//            } catch (e: Exception) {
//                Log.e("AIProcessor", "Error processing query: ${e.message}", e)
//                textToSpeech.speakResponse("Unfortunately, I cannot find what you are looking for right now.")
//                withContext(Dispatchers.Main) {
//                    showLoadingDialog(false)
//                }
//            }
//        }
//    }



}

