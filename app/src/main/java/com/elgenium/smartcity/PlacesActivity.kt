package com.elgenium.smartcity

import PlacesClientSingleton
import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.elgenium.smartcity.contextuals.MealPlaceRecommendationManager
import com.elgenium.smartcity.databinding.ActivityPlacesBinding
import com.elgenium.smartcity.models.RecommendedPlace
import com.elgenium.smartcity.models.SavedPlace
import com.elgenium.smartcity.network.PlaceDistanceService
import com.elgenium.smartcity.network_reponses.PlaceDistanceResponse
import com.elgenium.smartcity.recyclerview_adapter.RecommendedPlaceAdapter
import com.elgenium.smartcity.singletons.ActivityNavigationUtils.navigateToActivity
import com.elgenium.smartcity.singletons.BottomNavigationManager
import com.elgenium.smartcity.singletons.LayoutStateManager
import com.elgenium.smartcity.singletons.NavigationBarColorCustomizerHelper
import com.elgenium.smartcity.singletons.NotificationDataHandler
import com.elgenium.smartcity.singletons.TokenManager
import com.elgenium.smartcity.viewpager_adapter.PhotoPagerAdapter
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PointOfInterest
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.PhotoMetadata
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPhotoRequest
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.SearchByTextRequest
import com.google.android.libraries.places.api.net.SearchNearbyRequest
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

@Suppress("PrivatePropertyName")
class PlacesActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnPoiClickListener {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityPlacesBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val placesClient by lazy { PlacesClientSingleton.getClient(this) }
    private val DEFAULT_ZOOM_LEVEL = 15f
    private val DEFAULT_HEADING = 0f
    private val DEFAULT_ORIENTATION = 0f
    private var isFollowingUser = true
    private var currentRedMarker: Marker? = null
    private val poiMarkers = mutableListOf<Marker>()
    private var placeIDFromSearchActivity: String?= "No Place ID"
    private var savedPlace: SavedPlace = SavedPlace()
    private var placesList: MutableList<RecommendedPlace> = mutableListOf()
    private var isFewerLabels = false
    private var isFewerLandmarks = false
    private var mapTheme = "Aubergine"
    private var isTrafficOverlayEnabled = false
    private lateinit var mealPlaceRecommender: MealPlaceRecommendationManager
    private lateinit var sharedPreferences: SharedPreferences


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlacesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // Singleton object to set the color of the navigation bar making it more personalized
        NavigationBarColorCustomizerHelper.setNavigationBarColor(this, R.color.secondary_color)
        mealPlaceRecommender = MealPlaceRecommendationManager(this)

        retrievePreferences()

        // Singleton object that will handle bottom navigation functionality
        BottomNavigationManager.setupBottomNavigation(this, binding.bottomNavigation, PlacesActivity::class.java)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Set up the Search FAB to navigate to SearchActivity
        binding.fabSearch.setOnClickListener {
            navigateToActivity(this, SearchActivity::class.java, true)
        }


        // Setup map styles button listener
        binding.fabMapStyles.setOnClickListener {
            showMapStylesBottomSheet()
        }

        // Initialize FloatingActionButton using ViewBinding
        binding.fabCurrentLocation.setOnClickListener {
            resetMapToDefaultLocation()
        }


        // Fetch the place ID passed from the Dashboard
        val placeIDFromDashboard = intent.getStringExtra("DASHBOARD_RECOMMENDED_PLACE_ID")

        // If placeIDFromDashboard is not null or empty, proceed to fetch place details
        if (!placeIDFromDashboard.isNullOrEmpty()) {
            fetchPlaceDetailsFromAPI(placeIDFromDashboard) { savedPlace ->
                savedPlace?.let {
                    this.savedPlace = it
                    plotMarkerOnMap(placeIDFromDashboard, savedPlace)
                    showPlaceDetailsInBottomSheet(savedPlace)
                } ?: run {
                    Log.e("PlacesActivity", "Failed to fetch place details from dashboard")
                }
            }
        }

        // Fetch the place ID from intent
        placeIDFromSearchActivity = intent.getStringExtra("PLACE_ID")

        placeIDFromSearchActivity?.let { it ->
            fetchPlaceDetailsFromAPI(it) { savedPlace ->
                savedPlace?.let {
                    this.savedPlace = it
                    // Update UI or do something with the savedPlace
                    plotMarkerOnMap(placeIDFromSearchActivity!!, savedPlace) // Use the Place object to plot the marker
                    Log.e("PlacesActivity", "type 1: ${savedPlace.types}")


                    showPlaceDetailsInBottomSheet(it) // Use the Place object to show details
                } ?: run {
                    // Handle the case where savedPlace is null
                    Log.e("PlacesActivity", "Failed to fetch place details")
                }
            }
        }
        Log.d("PlacesActivity", "On create place id:  $placeIDFromSearchActivity")
        Log.d("PlacesActivity", "Inside of place data:  $savedPlace")


        // Get the category from the intent from Search Activity, if available
        val category = intent.getStringExtra("CATEGORY")

        // Fetch the user's location (ensure you have location permission)
        getUserLocation { userLatLng ->
            // Use the userLatLng here
            fetchAndAddPois(userLatLng, category)
        }


        userTokenForNotifSetup()

        NotificationDataHandler.init(this)
        fetchAndSaveCityName()


