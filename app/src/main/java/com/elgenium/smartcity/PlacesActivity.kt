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
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.elgenium.smartcity.contextuals.MealPlaceRecommendationManager
import com.elgenium.smartcity.contextuals.RainLikelihoodCalculator
import com.elgenium.smartcity.contextuals.SimilarPlacesRecommendationHelper
import com.elgenium.smartcity.databinding.ActivityPlacesBinding
import com.elgenium.smartcity.databinding.BottomSheetEventDetailsBinding
import com.elgenium.smartcity.models.Event
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
import com.elgenium.smartcity.viewpager_adapter.EventImageAdapter
import com.elgenium.smartcity.viewpager_adapter.PhotoPagerAdapter
import com.elgenium.smartcity.work_managers.ImageUploadWorker
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
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PointOfInterest
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.PhotoMetadata
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPhotoRequest
import com.google.android.libraries.places.api.net.FetchPlaceRequest
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.await
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
    private var isSimilarPlacesEnabled = false
    private var isFewerLandmarks = false
    private var mapTheme = "Aubergine"
    private var isTrafficOverlayEnabled = false
    private lateinit var mealPlaceRecommender: MealPlaceRecommendationManager
    private lateinit var sharedPreferences: SharedPreferences
    private var isActivityVisible = false
    private val eventLatlngs = ArrayList<LatLng>()
    private val eventList = mutableListOf<Event>()
    private lateinit var rainLikelihoodCalculator: RainLikelihoodCalculator



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlacesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // Singleton object to set the color of the navigation bar making it more personalized
        NavigationBarColorCustomizerHelper.setNavigationBarColor(this, R.color.secondary_color)
        mealPlaceRecommender = MealPlaceRecommendationManager(this)
        rainLikelihoodCalculator = RainLikelihoodCalculator(this)

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

        binding.fabActivityLauncher.setOnClickListener {
            navigateToActivity(this, MyActivitiesActivity::class.java, true)
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

        Log.e("PlacesActivity", "Event latlngs: $eventLatlngs")

        loadEventsFromFirebase()
    }

    private fun loadEventsFromFirebase() {
        val firebaseAuth = FirebaseAuth.getInstance()
        val user = firebaseAuth.currentUser

        if (user != null) {
            // User is authenticated, proceed with loading events
            val database = FirebaseDatabase.getInstance().getReference("Events")
            database.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    eventList.clear() // Clear the list before adding new data

                    // Check if the snapshot contains any data
                    if (snapshot.exists()) {
                        for (eventSnapshot in snapshot.children) {
                            val event = eventSnapshot.getValue(Event::class.java)
                            Log.d("EventMarker", "Images type: ${event?.images?.javaClass?.simpleName}")

                            event?.let { eventList.add(it) }
                        }

                        Log.e("EventMarker", "ALL EVENTS AT LOADEVENTSFROM FIREBASE: $eventList")
                    } else {
                        Log.e("EventMarker", "No events found in Firebase.")
                    }

                    // Only plot markers if the eventList is not empty
                    if (eventList.isNotEmpty()) {
                        plotMarkers()
                    } else {
                        Log.e("EventsActivity", "Event list is empty. No markers to plot.")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("EventMarker", "Error loading data: ${error.message}")
                }
            })
        } else {
            // User is not authenticated
            Log.e("EventMarker", "User is not authenticated. Unable to load events.")
            // Optionally handle the UI for non-authenticated state
        }
    }


    @SuppressLint("PotentialBehaviorOverride")
    private fun plotMarkers() {
        // Log start of the plotting process
        Log.d("EventMarker", "Starting plotMarkers function")

        // Clear existing markers if necessary
        mMap.clear()
        Log.d("EventMarker", "Cleared existing markers on the map")

        // Create a list to store the LatLng objects for the bounds
        val boundsBuilder = LatLngBounds.Builder()
        var hasMarkers = false // Flag to check if we have added any markers

        // Get the current date without the time (only year, month, and day)
        val currentDate = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        Log.d("EventMarker", "Current date (no time): $currentDate")

        // Define a date format to parse the endedDateTime string
        val dateFormat = SimpleDateFormat("dd/MM/yyyy h:mm a", Locale.getDefault())
        Log.d("EventMarker", "Date format initialized")

        // Iterate through the eventList to extract the LatLng from placeLatLng
        for ((index, event) in eventList.withIndex()) {
            Log.d("EventMarker", "Processing event at index $index: ${event.eventName}")

            // Parse the event's endedDateTime string to a Date object
            val eventDate = try {
                event.endedDateTime?.let { dateFormat.parse(it) }
            } catch (e: Exception) {
                Log.e("EventMarker", "Failed to parse endedDateTime for event ${event.eventName}: ${event.endedDateTime}", e)
                null
            }

            // Check if eventDate is the same as currentDate
            if (eventDate != null && isSameDay(eventDate, currentDate)) {
                Log.d("EventMarker", "Event ${event.eventName} is on the current date")

                event.placeLatLng?.let { latLngString ->
                    Log.d("EventMarker", "Attempting to parse LatLng from: $latLngString")

                    // Use regex to extract latitude and longitude
                    val regex = """lat/lng: \(([^,]+),([^)]+)\)""".toRegex()
                    val matchResult = regex.find(latLngString)

                    matchResult?.let {
                        val latitude = it.groups[1]?.value?.toDoubleOrNull()
                        val longitude = it.groups[2]?.value?.toDoubleOrNull()

                        if (latitude != null && longitude != null) {
                            Log.d("EventMarker", "Parsed LatLng for event ${event.eventName}: Lat=$latitude, Lng=$longitude")

                            // Create LatLng object
                            val latLng = LatLng(latitude, longitude)

                            // Determine the icon for the event based on its category or other properties
                            val iconId = getIconResourceForEventCategory(event.eventCategory)
                            Log.d("EventMarker", "Icon resource ID for ${event.eventCategory}: $iconId")

                            // Create a custom marker using the iconId
                            val customMarker = createCustomMarker(iconId)
                            Log.d("EventMarker", "Custom marker created")

                            // Add a marker for each event and set the event as a tag
                            val marker = mMap.addMarker(
                                MarkerOptions()
                                    .position(latLng)
                                    .title(event.location)
                                    .snippet(event.eventName)
                                    .icon(customMarker) // Use the custom marker
                            )
                            marker?.tag = event // Set the event object as a tag
                            Log.d("EventMarker", "Marker added for ${event.eventName} at Lat: $latitude, Lng: $longitude")

                            // Include the LatLng in the bounds builder
                            boundsBuilder.include(latLng)
                            hasMarkers = true // Set flag to true as we have added a marker
                        } else {
                            Log.e("EventMarker", "Failed to parse latitude/longitude for event ${event.eventName}")
                        }
                    } ?: Log.e("EventMarker", "Regex did not match for LatLng string: $latLngString")
                } ?: Log.d("EventMarker", "Event ${event.eventName} has no placeLatLng value")
            } else {
                Log.d("EventMarker", "Event ${event.eventName} is not on the current date, skipping")
            }
        }

        // Check if we have added any markers before building bounds
        if (hasMarkers) {
            Log.d("EventMarker", "Building bounds and moving camera")
            val bounds = boundsBuilder.build()
            mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100)) // Add padding as needed
        } else {
            Log.e("EventMarker", "No markers to display for the current date.")
        }

        // Set marker click listener
        mMap.setOnMarkerClickListener { marker ->
            val event = marker.tag as? Event
            if (event != null) {
                Log.d("EventMarker", "Marker clicked for event: ${event.eventName}")
                showEventDetailsBottomSheetDialog(event) // Show bottom sheet with event details
            } else {
                Log.e("EventMarker", "Marker has no event tag")
            }
            true // Return true to indicate that the event was consumed
        }

        Log.d("EventMarker", "plotMarkers function completed")
    }



    // Helper function to check if two dates are on the same day
    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val calendar1 = Calendar.getInstance().apply { time = date1 }
        val calendar2 = Calendar.getInstance().apply { time = date2 }
        return calendar1.get(Calendar.YEAR) == calendar2.get(Calendar.YEAR) &&
                calendar1.get(Calendar.DAY_OF_YEAR) == calendar2.get(Calendar.DAY_OF_YEAR)
    }

    private fun getIconResourceForEventCategory(category: String?): Int {
        // Convert the category to lowercase and trim any spaces
        return when (category?.trim()?.lowercase(Locale.getDefault())) {
            "concerts & live performances" -> R.drawable.concert
            "festivals & celebrations" -> R.drawable.festival
            "sales & promotions" -> R.drawable.fare
            "workshops & seminars" -> R.drawable.workshop
            "community events" -> R.drawable.community_events
            "outdoor & adventure events" -> R.drawable.outdoor
            else -> R.drawable.events
        }
    }







    private fun showEventDetailsBottomSheetDialog(event: Event) {
        val bottomSheetDialog = BottomSheetDialog(this)
        val bottomSheetBinding = BottomSheetEventDetailsBinding.inflate(LayoutInflater.from(this))
        bottomSheetDialog.setContentView(bottomSheetBinding.root)

        // Format the date and time
        val inputFormat = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
        val outputFormat = SimpleDateFormat("MMMM d, yyyy 'at' h:mm a", Locale.getDefault())

        // Populate event details in bottom sheet
        with(bottomSheetBinding) {
            eventName.text = event.eventName
            placeName.text = event.location
            placeAddress.text = event.additionalInfo
            eventCategory.text = event.eventCategory

            // Check if event category has Others placeholder
            val category = event.eventCategory ?: ""
            eventCategory.text = if (category.contains("Others: ", ignoreCase = true)) {
                category.replace("Others: ", "").trim()
            } else {
                category
            }

            // Set up ViewPager2 with images
            val imageUrls = event.images ?: emptyList()
            val imageAdapter = EventImageAdapter(imageUrls)
            viewPager.adapter = imageAdapter

            // calculate distance from the event place
            getUserLocation { userLocation ->
                if (userLocation != null) {
                    CoroutineScope(Dispatchers.Main).launch {
                        val distance = getTravelDistance(event, userLocation).await()
                        bottomSheetBinding.eventDistance.text = formatDistance(distance)
                    }
                } else {
                    bottomSheetBinding.eventDistance.text = getString(R.string.location_not_available)
                }
            }

            // format date and time as e.g., September 8, 2024 at 8:48 PM
            eventTimeStartedValue.text = formatDate(event.startedDateTime, inputFormat, outputFormat)
            eventTimeEndedValue.text = formatDate(event.endedDateTime, inputFormat, outputFormat)
            eventDescriptionDetails.text = event.eventDescription

            // close button
            bottomSheetBinding.closeButton.setOnClickListener {
                bottomSheetDialog.dismiss()
            }

            setupGetDirectionsButton(event, bottomSheetBinding, bottomSheetDialog)
            setupSaveButton(bottomSheetBinding, bottomSheetDialog, event)
            setupShareButton(bottomSheetBinding, bottomSheetDialog)
        }

        bottomSheetDialog.show()
    }

    private fun setupGetDirectionsButton(event: Event, bottomSheetBinding: BottomSheetEventDetailsBinding, bottomSheetDialog: BottomSheetDialog) {

        bottomSheetBinding.btnGetDirections.setOnClickListener{
            bottomSheetDialog.dismiss()

            Log.e("EventMarker", "Start Navigate button clicked")
            val intent =
                Intent(this@PlacesActivity, StartNavigationsActivity::class.java)
            intent.putExtra("TRAVEL_MODE", "DRIVE")
            intent.putExtra("IS_SIMULATED", false)
            intent.putExtra("ROUTE_TOKEN", "NO_ROUTE_TOKEN")
            intent.putStringArrayListExtra(
                "PLACE_IDS",
                arrayListOf(event.placeId)
            )
            startActivity(intent)
        }
    }

    private fun setupShareButton(bottomSheetBinding: BottomSheetEventDetailsBinding, bottomSheetDialog: BottomSheetDialog) {

        bottomSheetBinding.btnShare.setOnClickListener{
            val eventName = bottomSheetBinding.eventName.text.toString()
            val eventPlace = bottomSheetBinding.placeName.text.toString()
            val eventAddress = bottomSheetBinding.placeAddress.text.toString()
            val timeStarted = bottomSheetBinding.eventTimeStarted.text.toString()
            val timeEnded = bottomSheetBinding.eventTimeEnded.text.toString()
            val eventDescription = bottomSheetBinding.eventDescriptionDetails.text.toString()
            bottomSheetDialog.dismiss()

            val shareText = """
            📍 Let's go to this event:
            
                Event Name: $eventName
                Place: $eventPlace
                Address: $eventAddress
                Time Started: $timeStarted
                Time Ended: $timeEnded
                Event Details: $eventDescription
            """.trimIndent()

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, shareText)
                type = "text/plain"
            }

            startActivity(Intent.createChooser(shareIntent, "Share via"))
        }
    }

    private fun setupSaveButton(bottomSheetBinding: BottomSheetEventDetailsBinding, bottomSheetDialog: BottomSheetDialog, event: Event) {
        bottomSheetBinding.btnSave.setOnClickListener {
            val user = FirebaseAuth.getInstance().currentUser
            if (user != null) {
                val userId = user.uid
                val databaseReference = FirebaseDatabase.getInstance().getReference("Users")
                    .child(userId)
                    .child("saved_events")



                // Check if the event already exists
                databaseReference.orderByChild("checker").equalTo(event.checker)
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (snapshot.exists()) {
                                // Event already exists
                                bottomSheetDialog.dismiss()
                                LayoutStateManager.showFailureLayout(this@PlacesActivity, "The event has already been saved to Favorites. Please select another event.", "Return to Events", EventsActivity::class.java)
                            } else {
                                // Event does not exist, proceed with saving
                                val newEventRef = databaseReference.push() // Create a new child node

                                val savedEventData = mapOf(
                                    "eventName" to event.eventName,
                                    "location" to event.location,
                                    "additionalInfo" to event.additionalInfo,
                                    "eventCategory" to event.eventCategory,
                                    "startedDateTime" to event.startedDateTime,
                                    "endedDateTime" to event.endedDateTime,
                                    "eventDescription" to event.eventDescription,
                                    "placeLatLng" to event.placeLatLng,
                                    "placeId" to event.placeId,
                                    "submittedBy" to event.submittedBy,
                                    "submittedAt" to event.submittedAt,
                                    "userId" to event.userId,
                                    "images" to event.images, // Save image URLs directly
                                    "checker" to event.checker
                                )

                                bottomSheetDialog.dismiss() // Close the bottom sheet dialog
                                LayoutStateManager.showLoadingLayout(this@PlacesActivity, "Please wait while we are saving your event")

                                // Save to Firebase Realtime Database
                                newEventRef.setValue(savedEventData).addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        Toast.makeText(bottomSheetBinding.root.context, "Event saved successfully!", Toast.LENGTH_SHORT).show()
                                        LayoutStateManager.showSuccessLayout(this@PlacesActivity, "Event saved successfully!", "You can now view this saved event in Favorites.", EventsActivity::class.java)
                                    } else {
                                        Toast.makeText(bottomSheetBinding.root.context, "Failed to save event.", Toast.LENGTH_SHORT).show()
                                        LayoutStateManager.showFailureLayout(this@PlacesActivity, "Something went wrong. Please check your connection or try again.", "Return to Events", EventsActivity::class.java)
                                    }
                                }
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Toast.makeText(bottomSheetBinding.root.context, "Error checking saved events: ${error.message}", Toast.LENGTH_SHORT).show()
                        }
                    })
            } else {
                Toast.makeText(bottomSheetBinding.root.context, "User not signed in!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun formatDate(dateString: String?, inputFormat: SimpleDateFormat, outputFormat: SimpleDateFormat): String {
        return try {
            val date = dateString?.let { inputFormat.parse(it) }
            date?.let { outputFormat.format(it) } ?: ""
        } catch (e: ParseException) {
            ""
        }
    }

    private fun formatDistance(distance: Double?): String {
        return distance?.let {
            String.format(Locale.US, "%.1f km away from location", it) // Format to one decimal place
        } ?: "Distance not available"
    }

    private fun getTravelDistance(event: Event, userLocation: LatLng): Deferred<Double?> = CoroutineScope(Dispatchers.IO).async {
        val placeLatLngString = event.placeLatLng ?: return@async null
        val placeLatLng = parseLatLng(placeLatLngString) ?: return@async null
        val (eventLat, eventLng) = placeLatLng

        val origin = "${userLocation.latitude},${userLocation.longitude}"
        val destination = "$eventLat,$eventLng"
        val apiKey = BuildConfig.MAPS_API_KEY

        val retrofit = Retrofit.Builder()
            .baseUrl("https://maps.googleapis.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(PlaceDistanceService::class.java)

        try {
            val response = service.getDirections(origin, destination, apiKey).await()
            val distanceValue = response.routes[0].legs[0].distance.value
            val distanceInKm = distanceValue / 1000.0 // Convert meters to kilometers
            distanceInKm
        } catch (e: Exception) {
            Log.e("EventsActivity", "API Call Failure for event ${event.eventName}: ${e.message}")
            null
        }
    }

    private fun parseLatLng(latLngString: String): Pair<Double, Double>? {
        // Regular expression to match the latitude and longitude
        val regex = Regex("""lat/lng: \(([^,]+),([^)]*)\)""")
        val matchResult = regex.find(latLngString)
        val (latitude, longitude) = matchResult?.destructured ?: return null
        return Pair(latitude.toDouble(), longitude.toDouble())
    }


















    override fun onResume() {
        super.onResume()
        isActivityVisible = true
//        fetchRecommendedMealPlaces() // Call this method to check for recommendations
    }

    override fun onPause() {
        super.onPause()
        isActivityVisible = false
    }


    private fun fetchRecommendedMealPlaces() {
        val currentDate = getCurrentDate()
        val lastInteractionDate = sharedPreferences.getString("lastInteractionDate", null)
        Log.d("Recommendation", "CURRENT DATE: $currentDate")
        Log.d("Recommendation", "LAST INTERACTION DATE: $lastInteractionDate")


        // If it's a new day, reset the recommendations
        if (currentDate != lastInteractionDate) {
            resetRecommendations()
        }

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

        Log.d("Recommendation", "CURRENT HOUR: $currentHour")
        Log.d("Recommendation", "HAS EXECUTED FOR $mealTime: $hasDisplayedRecommendation")
        Log.d("Recommendation", "LAST INTERACTION DATE:  $lastInteractionDate, CURRENT DATE: $currentDate")


        if (!hasDisplayedRecommendation) {
            Log.d("Recommendation", "Performing text search for meal places...")

            // Get recommended place types
            val recommendedPlaceTypes = mealPlaceRecommender.mealTimePlaceMappings[mealTime]

            if (!recommendedPlaceTypes.isNullOrEmpty()) {
                mealPlaceRecommender.performTextSearch(placesClient, recommendedPlaceTypes, this, null, null, null, false) {
                    // Check if the activity is still visible
                    if (!isActivityVisible || isFinishing || isDestroyed) {
                        Log.e("MealRecommendation", "Activity is not valid to show the dialog.")
                        return@performTextSearch // Early return if the activity is not valid
                    }

                    Log.d("Recommendation", "Text search for meal places complete.")
                    // Mark as displayed
                    sharedPreferences.edit().putBoolean("hasDisplayedRecommendation_$mealTime", true).apply()
                    sharedPreferences.edit().putString("lastInteractionDate", currentDate).apply()
                }
            } else {
                Log.e("MealRecommendationActivity", "No recommended place types found for meal time: $mealTime")
            }
        }

    }

    // Helper function to reset recommendation flags
    private fun resetRecommendations() {
        val editor = sharedPreferences.edit()
        editor.putBoolean("hasDisplayedRecommendation_breakfast", false)
        editor.putBoolean("hasDisplayedRecommendation_lunch", false)
        editor.putBoolean("hasDisplayedRecommendation_snack", false)
        editor.putBoolean("hasDisplayedRecommendation_dinner", false)
        editor.putBoolean("hasDisplayedRecommendation_late-night", false) // Reset late-night flag
        editor.apply()

        Log.d("Recommendation", "Recommendation flags have been reset for a new day.")
    }

    // Helper function to get the current date as a string
    private fun getCurrentDate(): String {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1 // Calendar.MONTH is zero-based
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        return "$year-$month-$day" // Return date in the format "YYYY-MM-DD"
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
        isSimilarPlacesEnabled = sharedPreferences.getBoolean("similar_place", false)

        // Optionally log the retrieved value
        Log.e("Preferences", "mapTheme at retrievePreferences theme: $mapTheme")
        Log.e("Preferences", "isFewerLabels at retrievePreferences labels: $isFewerLabels")
        Log.e("Preferences", "isFewerLandmarks at retrievePreferences landmarks: $isFewerLandmarks")
        Log.e(
            "Preferences",
            "isTrafficOverlayEnabled at retrievePreferences traffic overlay: $isTrafficOverlayEnabled"
        )
        Log.e(
            "Preferences",
            "isSimilarPlacesEnabled at retrievePreferences traffic overlay: $isSimilarPlacesEnabled"
        )


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

        Log.i("POI", "POI clicked: ${poi.name} at location: ${poi.latLng}")

        currentRedMarker?.remove()
        Log.i("POI", "Previous marker removed")

        // Handle the POI click event
        currentRedMarker = mMap.addMarker(
            MarkerOptions()
                .position(poi.latLng)
                .title(poi.name)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)) // Red marker
        )
        Log.i("POI", "New red marker added for POI: ${poi.name}")

        // Optionally, move the camera to the new marker
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(poi.latLng, 15f))
        Log.i("POI", "Camera moved to POI location: ${poi.latLng}")

        poi.placeId?.let { placeId ->
            Log.i("POI", "Fetching details for placeId: $placeId")

            fetchPlaceDetailsFromAPI(placeId) { savedPlace ->
                savedPlace?.let {
                    this.savedPlace = it
                    Log.i("POI", "Place details fetched successfully for placeId: $placeId, Name: ${it.name}")

                    // Update UI or do something with the savedPlace
                    plotMarkerOnMap(placeId, savedPlace) // Use the Place object to plot the marker
                    Log.i("POI", "Marker plotted on map for savedPlace: ${it.name}")

                    showPlaceDetailsInBottomSheet(it) // Use the Place object to show details
                    Log.i("POI", "Place details shown in bottom sheet for: ${it.name}")
                } ?: run {
                    // Handle the case where savedPlace is null with an error log
                    Log.e("POI", "Failed to fetch place details for placeId: $placeId, POI name: ${poi.name}")
                }
            }
        } ?: run {
            // Log an error if the placeId itself is null
            Log.e("POI", "Place ID is null for POI name: ${poi.name}")
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
                Log.e("PlacesActivity", "User ID retrieved: $userId")

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
                val placeData = SavedPlace(
                    id = place.id,
                    name = placeName,
                    address = placeAddress,
                    phoneNumber = placePhoneNumber,
                    latLngString = place.latLng.toString(),
                    openingDaysAndTime = place.openingDaysAndTime ?: "No opening days available",
                    rating = placeRating,
                    websiteUri = placeWebsite,
                    distance = placeDistance,
                    types = place.types
                )

                if (placeData == null) {
                    Log.e("PlacesActivity", "Place data is null. Ensure placeIDFromSearchActivity is correctly set.")
                    return@setOnClickListener
                }

                Log.e("PlacesActivity", "Place data prepared: $placeData")



                // Save textual data directly
                saveTextualData(userRef, placeData)

            } else {
                // Handle case where user is not authenticated
                Log.e("PlacesActivity", "User not authenticated. Unable to save place.")
                Toast.makeText(this, "User not authenticated. Please log in.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // New function to save textual data
    private fun saveTextualData(userRef: DatabaseReference, placeData: SavedPlace) {
        // Check for existing records first
        userRef.orderByChild("placeId").equalTo(placeData.id).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    Log.e("PlacesActivity", "Place already exists in database with ID: ${placeData.id}")
                    // Place already exists, show a message or handle accordingly
                    LayoutStateManager.showFailureLayout(
                        this@PlacesActivity,
                        "This place is already saved in the Favorites section. Please select another place to save.",
                        "Return to Places",
                        PlacesActivity::class.java
                    )
                } else {
                    Log.e("PlacesActivity", "Place does not exist in database. Proceeding with save.")
                    // Proceed to save the textual data
                    val newPlaceRef = userRef.push()
                    newPlaceRef.setValue(placeData).addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            // Successfully saved the textual data
                            uploadPlaceImages(newPlaceRef.key ?: "NO ID")
                            LayoutStateManager.showSuccessLayout(
                                this@PlacesActivity,
                                "Place saved successfully!",
                                "You can now view the saved places under the Favorites screen.",
                                PlacesActivity::class.java
                            )
                            Log.d("PlacesActivity", "Textual data saved successfully.")

                        } else {
                            Log.e("PlacesActivity", "Error saving place data", task.exception ?: Exception("Unknown error"))
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle possible errors
                Log.e("PlacesActivity", "Database error while checking for existing place", error.toException())
            }
        })
    }

    private fun enqueueImageUpload(photoBitmap: Bitmap, placeName: String, savedPlaceId: String?) {
        // Save the bitmap to a temporary file
        val imageFile = File(applicationContext.cacheDir, "${placeName}_${System.currentTimeMillis()}.jpg")
        Log.d("PlacesActivity", "Saving bitmap to temporary file: ${imageFile.absolutePath}")

        try {
            val outputStream = FileOutputStream(imageFile)
            photoBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            outputStream.close()
            Log.d("PlacesActivity", "Successfully saved bitmap for $placeName to ${imageFile.absolutePath}")
        } catch (e: IOException) {
            Log.e("PlacesActivity", "Error saving bitmap for $placeName", e)
            return // Exit the function if there was an error saving the image
        }

        // Create input data for the worker
        val inputData = workDataOf(
            "imagePath" to imageFile.absolutePath,
            "placeName" to placeName,
            "savedPlaceId" to savedPlaceId // Pass the unique Firebase ID
        )

        // Create a work request
        val uploadWorkRequest = OneTimeWorkRequestBuilder<ImageUploadWorker>()
            .setInputData(inputData)
            .build()

        Log.d("PlacesActivity", "Enqueuing work request for image upload for place: $placeName with ID: $savedPlaceId")

        // Enqueue the work request
        WorkManager.getInstance(applicationContext).enqueue(uploadWorkRequest)
    }

    private fun uploadPlaceImages(savedPlaceId: String) {
        val photoMetadatas = savedPlace.photoMetadataList
        val limitedPhotoMetadatas = if (photoMetadatas.size > 4) photoMetadatas.take(4) else photoMetadatas

        Log.d("PlacesActivity", "Starting upload of images for place with ID: $savedPlaceId. Number of photos to upload: ${limitedPhotoMetadatas.size}")

        limitedPhotoMetadatas.forEach { photoMetadata ->
            // Prepare the image data for WorkManager
            val photoRequest = FetchPhotoRequest.builder(photoMetadata)
                .setMaxWidth(800)
                .setMaxHeight(800)
                .build()

            Log.d("PlacesActivity", "Fetching photo for metadata: $photoMetadata")

            placesClient.fetchPhoto(photoRequest)
                .addOnSuccessListener { response ->
                    val photoBitmap = response.bitmap
                    Log.d("PlacesActivity", "Successfully fetched photo for ${savedPlace.name}")

                    savedPlace.name?.let {
                        enqueueImageUpload(photoBitmap, it, savedPlaceId) // Pass the savedPlaceId to upload
                    } ?: Log.e("PlacesActivity", "Place name is null for ID: $savedPlaceId")
                }
                .addOnFailureListener { exception ->
                    Log.e("PlacesActivity", "Error fetching photo for metadata: $photoMetadata", exception)
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
        val markerWidth = 80 // Desired width of the marker in pixels
        val markerHeight = 80 // Desired height of the marker in pixels
        val iconSize = 40 // Desired size of the icon in pixels

        // Create the base marker bitmap with desired size
        val markerDrawable = ContextCompat.getDrawable(this, R.drawable.marker_bg)?.apply {
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
//            val regex = """lat/lng: \((-?\d+\.\d+),(-?\d+\.\d+)\)""".toRegex()
//            val destinationLatLng =
//                savedPlace.latLngString?.let {
//                    regex.find(it)?.let { matchResult ->
//                        "${matchResult.groupValues[1]},${matchResult.groupValues[2]}"
//                    }
//                }
            intent.putExtra("PLACE_LATLNG",savedPlace.latLngString)
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


        if (isSimilarPlacesEnabled) {
            val recommendationRecyclerView: RecyclerView = bottomSheetView.findViewById(R.id.recommendationRecyclerView)
            val recommendationTitle: TextView = bottomSheetView.findViewById(R.id.recommendationTitle)
            recommendationRecyclerView.visibility = View.VISIBLE
            recommendationTitle.visibility = View.VISIBLE

            val similarPlacesHelper = SimilarPlacesRecommendationHelper(
                placesClient = placesClient,
                fusedLocationClient = fusedLocationClient,
                context = this
            )

            place.types?.let { savedPlaceType ->
                similarPlacesHelper.getFilteredPlacesForRecommendationBasedOnType(
                    savedPlaceType,
                ) { top10 ->
                    // Set up the RecyclerView
                    recommendationRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

                    // Set up the adapter with the top 10 places
                    val adapter = RecommendedPlaceAdapter(top10, false, placesClient) { place ->
                        // Handle click events or other interactions with the places
                        fetchPlaceDetailsFromAPI(place.placeId) { savedPlace ->
                            savedPlace?.let {
                                this.savedPlace = it
                                bottomSheetDialog.dismiss()
                                plotMarkerOnMap(place.placeId, savedPlace)
                                showPlaceDetailsInBottomSheet(savedPlace)
                            } ?: run {
                                Log.e("PlacesActivity", "Failed to fetch place details")
                            }
                        }
                    }

                    // Attach the adapter to the RecyclerView
                    recommendationRecyclerView.adapter = adapter
                }
            }
        }


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