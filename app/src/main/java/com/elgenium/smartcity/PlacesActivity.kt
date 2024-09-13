package com.elgenium.smartcity

import PlacesClientSingleton
import android.Manifest
import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.viewpager2.widget.ViewPager2
import com.elgenium.smartcity.databinding.ActivityPlacesBinding
import com.elgenium.smartcity.models.SavedPlace
import com.elgenium.smartcity.network.PlaceDistanceService
import com.elgenium.smartcity.network.PlacesService
import com.elgenium.smartcity.network_reponses.PlaceDistanceResponse
import com.elgenium.smartcity.network_reponses.PlacesResponse
import com.elgenium.smartcity.singletons.ActivityNavigationUtils.navigateToActivity
import com.elgenium.smartcity.singletons.BottomNavigationManager
import com.elgenium.smartcity.singletons.NavigationBarColorCustomizerHelper
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
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.model.PhotoMetadata
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPhotoRequest
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Suppress("PrivatePropertyName")
class PlacesActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityPlacesBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val placesClient by lazy { PlacesClientSingleton.getClient(this) }
    private val DEFAULT_ZOOM_LEVEL = 15f
    private val DEFAULT_HEADING = 0f
    private val DEFAULT_ORIENTATION = 0f
    private var isFollowingUser = true
    private var currentRedMarker: Marker? = null
    private val poiMarkers = mutableListOf<Marker>()
    private var placeIDFromIntent: String?= "No Place ID"
    private var savedPlace: SavedPlace = SavedPlace()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlacesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Singleton object to set the color of the navigation bar making it more personalized
        NavigationBarColorCustomizerHelper.setNavigationBarColor(this, R.color.secondary_color)

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

        // Fetch the place ID from intent
        placeIDFromIntent = intent.getStringExtra("PLACE_ID")

        placeIDFromIntent?.let { it ->
            fetchPlaceDetailsFromAPI(it) { savedPlace ->
                savedPlace?.let {
                    this.savedPlace = it
                    // Update UI or do something with the savedPlace
                    plotMarkerOnMap(placeIDFromIntent!!, savedPlace) // Use the Place object to plot the marker
                    showPlaceDetailsInBottomSheet(it) // Use the Place object to show details
                } ?: run {
                    // Handle the case where savedPlace is null
                    Log.e("PlacesActivity", "Failed to fetch place details")
                }
            }
        }
        Log.d("PlacesActivity", "On create place id:  $placeIDFromIntent")
        Log.d("PlacesActivity", "Inside of place data:  $savedPlace")


        // Get the category from the intent from Search Activity, if available
        val category = intent.getStringExtra("CATEGORY")
        // Fetch the user's location (ensure you have location permission)
        getUserLocation { userLatLng ->
            // Use the userLatLng here
            fetchAndAddPois(userLatLng, category)
        }
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
        val btnTraffic: ImageButton = bottomSheetView.findViewById(R.id.btnTraffic)

        // Set click listeners for the buttons
        btnClose.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        btnSatellite.setOnClickListener {
            mMap.mapType = GoogleMap.MAP_TYPE_SATELLITE
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
        }

        btnTerrain.setOnClickListener {
            mMap.mapType = GoogleMap.MAP_TYPE_TERRAIN
        }

        btnTraffic.setOnClickListener {
            // Toggle traffic layer visibility
            mMap.isTrafficEnabled = !mMap.isTrafficEnabled
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

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

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

//            // Marker click listener to show the bottom sheet
            mMap.setOnMarkerClickListener { marker ->
                // Fetch the place ID (you can save this when adding the marker)
                val placeId = marker.tag as? String
                placeId?.let {
                    Log.d("PlacesActivity", "At on map click marker listener: $savedPlace")
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
                val placeOpeningDays = bottomSheetView.findViewById<TextView>(R.id.placeHoursDays).text.toString()
                val placeOpeningHours = bottomSheetView.findViewById<TextView>(R.id.placeHoursTime).text.toString()
                val placeRating = bottomSheetView.findViewById<TextView>(R.id.placeRating).text.toString()
                val placeWebsite = bottomSheetView.findViewById<TextView>(R.id.placeWebsite).text.toString()
                val placeDistance = bottomSheetView.findViewById<TextView>(R.id.placeDistance).text.toString()
                val placeStatus = bottomSheetView.findViewById<TextView>(R.id.openStatus).text.toString()


                // Prepare place data
                val placeData = placeIDFromIntent?.let {
                    SavedPlace(
                        id = it,
                        name = placeName,
                        address = placeAddress,
                        phoneNumber = placePhoneNumber,
                        latLngString = place.latLng.toString(),
                        openingDaysAndTime = "$placeOpeningDays==$placeOpeningHours",
                        rating = placeRating,
                        websiteUri = placeWebsite,
                        distance = placeDistance,
                        openingStatus = placeStatus,
                    )
                }

                Log.d("PlacesActivity", "Inside of place data:  $placeData")

                // Check for existing records
                userRef.orderByChild("id").equalTo(placeIDFromIntent).addListenerForSingleValueEvent(object :
                    ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.exists()) {
                            // Place already exists, show a message or handle accordingly
                            Toast.makeText(this@PlacesActivity, "Place is already saved!", Toast.LENGTH_SHORT).show()
                        } else {
                            // Save the place data
                            val newPlaceRef = userRef.push()
                            newPlaceRef.setValue(placeData)
                                .addOnSuccessListener {
                                    // Successfully saved the place data
                                    Toast.makeText(this@PlacesActivity, "Place saved successfully!", Toast.LENGTH_SHORT).show()
                                }
                                .addOnFailureListener { exception ->
                                    // Handle any errors
                                    Log.e("PlacesActivity", "Error saving place data", exception)
                                    Toast.makeText(this@PlacesActivity, "Failed to save place. Please try again.", Toast.LENGTH_SHORT).show()
                                }
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

    private fun fetchAndAddPois(userLatLng: LatLng, category: String? = null) {
        // Check if category is null or empty
        if (category.isNullOrEmpty()) {
            Log.d("PlacesActivity", "No category provided. No POIs will be displayed.")
            return // Exit the method if no category is provided
        }

        // Check for location permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val apiKey = BuildConfig.MAPS_API_KEY

            val retrofit = Retrofit.Builder()
                .baseUrl("https://maps.googleapis.com/maps/api/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service = retrofit.create(PlacesService::class.java)

            val location = "${userLatLng.latitude},${userLatLng.longitude}"
            val radius = 5000 // Radius in meters

            // Define custom icons
            val restaurantIcon = createCustomMarker(R.drawable.restaurant)
            val hotelIcon = createCustomMarker(R.drawable.hotel)
            val barIcon = createCustomMarker(R.drawable.bar)
            val defaultIcon = createCustomMarker(R.drawable.marker_custom)

            // List with the provided category
            val categories = listOf(category)

            Log.d("PlacesActivity", "category in list: $categories")

            for (type in categories) {
                service.getNearbyPlaces(location, radius, type, apiKey).enqueue(object :
                    Callback<PlacesResponse> {
                    override fun onResponse(call: Call<PlacesResponse>, response: Response<PlacesResponse>) {
                        if (response.isSuccessful) {
                            response.body()?.results?.forEach { place ->
                                Log.d("PlacesActivity", "Place: ${response.body()}")
                                val latLng = LatLng(place.geometry.location.lat, place.geometry.location.lng)
                                val markerOptions = MarkerOptions()
                                    .position(latLng)
                                    .title(place.name)
                                    .snippet("Category: $type")

                                // Set the icon based on the category
                                when (type) {
                                    "restaurant" -> markerOptions.icon(restaurantIcon)
                                    "hotel" -> markerOptions.icon(hotelIcon)
                                    "bar" -> markerOptions.icon(barIcon)
                                    else -> markerOptions.icon(defaultIcon) // Use default icon for other types
                                }

                                val marker = mMap.addMarker(markerOptions)
                                marker?.tag = place.place_id // Ensure place.id is valid
                                marker?.let { poiMarkers.add(it) }
                                Log.d("PlacesActivity", "Marker added with ID: ${place.place_id}")
                                Log.d("PlacesActivity", "POI markers on the list: $poiMarkers")
                            }
                        } else {
                            Log.e("PlacesActivity", "Response error for type: $type. Error code: ${response.code()}")
                        }
                    }

                    override fun onFailure(call: Call<PlacesResponse>, t: Throwable) {
                        Log.e("PlacesActivity", "Error fetching POI data", t)
                    }
                })
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
            bottomSheetDialogMoreOptions.dismiss()
            startActivity(Intent.createChooser(shareIntent, "Share via"))
        }


        // Set up report an event option
        reportOptionLayout.setOnClickListener {
            // Handle report an event option click
            val intent = Intent(this, ReportEventActivity::class.java)
            intent.putExtra("PLACE_NAME", savedPlace.name)
            intent.putExtra("PLACE_ADDRESS", savedPlace.address)
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
            Place.Field.LAT_LNG
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
                    latLng = place.latLng,
                    openingDaysAndTime = place.openingHours?.weekdayText?.joinToString(", "),
                    rating = place.rating?.toString(),
                    websiteUri = place.websiteUri?.toString(),
                    photoMetadataList = place.photoMetadatas ?: emptyList()
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

        // Update the UI with place details
        updatePlaceDetailsUI(place, bottomSheetView)
        handleOpeningHours(place, bottomSheetView)
        checkLocationPermissionAndFetchDistance(place, bottomSheetView)
        getAndLoadPhotoMetadatasFromPlace(place, bottomSheetView)
        setupMoreButton(bottomSheetView, bottomSheetDialog)
        setupCloseButton(bottomSheetView, bottomSheetDialog)
        setupSavedPlaces(bottomSheetView, bottomSheetDialog, place)
        setupGetDirectionsButton(bottomSheetView, bottomSheetDialog)

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

        // Update the UI based on the open/closed status
        if (isOpen) {
            openStatus.text = getString(R.string.open_status)
            openStatus.setBackgroundResource(R.drawable.open_pill_background)
        } else {
            openStatus.text = getString(R.string.closed)
            openStatus.setBackgroundResource(R.drawable.closed_pill_background)
        }

        openStatus.visibility = View.VISIBLE
    }

    private fun checkLocationPermissionAndFetchDistance(place: SavedPlace, bottomSheetView: View) {
        // Check if location permissions are granted.
        checkLocationPermission()

        // Retrieve the last known location from the fused location client.
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val userLocation = LatLng(location.latitude, location.longitude) // User's current location.
                val placeLocation = place.latLng // Location of the place.

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
                            override fun onResponse(call: Call<PlaceDistanceResponse>, response: Response<PlaceDistanceResponse>) {
                                // Handle the response from the API.
                                if (response.isSuccessful && response.body() != null) {
                                    val directionsResponse = response.body()
                                    if (directionsResponse?.routes?.isNotEmpty() == true) {
                                        // Extract and display the distance from the response.
                                        val distance = directionsResponse.routes[0].legs[0].distance.text
                                        bottomSheetView.findViewById<TextView>(R.id.placeDistance).text = getString(
                                            R.string.distance_from_location,
                                            distance
                                        )
                                    } else {
                                        // Display a message if distance information is not available.
                                        bottomSheetView.findViewById<TextView>(R.id.placeDistance).text = getString(R.string.distance_not_available)
                                    }
                                } else {
                                    // Display an error message if the API response is not successful.
                                    bottomSheetView.findViewById<TextView>(R.id.placeDistance).text = getString(R.string.distance_error)
                                }
                            }

                            override fun onFailure(call: Call<PlaceDistanceResponse>, t: Throwable) {
                                // Log the error and display an error message if the API request fails.
                                Log.e("RetrofitError", "Error fetching directions: ${t.message}")
                                bottomSheetView.findViewById<TextView>(R.id.placeDistance).text = getString(R.string.distance_error)
                            }
                        })
                } else {
                    // Display a message if the place location is not available.
                    bottomSheetView.findViewById<TextView>(R.id.placeDistance).text = getString(R.string.distance_not_available)
                }
            } else {
                // Display a message if the user's current location is not available.
                bottomSheetView.findViewById<TextView>(R.id.placeDistance).text = getString(R.string.distance_location_not_available)
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

    private fun setupGetDirectionsButton(bottomSheetView: View, bottomSheetDialog: BottomSheetDialog) {
        val btnGetDirections: MaterialButton = bottomSheetView.findViewById(R.id.btnGetDirections)
        btnGetDirections.setOnClickListener {
            // Dismiss the bottom sheet dialog
            bottomSheetDialog.dismiss()

            navigateToActivity(this, DirectionsActivity::class.java, false)
        }
    }

    private fun loadPhotosIntoViewPager(photoMetadatas: List<PhotoMetadata>, viewPager: ViewPager2) {
        val photoBitmaps = mutableListOf<Bitmap>()

        // Fetch each photo
        photoMetadatas.forEach { photoMetadata ->
            val photoRequest = FetchPhotoRequest.builder(photoMetadata)
                .setMaxWidth(400)
                .setMaxHeight(400)
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


    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }
}