        sharedPreferences = getSharedPreferences("user_settings", MODE_PRIVATE)
        fetchRecommendedMealPlaces()
    }


    private fun fetchRecommendedMealPlaces() {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        // Determine meal time
        val mealTime = when (currentHour) {
            in 5..11 -> "breakfast"
            in 12..13 -> "lunch"
            in 14..17 -> "snack"
            in 18..22 -> "dinner"
            else -> "late-night" // Late-night option
        }

        // Check if meal time is valid and if recommendations have already been shown
        val hasDisplayedRecommendation = sharedPreferences.getBoolean("hasDisplayedRecommendation_$mealTime", false)
        val hasExecutedBreakfast  = sharedPreferences.getBoolean("hasDisplayedRecommendation_breakfast", false)
        val hasExecutedLunch = sharedPreferences.getBoolean("hasDisplayedRecommendation_lunch", false)
        val hasExecutedSnack = sharedPreferences.getBoolean("hasDisplayedRecommendation_snack", false)
        val hasExecutedDinner = sharedPreferences.getBoolean("hasDisplayedRecommendation_dinner", false)
        val hasExecutedLateNight = sharedPreferences.getBoolean("hasDisplayedRecommendation_late-night", false)

        Log.d("Recommendation", "HAS EXECUTED FOR $mealTime: $hasDisplayedRecommendation")

        Log.d("Recommendation", "HAS EXECUTED BREAKFAST: $hasExecutedBreakfast")
        Log.d("Recommendation", "HAS EXECUTED LUNCH: $hasExecutedLunch")
        Log.d("Recommendation", "HAS EXECUTED SNACK: $hasExecutedSnack")
        Log.d("Recommendation", "HAS EXECUTED DINNER: $hasExecutedDinner")
        Log.d("Recommendation", "HAS EXECUTED LATE NIGHT: $hasExecutedLateNight")

        if (!hasDisplayedRecommendation) {
            Log.d("Recommendation", "Performing text search for meal places...")

            // Get recommended place types
            val recommendedPlaceTypes = mealPlaceRecommender.mealTimePlaceMappings[mealTime]

            if (!recommendedPlaceTypes.isNullOrEmpty()) {
                mealPlaceRecommender.performTextSearch(placesClient, recommendedPlaceTypes, this, null, null, null, false) {
                    Log.d("Recommendation", "Text search for meal places complete.")
                }

                // Mark as displayed
                sharedPreferences.edit().putBoolean("hasDisplayedRecommendation_$mealTime", true).apply()
            } else {
                Log.e("MealRecommendationActivity", "No recommended place types found for meal time: $mealTime")
            }
        }

        if (mealTime == "late-night" && currentHour == 4)
            resetRecommendations()
    }

    // Optional: Reset the flags at the start of a new day (e.g., midnight)
    private fun resetRecommendations() {
        val editor = sharedPreferences.edit()
        editor.putBoolean("hasDisplayedRecommendation_breakfast", false)
        editor.putBoolean("hasDisplayedRecommendation_lunch", false)
        editor.putBoolean("hasDisplayedRecommendation_snack", false)
        editor.putBoolean("hasDisplayedRecommendation_dinner", false)
        editor.putBoolean("hasDisplayedRecommendation_late-night", false) // Reset late-night flag
        editor.apply()
    }


    private fun fetchAndSaveCityName() {
        NotificationDataHandler.getUserCityName(this) { cityName ->
            if (cityName != null) {
                Log.d("MainActivity", "Fetched city name: $cityName")

                // Check if the current city is different from the saved one
                if (NotificationDataHandler.isCurrentCityDifferent(this, cityName)) {
                    // If the city is different, save it to SharedPreferences and Firebase
                    NotificationDataHandler.checkAndSaveCityName(this, cityName)
                } else {
                    Log.d("MainActivity", "City name is already up-to-date.")
                }
            } else {
                Log.e("MainActivity", "Failed to retrieve city name")
            }
        }
    }



    private fun userTokenForNotifSetup() {
        val savedToken = TokenManager.getSavedToken(this)
        if (savedToken == null) {
            getToken() // Implement this to fetch the token
        } else {
            Log.d("FCM", "Token already saved: $savedToken")
            TokenManager.saveTokenToFirebase(this,savedToken)
        }
    }

    private fun getToken() {
        // Fetch the FCM registration token
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.d("FCM", "New token retrieved: $token")

                // Send the token to the server
                TokenManager.saveTokenToFirebase(this, token ?: "NO TOKEN")
            } else {
                Log.e("FCM", "Fetching FCM registration token failed", task.exception)
            }
        }
    }


     private fun retrievePreferences() {
        val sharedPreferences = getSharedPreferences("user_settings", MODE_PRIVATE)
        mapTheme = sharedPreferences.getString("map_theme", "Aubergine").toString()
        isFewerLabels = sharedPreferences.getBoolean("map_labels", false)
         isFewerLandmarks = sharedPreferences.getBoolean("map_landmarks", false)
         isTrafficOverlayEnabled = sharedPreferences.getBoolean("map_overlay", false)

         // Optionally log the retrieved value
        Log.e("Preferences", "contextRecommender at retrievePreferences theme: $mapTheme")
        Log.e("Preferences", "eventRecommender at retrievePreferences labels: $isFewerLabels")
         Log.e("Preferences", "eventRecommender at retrievePreferences landmarks: $isFewerLandmarks")
         Log.e("Preferences", "eventRecommender at retrievePreferences traffic overlay: $isTrafficOverlayEnabled")

     }

    private fun getUserLocation(onLocationReceived: (LatLng) -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val userLatLng = LatLng(it.latitude, it.longitude)
                    onLocationReceived(userLatLng)
                } ?: run {
                    Log.e("LocationError", "Location is null")
                }
            }
        } else {
            checkLocationPermission()
        }
    }

    private fun showMapStylesBottomSheet() {
        // Inflate the bottom sheet layout
        val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_map_options, findViewById(android.R.id.content), false)
        val bottomSheetDialog = BottomSheetDialog(this)
        bottomSheetDialog.setContentView(bottomSheetView)

        // Get references to the buttons
        val btnClose: ImageButton = bottomSheetView.findViewById(R.id.btnClose)
        val btnSatellite: ImageButton = bottomSheetView.findViewById(R.id.btnSatellite)
        val btnStandard: ImageButton = bottomSheetView.findViewById(R.id.btnStandard)
        val btnTerrain: ImageButton = bottomSheetView.findViewById(R.id.btnTerrain)

        // Set click listeners for the buttons
        btnClose.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        btnSatellite.setOnClickListener {
            mMap.mapType = GoogleMap.MAP_TYPE_SATELLITE
            bottomSheetDialog.dismiss()
        }

        btnStandard.setOnClickListener {
            // Set the map to the default, flatter map style
            mMap.mapType = GoogleMap.MAP_TYPE_NORMAL
            // Ensure the tilt is set to zero to maintain a flat view
            val currentCameraPosition = mMap.cameraPosition
            val flatCameraPosition = CameraPosition.builder(currentCameraPosition)
                .tilt(0f) // Set tilt to zero for a flat view
                .build()
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(flatCameraPosition))
            bottomSheetDialog.dismiss()
        }

        btnTerrain.setOnClickListener {
            mMap.mapType = GoogleMap.MAP_TYPE_TERRAIN
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }

    private fun resetMapToDefaultLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val userLatLng = LatLng(it.latitude, it.longitude)
                    val cameraPosition = CameraPosition.builder()
                        .target(userLatLng)
                        .zoom(DEFAULT_ZOOM_LEVEL)
                        .bearing(DEFAULT_HEADING)
                        .tilt(DEFAULT_ORIENTATION)
                        .build()

                    // Remove the previous red marker if it exists
                    currentRedMarker?.let { existingMarker ->
                        try {
                            existingMarker.remove()
                            Log.d("PlacesActivity", "Removed previous red marker during reset")
                        } catch (e: Exception) {
                            Log.e("PlacesActivity", "Error removing previous red marker during reset", e)
                        }
                    }

                    // Remove all POI markers
                    poiMarkers.forEach { marker ->
                        try {
                            marker.remove()
                            Log.d("PlacesActivity", "Removed POI marker: ${marker.title}")
                        } catch (e: Exception) {
                            Log.e("PlacesActivity", "Error removing POI marker: ${marker.title}", e)
                        }
                    }
                    poiMarkers.clear() // Clear the list after removing markers

                    // Use animateCamera to smoothly transition to the new camera position
                    mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
                }
            }
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        }
    }

    @SuppressLint("PotentialBehaviorOverride")
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        mMap.setIndoorEnabled(true)
        setMapStyle()

        googleMap.setOnPoiClickListener(this)

        // Check for location permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // Enable the My Location layer
            mMap.isMyLocationEnabled = true

            // Optional: Hide the default My Location button
            mMap.uiSettings.isMyLocationButtonEnabled = false

            // Use the getUserLocation method to move the camera to the user's location
            getUserLocation { userLatLng ->
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 15f))
            }

            // Start location updates without moving the camera
            startLocationUpdates()

            // Listen for user interactions with the map
            mMap.setOnCameraMoveStartedListener { reason ->
                if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                    // User moved the map; stop following
                    isFollowingUser = false
                }
            }

            // Marker click listener to show the bottom sheet
            mMap.setOnMarkerClickListener { marker ->
                // Fetch the place ID (you can save this when adding the marker)
                val placeId = marker.tag as? String
                placeId?.let {
                    Log.e("PlacesActivity", "At on map click marker listener: $savedPlace")
                    // Plot the marker on the map
                    plotMarkerOnMap(placeId, savedPlace)
                    // Show place details in the bottom sheet
                    showPlaceDetailsInBottomSheet(savedPlace)
                    // Return true to indicate the click event has been handled
                    true
                } ?: false
            }
        } else {
            // Request location permission
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        }
    }

    override fun onPoiClick(poi: PointOfInterest) {

        currentRedMarker?.remove()
        // Handle the POI click event
        currentRedMarker = mMap.addMarker(
            MarkerOptions()
                .position(poi.latLng)
                .title(poi.name)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)) // Red marker
        )

        // Optionally, move the camera to the new marker
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(poi.latLng, 15f))

        poi.placeId?.let { it ->
            fetchPlaceDetailsFromAPI(it) { savedPlace ->
                savedPlace?.let {
                    this.savedPlace = it
                    // Update UI or do something with the savedPlace
                    plotMarkerOnMap(poi.placeId!!, savedPlace) // Use the Place object to plot the marker
                    showPlaceDetailsInBottomSheet(it) // Use the Place object to show details
                } ?: run {
                    // Handle the case where savedPlace is null
                    Log.e("PlacesActivity", "Failed to fetch place details")
                }
            }
        }
    }

    private fun setMapStyle() {
        try {
            // Determine the appropriate style based on conditions
            val styleResource = when {
                mapTheme == "Aubergine" && !isFewerLabels && !isFewerLandmarks -> {
                    showToast("Applying Aubergine style")
                    R.raw.aubergine
                }

                mapTheme == "Aubergine" && isFewerLabels && isFewerLandmarks -> {
                    showToast("Applying Aubergine style with few landmarks & labels")
                    R.raw.aubergine_few_landmarks_label
                }

                mapTheme == "Aubergine" && isFewerLandmarks -> {
                    showToast("Applying Aubergine style with fewer landmarks")
                    R.raw.aubergine_few_landmarks
                }
                mapTheme == "Aubergine" && isFewerLabels -> {
                    showToast("Applying Aubergine style with fewer labels")
                    R.raw.aubergine_few_labels
                }
                mapTheme == "Standard" && !isFewerLabels && !isFewerLandmarks -> {
                    showToast("Applying Standard style")
                    R.raw.light
                }

                mapTheme == "Standard" && isFewerLabels && isFewerLandmarks -> {
                    showToast("Applying Standard style with few landmarks & label")
                    R.raw.light_few_landmarks_label
                }
                mapTheme == "Standard" && isFewerLandmarks -> {
                    showToast("Applying Standard style with fewer landmarks")
                    R.raw.light_few_landmarks
                }
                mapTheme == "Standard" && isFewerLabels -> {
                    showToast("Applying Standard style with fewer labels")
                    R.raw.light_few_labels
                }
                mapTheme == "Retro" && !isFewerLabels && !isFewerLandmarks -> {
                    showToast("Applying Retro style")
                    R.raw.retro
                }

                mapTheme == "Retro" && isFewerLabels && isFewerLandmarks -> {
                    showToast("Applying Retro style with few landmarks & label")
                    R.raw.retro_few_landmarks_label
                }
                mapTheme == "Retro" && isFewerLandmarks -> {
                    showToast("Applying Retro style with fewer landmarks")
                    R.raw.retro_few_landmarks
                }
                mapTheme == "Retro" && isFewerLabels -> {
                    showToast("Applying Retro style with fewer labels")
                    R.raw.retro_few_labels
                }
                else -> {
                    showToast("No valid style found; using default.")
                    null // Fallback in case no conditions match
                }
            }

            // Load the JSON file from the res/raw directory if a style resource is determined
            styleResource?.let { resource ->
                val inputStream = resources.openRawResource(resource)
                val jsonString = inputStream.bufferedReader().use { it.readText() }

                // Apply the style to the map
                val success = mMap.setMapStyle(MapStyleOptions(jsonString))
                if (!success) {
                    Log.e("MapStyle", "Style parsing failed.")
                }
            } ?: Log.e("MapStyle", "No valid map style resource found.")

            mMap.isTrafficEnabled = isTrafficOverlayEnabled


        } catch (e: IOException) {
            e.printStackTrace()
            // Handle the exception if loading the style fails
        } catch (e: Resources.NotFoundException) {
            e.printStackTrace()
        }
    }

    // Helper function to show Toast messages
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }


    private fun setupMoreButton(bottomSheetView: View, bottomSheetDialog: BottomSheetDialog) {
        val btnMore: MaterialButton = bottomSheetView.findViewById(R.id.btnMore)

        btnMore.setOnClickListener {
            // Dismiss the current bottom sheet if needed
            bottomSheetDialog.dismiss()

            // Show the options bottom sheet
            setupOptionsBottomSheet(bottomSheetView)
        }
    }

    private fun setupSavedPlaces(bottomSheetView: View, bottomSheetDialog: BottomSheetDialog, place: SavedPlace) {
        val btnSave: MaterialButton = bottomSheetView.findViewById(R.id.btnSave)

        btnSave.setOnClickListener {
            // Dismiss the current bottom sheet if needed
            bottomSheetDialog.dismiss()

            // Get the current user ID
            val userId = FirebaseAuth.getInstance().currentUser?.uid
            if (userId != null) {
                // Get a reference to the Firebase Realtime Database
                val database = FirebaseDatabase.getInstance()
                val userRef = database.getReference("Users/$userId/saved_places")

                // Fetch data from the bottom sheet UI elements
                val placeName = bottomSheetView.findViewById<TextView>(R.id.placeName).text.toString()
                val placeAddress = bottomSheetView.findViewById<TextView>(R.id.placeAddress).text.toString()
                val placePhoneNumber = bottomSheetView.findViewById<TextView>(R.id.placePhone).text.toString()
                val placeRating = bottomSheetView.findViewById<TextView>(R.id.placeRating).text.toString()
                val placeWebsite = bottomSheetView.findViewById<TextView>(R.id.placeWebsite).text.toString()
                val placeDistance = bottomSheetView.findViewById<TextView>(R.id.placeDistance).text.toString()

                // Prepare place data
                val placeData = placeIDFromSearchActivity?.let {
                    SavedPlace(
                        id = it,
                        name = placeName,
                        address = placeAddress,
                        phoneNumber = placePhoneNumber,
                        latLngString = place.latLng.toString(),
                        openingDaysAndTime = place.openingDaysAndTime ?: "No opening days available",
                        rating = placeRating,
                        websiteUri = placeWebsite,
                        distance = placeDistance,
                    )
                }

                Log.d("PlacesActivity", "Inside of place data:  $placeData")

                LayoutStateManager.showLoadingLayout(this, "Please wait while we are saving your place")

                // Check for existing records first
                userRef.orderByChild("id").equalTo(placeData?.id).addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.exists()) {
                            // Place already exists, show a message or handle accordingly
                            LayoutStateManager.showFailureLayout(this@PlacesActivity, "This place is already save in the Favorites section. Please select another place to save.", "Return to Places", PlacesActivity::class.java)
                            savedPlace.id?.let { placeId ->
                                savedPlace.name?.let { placeName ->
                                    savedPlace.types?.let { placeTypes ->
                                        logSavedPlace(placeId, placeName, placeTypes)
                                    }
                                }
                            }

                        } else {
                            // Proceed to upload images and save the place data
                            uploadPlaceImagesAndSaveData(userRef, placeData, place)
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        // Handle possible errors
                        Log.e("PlacesActivity", "Database error", error.toException())
                    }
                })
            } else {
                // Handle case where user is not authenticated
                Toast.makeText(this, "User not authenticated. Please log in.", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun uploadPlaceImagesAndSaveData(userRef: DatabaseReference, placeData: SavedPlace?, place: SavedPlace) {
        val storageRef = FirebaseStorage.getInstance().reference.child("places")
        val photoMetadatas = place.photoMetadataList
        val imageUrls = mutableListOf<String>()

        // Limit the number of photos to a maximum of 4
        val limitedPhotoMetadatas = if (photoMetadatas.size > 4) photoMetadatas.take(4) else photoMetadatas

        if (limitedPhotoMetadatas.isEmpty()) {
            // Save place data without images if no images exist
            savePlaceToDatabase(userRef, placeData, imageUrls)
            return
        }

        limitedPhotoMetadatas.forEachIndexed { _, photoMetadata ->
            val photoRequest = FetchPhotoRequest.builder(photoMetadata)
                .setMaxWidth(400)
                .setMaxHeight(400)
                .build()

            placesClient.fetchPhoto(photoRequest)
                .addOnSuccessListener { response ->
                    val photoBitmap = response.bitmap

                    // Convert Bitmap to ByteArray for Firebase Storage upload
                    val baos = ByteArrayOutputStream()
                    photoBitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
                    val data = baos.toByteArray()

                    // Upload the photo to Firebase Storage
                    // Create a unique file name using place name, index, and current timestamp
                    val photoRef = storageRef.child("${place.name}_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg")
                    val uploadTask = photoRef.putBytes(data)

                    uploadTask.addOnSuccessListener {
                        // Get the download URL of the uploaded image
                        photoRef.downloadUrl.addOnSuccessListener { uri ->
                            imageUrls.add(uri.toString())

                            // Once all images are uploaded, save place data with image URLs
                            if (imageUrls.size == limitedPhotoMetadatas.size) {
                                savePlaceToDatabase(userRef, placeData, imageUrls)
                            }
                        }
                    }.addOnFailureListener { exception ->
                        Log.e("PlacesActivity", "Error uploading image", exception)
                    }
                }.addOnFailureListener { exception ->
                    Log.e("PlacesActivity", "Error fetching photo", exception)
                }
        }
    }

    private fun savePlaceToDatabase(userRef: DatabaseReference, placeData: SavedPlace?, imageUrls: List<String>) {
        placeData?.let { place ->
            // Add the image URLs to the place data
            val updatedPlaceData = place.copy(imageUrls = imageUrls)

            // Save the place data without checking for duplicates again
            val newPlaceRef = userRef.push()
            newPlaceRef.setValue(updatedPlaceData)
                .addOnSuccessListener {
                    // Successfully saved the place data
                    LayoutStateManager.showSuccessLayout(
                        this@PlacesActivity,
                        "Place saved successfully!",
                        "You can now view your saved place under the Favorites menu.",
                        PlacesActivity::class.java
                    )
                }
                .addOnFailureListener { exception ->
                    // Handle any errors
                    Log.e("PlacesActivity", "Error saving place data", exception)
                    LayoutStateManager.showFailureLayout(
                        this@PlacesActivity,
                        "Something went wrong. Please check your connection or try again.",
                        "Return to Places",
                        PlacesActivity::class.java
                    )
                }
        }
    }


    @SuppressLint("PotentialBehaviorOverride")
    private fun fetchAndAddPois(userLatLng: LatLng, category: String? = null) {
        // Check if category is null or empty
        if (category.isNullOrEmpty()) {
            Log.e("PlacesActivity", "No category provided. No POIs will be displayed.")
            return // Exit the method if no category is provided
        }

        // Check for location permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {


            // Define the search area as a 1000 meter diameter circle
            val center = LatLng(userLatLng.latitude, userLatLng.longitude)
            val circle = CircularBounds.newInstance(center, 1000.0) // 1000 meters

            // Define the fields to include in the response for each returned place
            val placeFields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.TYPES)

            // Define included and excluded types
            val includedTypes = listOf(category) // Only include "bar"

            // Build the SearchNearbyRequest
            val searchNearbyRequest = SearchNearbyRequest.builder(circle, placeFields)
                .setIncludedTypes(includedTypes) // Only bars will be included
                .setMaxResultCount(15)
                .build()

            // Call placesClient.searchNearby() to perform the search
            placesClient.searchNearby(searchNearbyRequest)
                .addOnSuccessListener { response ->
                    val places = response.places

                    // Clear existing markers if needed (optional)
                    // poiMarkers.forEach { it.remove() }
                    // poiMarkers.clear()

                    places.forEach { place ->
                        val latLng = place.latLng ?: return@forEach

                        val markerOptions = MarkerOptions()
                            .position(latLng)
                            .title(place.name)
                            .snippet("Type: ${place.placeTypes?.joinToString(", ")}")

                        // Set the icon based on the category
                        place.placeTypes?.let { placeTypes ->
                            when {
                                placeTypes.contains("restaurant") -> markerOptions.icon(createCustomMarker(R.drawable.restaurant))
                                placeTypes.contains("hotel") -> markerOptions.icon(createCustomMarker(R.drawable.hotel))
                                placeTypes.contains("pharmacy") -> markerOptions.icon(createCustomMarker(R.drawable.pharmacy))
                                placeTypes.contains("atm") -> markerOptions.icon(createCustomMarker(R.drawable.atm))
                                placeTypes.contains("gas_station") -> markerOptions.icon(createCustomMarker(R.drawable.gas_station))
                                placeTypes.contains("supermarket") -> markerOptions.icon(createCustomMarker(R.drawable.market))

                                else -> markerOptions.icon(createCustomMarker(R.drawable.marker_custom))
                            }
                        }


                        // Add the marker to the map
                        val marker = mMap.addMarker(markerOptions)
                        marker?.tag = place.id // Store place ID in marker's tag

                        // Add marker to the poiMarkers list
                        marker?.let { poiMarkers.add(it) }
                        Log.d("PlacesActivity", "Marker added with ID: ${place.id}")
                        Log.d("PlacesActivity", "POI markers on the list: $poiMarkers")

                        // Set the marker click listener
                        mMap.setOnMarkerClickListener { markers ->
                            // Retrieve the place ID from the marker's tag
                            val placeId = markers.tag as? String
                            placeId?.let {
                                // Fetch the details for this specific place and show them in the bottom sheet
                                fetchPlaceDetailsFromAPI(it) { fetchedPlace ->
                                    fetchedPlace?.let { placeDetails ->
                                        showPlaceDetailsInBottomSheet(placeDetails) // Show details in the bottom sheet
                                    } ?: run {
                                        Log.e("PlacesActivity", "Failed to fetch place details for place ID: $placeId")
                                    }
                                }
                            }
                            true // Return true to indicate that we have handled the click
                        }
                    }
                }.addOnFailureListener { exception ->
                    Log.e("PlacesActivity", "Error fetching nearby places: ${exception.message}")
                }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }


    private fun createCustomMarker(iconId: Int): BitmapDescriptor {
        val markerWidth = 100 // Desired width of the marker in pixels
        val markerHeight = 100 // Desired height of the marker in pixels
        val iconSize = 30 // Desired size of the icon in pixels

        // Create the base marker bitmap with desired size
        val markerDrawable = ContextCompat.getDrawable(this, R.drawable.marker_custom)?.apply {
            setBounds(0, 0, markerWidth, markerHeight)
        }
        val markerBitmap = Bitmap.createBitmap(markerWidth, markerHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(markerBitmap)
        markerDrawable?.draw(canvas)

        // Create the icon bitmap and resize it to fit within the marker
        val iconDrawable = ContextCompat.getDrawable(this, iconId)?.apply {
            setBounds(0, 0, iconSize, iconSize) // Resize icon to fit within the marker
        }
        val iconBitmap = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
        val iconCanvas = Canvas(iconBitmap)
        iconDrawable?.draw(iconCanvas)

        // Combine the marker and icon
        val markerWithIconBitmap = Bitmap.createBitmap(markerWidth, markerHeight, Bitmap.Config.ARGB_8888)
        val markerWithIconCanvas = Canvas(markerWithIconBitmap)
        markerWithIconCanvas.drawBitmap(markerBitmap, 0f, 0f, null)

        // Position the icon in the center of the marker
        val left = (markerWidth - iconSize) / 2
        val top = (markerHeight - iconSize) / 2
        markerWithIconCanvas.drawBitmap(iconBitmap, left.toFloat(), top.toFloat(), null)

        return BitmapDescriptorFactory.fromBitmap(markerWithIconBitmap)
    }

    private fun setupOptionsBottomSheet(bottomSheetView: View) {
        // Inflate a new view for the bottom sheet
        val inflater = LayoutInflater.from(this)
        val bottomSheetViewMoreOptions = inflater.inflate(R.layout.bottom_sheet_more_options, binding.root, false)

        // Create a new BottomSheetDialog instance
        val bottomSheetDialogMoreOptions = BottomSheetDialog(this)
        bottomSheetDialogMoreOptions.setContentView(bottomSheetViewMoreOptions)

        // obtain details from the more options bottom sheet dialog
        val callOptionLayout: LinearLayout = bottomSheetViewMoreOptions.findViewById(R.id.callOptionLayout)
        val shareOptionLayout: LinearLayout = bottomSheetViewMoreOptions.findViewById(R.id.shareOptionLayout)
        val reportOptionLayout: LinearLayout = bottomSheetViewMoreOptions.findViewById(R.id.reportOptionLayout)

        // obtain data from the place details bottom sheet
        // this prevents another API request by reusing the already available data on the bottom sheet place details
        val phoneNumber: TextView = bottomSheetView.findViewById(R.id.placePhone)
        val placeName: TextView = bottomSheetView.findViewById(R.id.placeName)
        val placeAddress: TextView = bottomSheetView.findViewById(R.id.placeAddress)
        val placeRating: TextView = bottomSheetView.findViewById(R.id.placeRating)

        // Set up call option
        if (phoneNumber.text.toString().isEmpty()) {
            callOptionLayout.visibility = View.GONE
        } else {
            callOptionLayout.visibility = View.VISIBLE
            callOptionLayout.setOnClickListener {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:${phoneNumber.text}")
                }
                bottomSheetDialogMoreOptions.dismiss()
                startActivity(intent)
            }
        }

        shareOptionLayout.setOnClickListener {
            // setup share text
            val shareText = """
            📍 Check out this place:
            
            Name: ${placeName.text}
            Address: ${placeAddress.text}
            Phone: ${phoneNumber.text} ?: "No phone number available"}
            Rating: ${placeRating.text} ?: "No rating available."}
        """.trimIndent()

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, shareText)
                type = "text/plain"
            }

            placeIDFromSearchActivity?.let { it1 -> savedPlace.types?.let { it2 ->
                logSharedPlace(it1, placeName.text.toString(),
                    it2
                )
            } }
            bottomSheetDialogMoreOptions.dismiss()
            startActivity(Intent.createChooser(shareIntent, "Share via"))
        }


        // Set up report an event option
        reportOptionLayout.setOnClickListener {
            // Handle report an event option click
            val intent = Intent(this, ReportEventActivity::class.java)
            intent.putExtra("PLACE_NAME", savedPlace.name)
            intent.putExtra("PLACE_ADDRESS", savedPlace.address)
            val regex = """lat/lng: \((-?\d+\.\d+),(-?\d+\.\d+)\)""".toRegex()
            val destinationLatLng =
                savedPlace.latLngString?.let {
                    regex.find(it)?.let { matchResult ->
                        "${matchResult.groupValues[1]},${matchResult.groupValues[2]}"
                    }
                }
            intent.putExtra("PLACE_LATLNG",destinationLatLng)
            intent.putExtra("PLACE_ID", savedPlace.id)
            val options = ActivityOptions.makeCustomAnimation(
                this,
                R.anim.fade_in,
                R.anim.fade_out
            )
            bottomSheetDialogMoreOptions.dismiss()
            this.startActivity(intent, options.toBundle())
        }

        // Show the bottom sheet dialog
        bottomSheetDialogMoreOptions.show()
    }

    private fun fetchPlaceDetailsFromAPI(placeId: String, callback: (SavedPlace?) -> Unit) {
        val fields = listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.ADDRESS,
            Place.Field.PHONE_NUMBER,
            Place.Field.WEBSITE_URI,
            Place.Field.PHOTO_METADATAS,
            Place.Field.RATING,
            Place.Field.OPENING_HOURS,
            Place.Field.LAT_LNG,
            Place.Field.TYPES
        )
        val request = FetchPlaceRequest.builder(placeId, fields).build()

        placesClient.fetchPlace(request)
            .addOnSuccessListener { response ->
                val place = response.place
                // Convert Place object to SavedPlace object
                val savedPlace = SavedPlace(
                    id = place.id,
                    name = place.name,
                    address = place.address,
                    phoneNumber = place.phoneNumber,
                    latLngString = place.latLng?.toString(),
                    latLng = place.latLng,
                    openingDaysAndTime = place.openingHours?.weekdayText?.joinToString(", "),
                    rating = place.rating?.toString(),
                    websiteUri = place.websiteUri?.toString(),
                    photoMetadataList = place.photoMetadatas ?: emptyList(),
                    types = place.placeTypes?.toString()
                )
                callback(savedPlace) // Return the SavedPlace object via callback



            }
            .addOnFailureListener { exception ->
                Log.e("PlacesActivity", "Error fetching place details", exception)
                callback(null) // Return null if the API call fails
            }
    }

    private fun plotMarkerOnMap(placeId: String, place: SavedPlace) {
        Log.d("PlacesActivity", "Inside of place data on plotMarkerOnMap:  $savedPlace")

        place.latLng?.let { latLng ->
            // Remove the previous red marker if it exists
            currentRedMarker?.let { existingMarker ->
                try {
                    existingMarker.remove()
                    Log.d("PlacesActivity", "Removed previous red marker")
                } catch (e: Exception) {
                    Log.e("PlacesActivity", "Error removing previous red marker", e)
                }
            }

            // Add the new red marker
            val redMarkerOptions = MarkerOptions()
                .position(latLng)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            currentRedMarker = mMap.addMarker(redMarkerOptions)
            currentRedMarker?.tag = placeId
            Log.e("PlacesActivity", "type 3: ${savedPlace.types}")

            // Animate the camera to the new red marker
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
        } ?: run {
            Log.d("PlacesActivity", "Place does not have a LatLng.")
        }
    }

    private fun showPlaceDetailsInBottomSheet(place: SavedPlace) {
        // Inflate the bottom sheet view
        val inflater = LayoutInflater.from(this)
        val bottomSheetView = inflater.inflate(R.layout.bottom_sheet_place_details, binding.root, false)

        // Create a new BottomSheetDialog instance
        val bottomSheetDialog = BottomSheetDialog(this)
        bottomSheetDialog.setContentView(bottomSheetView)


        // Fetch recommendations based on place type
//        place.types?.let { savedPlaceType ->
//            getFilteredPlacesForRecommendationBasedOnType(savedPlaceType, bottomSheetView, bottomSheetDialog)
//        }

        // Update the UI with place details immediately
        updatePlaceDetailsUI(place, bottomSheetView)
        handleOpeningHours(place, bottomSheetView)

        // Fetch the photo metadata asynchronously
        getAndLoadPhotoMetadatasFromPlace(place, bottomSheetView)

        // Handle bottom sheet buttons (More, Close, Save, Directions)
        setupMoreButton(bottomSheetView, bottomSheetDialog)
        setupCloseButton(bottomSheetView, bottomSheetDialog)
        setupSavedPlaces(bottomSheetView, bottomSheetDialog, place)
        setupGetDirectionsButton(bottomSheetView, bottomSheetDialog, place)

        // Log viewed place details
      logViewedPlace(place.id.toString(), place.name.toString(), place.types.toString(), place.latLngString.toString(), place.address.toString())

        // Fetch the distance asynchronously and update the UI when it's ready
        val placeLatLng = place.latLng
        if (placeLatLng != null) {
            checkLocationPermissionAndFetchDistance(null, null, placeLatLng) { distance ->
                // Update the distance in the bottom sheet once it's fetched
                bottomSheetView.findViewById<TextView>(R.id.placeDistance).text = getString(
                    R.string.distance_from_location,
                    distance.toString()
                )
            }
        }

        // Show the bottom sheet dialog
        bottomSheetDialog.show()
    }


    private fun updatePlaceDetailsUI(place: SavedPlace, bottomSheetView: View) {
        // Find the UI elements in the bottom sheet view.
        val placeName: TextView = bottomSheetView.findViewById(R.id.placeName)
        val placeAddress: TextView = bottomSheetView.findViewById(R.id.placeAddress)
        val placePhone: TextView = bottomSheetView.findViewById(R.id.placePhone)
        val placeWebsite: TextView = bottomSheetView.findViewById(R.id.placeWebsite)
        val placeRating: TextView = bottomSheetView.findViewById(R.id.placeRating)


        Log.e("PlacesActivity", "type 5: ${savedPlace.types}")

        // Update the TextViews with the details of the place.
        placeName.text = place.name ?: "Unknown Place" // Set place name or default text if null.
        placeAddress.text = place.address ?: "No Address Available" // Set place address or default text if null.
        placePhone.text = place.phoneNumber ?: "No Phone Available" // Set place phone number or default text if null.
        placeWebsite.text = place.websiteUri ?: "No Website Available" // Set place website or default text if null.
        placeRating.text = getString(R.string.rating, place.rating ?: "No Rating") // Set place rating or default text if null.

        // Handle the case where the place does not have latitude and longitude information.
        if (place.latLng == null) {
            placeAddress.text = getString(R.string.location_details_not_available) // Display a message indicating location details are not available.
        }



    }

    private fun handleOpeningHours(place: SavedPlace, bottomSheetView: View) {
        // Retrieve the opening hours and days from the place object.
        val openingDaysAndTime = place.openingDaysAndTime ?: "No opening days available"

        Log.d("PlacesActivity", "Opening days and time: $openingDaysAndTime")

        // Find the UI elements for displaying hours and status.
        val placeHoursDays: TextView = bottomSheetView.findViewById(R.id.placeHoursDays)
        val placeHoursTime: TextView = bottomSheetView.findViewById(R.id.placeHoursTime)
        val openStatus: TextView = bottomSheetView.findViewById(R.id.openStatus)
        val placeDistance: TextView = bottomSheetView.findViewById(R.id.placeDistance)

        if (openingDaysAndTime == "No opening days available") {
            // Hide the open status if no opening hours information is available.
            openStatus.visibility = View.GONE

            // Adjust margin for placeDistance TextView.
            val layoutParams = placeDistance.layoutParams as ViewGroup.MarginLayoutParams
            layoutParams.marginStart = 0
            placeDistance.layoutParams = layoutParams

            // Clear text for hours and days if not available.
            placeHoursDays.text = getString(R.string.no_available_opening_day_or_time_information)
            placeHoursTime.text = getString(R.string.empty)
            return
        }

        // Split the openingDaysAndTime string into lines
        val daysList = mutableListOf<String>()
        val timesList = mutableListOf<String>()

        // Process each line in the string
        openingDaysAndTime.split(", ").forEach { dayInfo ->
            val parts = dayInfo.split(": ")
            if (parts.size == 2) {
                daysList.add(parts[0])
                timesList.add(parts[1].replace("–", "-").replace("\u202F", " ")) // Replace non-standard dash and non-breaking space
            }
        }

        // Update the UI elements
        placeHoursDays.text = daysList.joinToString("\n")
        placeHoursTime.text = timesList.joinToString("\n")

        // check if the place is open 24 hours
        val isOpen24Hours = timesList.getOrNull(0) == "Open 24 hours"

        // Determine if the place is currently open
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val calendar = Calendar.getInstance()
        val currentDay = calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault())
        val currentTime = calendar.time
        val currentTimeFormatted = timeFormat.format(currentTime)

        Log.d("PlacesActivity", "Current day: $currentDay")
        Log.d("PlacesActivity", "Current time formatted: $currentTimeFormatted")

        var isOpen = false

        // Check the opening hours for the current day
        daysList.forEachIndexed { index, day ->
            Log.d("PlacesActivity", "Checking day: $day")

            if (day.equals(currentDay, ignoreCase = true)) {
                val times = timesList[index].split("-")

                Log.d("PlacesActivity", "Time List : $timesList")
                Log.d("PlacesActivity", "Time split : $times")

                if (times.size == 2) {
                    try {
                        val openTime = timeFormat.parse(times[0].trim())
                        val closeTime = timeFormat.parse(times[1].trim())
                        val currentFormattedTime = timeFormat.parse(currentTimeFormatted)

                        Log.d("PlacesActivity", "Open time: $openTime")
                        Log.d("PlacesActivity", "Close time: $closeTime")
                        Log.d("PlacesActivity", "Current time: $currentFormattedTime")

                        // Handle cases where closing time is the next day
                        if (closeTime != null) {
                            if (closeTime.before(openTime)) {
                                // The place is open overnight
                                if (currentFormattedTime != null) {
                                    if (currentFormattedTime.after(openTime) || currentFormattedTime.before(closeTime)) {
                                        isOpen = true
                                    }
                                }
                            } else {
                                // The place is open during the day
                                if (currentFormattedTime != null) {
                                    if (currentFormattedTime.after(openTime) && currentFormattedTime.before(closeTime)) {
                                        isOpen = true
                                    }
                                }
                            }
                        }
                    } catch (e: ParseException) {
                        Log.e("PlacesActivity", "Error parsing time string: ${e.message}")
                    }
                }
            }
        }
        Log.e("PlacesActivity", "Error parsing time string: ${placeHoursTime.text}")

        // Update the UI based on the open/closed status
        if (isOpen || isOpen24Hours) {
            openStatus.text = getString(R.string.open_status)
            openStatus.setBackgroundResource(R.drawable.open_pill_background)
        } else {
            openStatus.text = getString(R.string.closed)
            openStatus.setBackgroundResource(R.drawable.closed_pill_background)
        }

        openStatus.visibility = View.VISIBLE
    }

    private fun checkLocationPermissionAndFetchDistance(
        place: SavedPlace?, // Nullable place
        bottomSheetView: View?, // Nullable bottomSheetView
        placeLatLng: LatLng? = null, // Optional LatLng parameter
        distanceCallback: (String) -> Unit // Callback to return the distance or an error
    ) {
        // Check if location permissions are granted.
        checkLocationPermission()

        // Retrieve the last known location from the fused location client.
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val userLocation = LatLng(location.latitude, location.longitude) // User's current location.
                val placeLocation = placeLatLng ?: place?.latLng // Use passed LatLng or fallback to place.latLng.

                if (placeLocation != null) {
                    // Build the API request URL parameters.
                    val apiKey = BuildConfig.MAPS_API_KEY
                    val origin = "${userLocation.latitude},${userLocation.longitude}"
                    val destination = "${placeLocation.latitude},${placeLocation.longitude}"

                    // Create a Retrofit instance for API requests.
                    val api = createRetrofitInstance()

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

                                        // Update UI only if bottomSheetView is not null
                                        bottomSheetView?.findViewById<TextView>(R.id.placeDistance)?.text = getString(
                                            R.string.distance_from_location,
                                            distance
                                        )

                                        // Return distance through the callback
                                        distanceCallback(distance)
                                    } else {
                                        // Return "Distance not available" if no routes are found.
                                        distanceCallback("Distance not available")

                                        // Optionally update the UI if bottomSheetView is not null
                                        bottomSheetView?.findViewById<TextView>(R.id.placeDistance)?.text =
                                            getString(R.string.distance_not_available)
                                    }
                                } else {
                                    // Return "Error calculating distance" if the API response fails.
                                    distanceCallback("Error calculating distance")

                                    // Optionally update the UI if bottomSheetView is not null
                                    bottomSheetView?.findViewById<TextView>(R.id.placeDistance)?.text =
                                        getString(R.string.distance_error)
                                }
                            }

                            override fun onFailure(call: Call<PlaceDistanceResponse>, t: Throwable) {
                                // Log the error and return "Error calculating distance"
                                Log.e("RetrofitError", "Error fetching directions: ${t.message}")
                                distanceCallback("Error calculating distance")

                                // Optionally update the UI if bottomSheetView is not null
                                bottomSheetView?.findViewById<TextView>(R.id.placeDistance)?.text =
                                    getString(R.string.distance_error)
                            }
                        })
                } else {
                    // Return "Place location not available" if place location is null.
                    distanceCallback("Place location not available")

                    // Optionally update the UI if bottomSheetView is not null
                    bottomSheetView?.findViewById<TextView>(R.id.placeDistance)?.text =
                        getString(R.string.distance_not_available)
                }
            } else {
                // Return "User location not available" if the user's current location is null.
                distanceCallback("User location not available")

                // Optionally update the UI if bottomSheetView is not null
                bottomSheetView?.findViewById<TextView>(R.id.placeDistance)?.text =
                    getString(R.string.distance_location_not_available)
            }
        }
    }



    private fun getAndLoadPhotoMetadatasFromPlace(place: SavedPlace, bottomSheetView: View): List<PhotoMetadata> {
        // Get the ViewPager2 instance from the bottom sheet view to display photos.
        val placePhoto: ViewPager2 = bottomSheetView.findViewById(R.id.viewPager)

        // Retrieve the list of photo metadata for the place. Use an empty list if none are available.
        val photoMetadatas = place.photoMetadataList

        // Check if there are any photo metadata available.
        if (photoMetadatas.isNotEmpty()) {
            // Load the photos into the ViewPager2.
            loadPhotosIntoViewPager(photoMetadatas, placePhoto)

            // Make the ViewPager2 visible if photos are available.
            placePhoto.visibility = View.VISIBLE
        } else {
            // Hide the ViewPager2 if no photos are available.
            placePhoto.visibility = View.GONE
        }

        return place.photoMetadataList
    }

    private fun setupCloseButton(bottomSheetView: View, bottomSheetDialog: BottomSheetDialog) {
        val btnClose: ImageButton = bottomSheetView.findViewById(R.id.closeButton)
        btnClose.setOnClickListener {
            // Dismiss the bottom sheet dialog
            bottomSheetDialog.dismiss()
        }
    }

    private fun setupGetDirectionsButton(bottomSheetView: View, bottomSheetDialog: BottomSheetDialog, place: SavedPlace) {
        val btnGetDirections: MaterialButton = bottomSheetView.findViewById(R.id.btnGetDirections)
        btnGetDirections.setOnClickListener {
            // Dismiss the bottom sheet dialog
            bottomSheetDialog.dismiss()

            getUserLocation { userLocation ->
                val origin = "${userLocation.latitude},${userLocation.longitude}"
                val regex = """lat/lng: \((-?\d+\.\d+),(-?\d+\.\d+)\)""".toRegex()
                val destinationLatLng =
                    place.latLngString?.let {
                        regex.find(it)?.let { matchResult ->
                            "${matchResult.groupValues[1]},${matchResult.groupValues[2]}"
                        }
                    }
                val destination = "${place.name}==${place.address}==${destinationLatLng}==${place.id}"
                Log.e("PlacesActivity", "Destination Lat lng: ${place.latLngString}")
                Log.e(
                    "PlacesActivity",
                    "Origin: $origin, Destination: $destination"
                )

                if (destination.isNotBlank()) {
                    val intent =
                        Intent(this@PlacesActivity, DirectionsActivity::class.java)
                    intent.putExtra("DESTINATION", destination)
                    intent.putExtra("ORIGIN", origin)
                    startActivity(intent)
                    bottomSheetDialog.dismiss()
                } else {
                    Log.e("PlacesActivity", "Destination is missing")
                }
            }

        }
    }

    private fun loadPhotosIntoViewPager(photoMetadatas: List<PhotoMetadata>, viewPager: ViewPager2) {
        val photoBitmaps = mutableListOf<Bitmap>()

        // Fetch each photo
        photoMetadatas.forEach { photoMetadata ->
            val photoRequest = FetchPhotoRequest.builder(photoMetadata)
                .setMaxWidth(800)
                .setMaxHeight(800)
                .build()
            placesClient.fetchPhoto(photoRequest)
                .addOnSuccessListener { response ->
                    // Get the Bitmap and add it to the list
                    val photoBitmap = response.bitmap
                    photoBitmap.let {
                        photoBitmaps.add(it)
                        Log.d("PlacesActivity", "Bitmaps: $photoBitmap")
                        // Only set the adapter once, when all photos are loaded
                        if (photoBitmaps.size == photoMetadatas.size) {
                            viewPager.adapter = PhotoPagerAdapter(photoBitmaps) // Pass Bitmap list directly
                        }
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("PlacesActivity", "Error fetching photo", exception)
                }
        }
    }

    private fun createRetrofitInstance(): PlaceDistanceService {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://maps.googleapis.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(PlaceDistanceService::class.java)
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE)
        }
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 1000L
        ).apply {
            setMinUpdateIntervalMillis(500L)
        }.build()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            // No need to move the camera here
            // The My Location layer handles the user's location and heading indicator
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val view = currentFocus
            if (view is View) {
                val outRect = Rect()
                view.getGlobalVisibleRect(outRect)
                if (!outRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    view.clearFocus()
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(view.windowToken, 0)
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun logSharedPlace(placeId: String, placeName: String, placeType: String) {
        val sharedPlaceData = mapOf(
            "placeId" to placeId,
            "placeName" to placeName,
            "timestamp" to System.currentTimeMillis(),
            "type" to placeType
        )

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        val database = FirebaseDatabase.getInstance().getReference("Users/$userId/interactions/sharedPlaces")
        val newKey = database.push().key // Create a unique key for each shared place

        newKey?.let {
            database.child(it).setValue(sharedPlaceData)
        }
    }

    private fun logViewedPlace(placeId: String, placeName: String, placeType: String, placeLatLng: String, placeAddress: String) {
        val viewedPlaceData = mapOf(
            "placeId" to placeId,
            "placeName" to placeName,
            "placeAddress" to placeAddress,
            "placeLatLng" to placeLatLng,
            "timestamp" to System.currentTimeMillis(),
            "type" to placeType

        )

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        val database = FirebaseDatabase.getInstance().getReference("Users/$userId/interactions/viewedPlaces")
        val newKey = database.push().key // Create a unique key for each viewed place

        newKey?.let {
            database.child(it).setValue(viewedPlaceData)
        }
    }

    private fun logSavedPlace(placeId: String, placeName: String, placeType: String) {
        val savedPlaceData = mapOf(
            "placeId" to placeId,
            "placeName" to placeName,
            "timestamp" to System.currentTimeMillis(),
            "type" to placeType
        )

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        val database = FirebaseDatabase.getInstance().getReference("Users/$userId/interactions/savePlaces")
        val newKey = database.push().key // Create a unique key for each saved place

        newKey?.let {
            database.child(it).setValue(savedPlaceData)
        }
    }

    private fun getFilteredPlacesForRecommendationBasedOnType(currentPlaceTypesString: String, bottomSheetView: View, bottomSheetDialog: BottomSheetDialog) {
        // Clean up the input string to get a list of place types
        val currentPlaceTypes = currentPlaceTypesString
            .replace("[", "") // Remove opening bracket
            .replace("]", "") // Remove closing bracket
            .split(",") // Split by commas to get individual place types
            .map { it.trim() } // Remove any extra spaces around each type
            .filter { it.isNotEmpty() } // Filter out any empty strings

        Log.e("PlacesActivity", "Current place types: $currentPlaceTypes")

        val placeFields = listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.ADDRESS,
            Place.Field.TYPES,
            Place.Field.RATING,
            Place.Field.USER_RATINGS_TOTAL,
            Place.Field.LAT_LNG,
            Place.Field.PHOTO_METADATAS
        )

        // Construct search query based on cleaned-up current place types
        val query = currentPlaceTypes.joinToString(" OR ")
        Log.e("PlacesActivity", "Search query for places: $query")

        val searchByTextRequest = SearchByTextRequest.builder(query, placeFields)
            .setMaxResultCount(20)
            .build()

        // Get the current location first
        getCurrentLocation {
            placesClient.searchByText(searchByTextRequest)
                .addOnSuccessListener { response ->
                    Log.e("PlacesActivity", "Successfully retrieved places. Total places found: ${response.places.size}")

                    val places: List<Place> = response.places
                    placesList.clear()

                    // Populate placesList and calculate scores and distances
                    places.forEach { place ->
                        // Ensure place.types is not null and convert to a list of strings
                        val placeTypes = place.placeTypes?.map { it.toString() } ?: emptyList()

                        // Calculate similarity based on common place types
                        val commonTypes = currentPlaceTypes.intersect(placeTypes.toSet()).size
                        val score = commonTypes.toDouble() // Higher number of common types results in a higher score

                        Log.e("PlacesActivity", "Place: ${place.name}, Common types: $commonTypes")
                        Log.e("PlacesActivity", "Current place types: $currentPlaceTypes")
                        Log.e("PlacesActivity", "Place types: $placeTypes")

                        // Calculate distance from user's current location using callback
                        val placeLatLng = place.latLng

                        if (placeLatLng != null) {
                            checkLocationPermissionAndFetchDistance(null, null, placeLatLng) { distance ->
                                val placeModel = place.id?.let {
                                    val cleanDistance = distance.replace("[^\\d.]".toRegex(), "")
                                    val distanceDouble = if (cleanDistance.isNotEmpty()) cleanDistance.toDouble() else 0.0
                                    RecommendedPlace(
                                        placeId = it,
                                        name = place.name ?: "",
                                        address = place.address ?: "",
                                        placeTypes = placeTypes,
                                        score = score, // Assign score based on similarity
                                        rating = place.rating ?: 0.0, // Store rating
                                        numReviews = place.userRatingsTotal ?: 0, // Store number of reviews
                                        photoMetadata = place.photoMetadatas?.firstOrNull(),
                                        distance = distanceDouble,
                                        distanceString = distance, // Use distance from callback,
                                    )
                                }

                                if (placeModel != null) {
                                    placesList.add(placeModel)

                                    // Once all places are added, sort by score and distance
                                    if (placesList.size == response.places.size) {
                                        // Sort places first by score (descending) and then by distance (ascending)

                                        placesList.sortWith(compareByDescending<RecommendedPlace> { it.score }.thenBy { it.distance })
                                        Log.e("PlacesActivity", "place list size: ${placesList.size}")
                                        Log.e("PlacesActivity", "place : ${placeModel }")


                                        // Get the top 10 after sorting
                                        val top10 = placesList.take(10)

                                        Log.e("PlacesActivity", "TOP 10 PLACE LIST: $top10")


                                        // Set up the RecyclerView
                                        val recommendationRecyclerview: RecyclerView = bottomSheetView.findViewById(R.id.recommendationRecyclerView)
                                        recommendationRecyclerview.layoutManager = LinearLayoutManager(this@PlacesActivity, LinearLayoutManager.HORIZONTAL, false)

                                        val adapter = RecommendedPlaceAdapter(top10, false, placesClient) { places ->
                                            fetchPlaceDetailsFromAPI(places.placeId) { savedPlace ->
                                                savedPlace?.let {
                                                    this.savedPlace = it
                                                    bottomSheetDialog.dismiss()
                                                    plotMarkerOnMap(places.placeId, savedPlace)
                                                    showPlaceDetailsInBottomSheet(savedPlace)
                                                } ?: run {
                                                    Log.e("PlacesActivity", "Failed to fetch place details from dashboard")
                                                }
                                            }
                                        }

                                        recommendationRecyclerview.adapter = adapter
                                    }
                                }
                            }
                        }
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("PlacesActivity", "Error retrieving places", exception)
                }
        }

    }

     //Calculate the score based on various factors like preferences, rating, reviews, and distance
     private fun calculateScore(
         place: Place,
         placeTypes: List<String>,
         preferredPlaces: List<String>,
         userLocation: LatLng
     ): Double {
         // Step 1: Initialize base weight for all place types (default weight is 1.0 for preferred, 0.5 for others)
         val weights = mutableMapOf<String, Double>().apply {
             placeTypes.forEach { type -> this[type] = 0.5 } // Base weight for all place types
             preferredPlaces.forEach { placeType ->
                 this[placeType] = (this[placeType] ?: 0.5) + 1.0 // Increase weight for preferred place types
             }
         }

         // Step 2: Extract ratings and number of reviews
         val rating = place.rating ?: 0.0
         val numReviews = place.userRatingsTotal ?: 0

         // Step 3: Calculate distance between the user and the place
         val placeLocation = place.latLng
         val distance = placeLocation?.let { calculateDistance(userLocation, it) } ?: 0.0

         // Step 4: Calculate the score based on the weighted sum
         return (placeTypes.sumOf { type -> weights[type] ?: 0.0 } * 2) + // Double the weight based on place types
                 (rating * 0.3) +  // Lower weight for rating (normalized by multiplying by 0.3)
                 (numReviews / 100.0) * 0.1 - // Lower weight for normalized number of reviews
                 (distance / 1000.0 * 2) // Subtract the normalized distance (distance in kilometers) with a higher impact
     }



    private fun calculateDistance(start: LatLng, end: LatLng): Double {
        val results = FloatArray(1)
        Location.distanceBetween(
            start.latitude,
            start.longitude,
            end.latitude,
            end.longitude,
            results
        )
        return results[0].toDouble() // Distance in meters
    }

    private fun getCurrentLocation(callback: (LatLng) -> Unit) {
        // Check if the location permission is granted
        if (ContextCompat.checkSelfPermission(
                this,
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
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("SearchActivity", "Failed to get location", exception)
                }
        } else {
            // Permission is not granted, request it
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1
            )
        }
    }
    
    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }
}


//Users {
//    e5Ndfru2QWRCkzydykw9Pm3QkTz1 {
//        interactions {
//            searchedPlaces {
//                -O8V8Taz2_U92GG5PTvT{
//                    query:"J.CO Donuts & Coffee, Cebu IT Park",
//                    timestamp:1728189948973
//                    type:[coffee_shop, cafe, store, food, point_of_interest, establishment]
//                }
//
//                -O8V8V77dX0TgY2Cic-D {
//                    query:"South Sea Auto Parts",
//                    timestamp:1728189955189
//                    type:[auto_parts_store, car_repair, store, point_of_interest, establishment]
//                }
//            },
//            viewedPlaces{
//                -O8VlbsAX3YFMsdvO-Wb {
//                    placeId:"ChIJ6xO2pQSZqTMRX0ROdpMZGvI",
//                    placeName:"OYO 628 Bamboo Bay Rli",
//                    timestamp:1728200472717,
//                    type:"[lodging, point_of_interest, establishment]"
//                }
//
//                -O8VlctSBa_Tm3xAA242 {
//                    placeId:"ChIJo0FUhwSZqTMR4EsAV9Vv4NY",
//                    placeName:"Alpa City Suites",
//                    timestamp:1728200476895,
//                    type:"[lodging, point_of_interest, establishment]"
//
//                }
//
//            }
//        }
//    }
//}