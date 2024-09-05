package com.elgenium.smartcity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.elgenium.smartcity.databinding.ActivityPlacesBinding
import com.elgenium.smartcity.models.PlaceDistanceResponse
import com.elgenium.smartcity.network.PlaceDistance
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
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.PhotoMetadata
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPhotoRequest
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.material.bottomsheet.BottomSheetDialog
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Suppress("PrivatePropertyName")
class PlacesActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityPlacesBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var placesClient: PlacesClient
    private val DEFAULT_ZOOM_LEVEL = 15f
    private val DEFAULT_HEADING = 0f
    private val DEFAULT_ORIENTATION = 0f
    private var isFollowingUser = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPlacesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // get google maps API key in the secrets.properties
        val apiKey = BuildConfig.MAPS_API_KEY

        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(applicationContext, apiKey)
        }
        placesClient = Places.createClient(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)



        // Initialize FloatingActionButton using ViewBinding
        binding.fabCurrentLocation.setOnClickListener {
            resetMapToDefaultLocation()
        }


        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Set the selected item in the BottomNavigationView
        binding.bottomNavigation.selectedItemId = R.id.navigation_places

        // Set up the BottomNavigationView listener
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    navigateToActivity(DashboardActivity::class.java)
                    true
                }
                R.id.navigation_places -> true
                R.id.navigation_favorites -> true
                R.id.navigation_events -> true
                R.id.navigation_settings -> {
                    navigateToActivity(SettingsActivity::class.java)
                    true
                }
                else -> false
            }
        }

        // Set up the Search FAB to navigate to SearchActivity
        binding.fabSearch.setOnClickListener {
            navigateToActivity(SearchActivity::class.java)
        }

        val placeId = intent.getStringExtra("PLACE_ID")

        placeId?.let {
            fetchPlaceDetails(it)
        }

        binding.fabMapStyles.setOnClickListener {
            showMapStylesBottomSheet()
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

                    // Use animateCamera to smoothly transition to the new camera position
                    mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
                }
            }
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        }
    }


    private fun fetchPlaceDetails(placeId: String) {
        val fields = listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.ADDRESS,
            Place.Field.PHONE_NUMBER,
            Place.Field.WEBSITE_URI,
            Place.Field.PHOTO_METADATAS,
            Place.Field.RATING,
            Place.Field.OPENING_HOURS,
            Place.Field.LAT_LNG // Ensure this is included
        )
        val request = FetchPlaceRequest.builder(placeId, fields).build()

        placesClient.fetchPlace(request)
            .addOnSuccessListener { response ->
                val place = response.place
                place.latLng?.let { latLng ->
                    // Handle the valid latLng
                    val marker = mMap.addMarker(MarkerOptions().position(latLng).title(place.name))
                    marker?.tag = placeId
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                    showPlaceDetailsBottomSheet(placeId)
                } ?: run {
                    Log.d("PlacesActivity", "Place does not have a LatLng.")
                }
            }
            .addOnFailureListener { exception ->
                Log.e("PlacesActivity", "Error fetching place details", exception)
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

            // Move the camera to the user's location once
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val userLatLng = LatLng(it.latitude, it.longitude)
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 15f))

                    // Fetch and add POIs after setting the initial location
//                    fetchAndAddPois(userLatLng)
                }
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
                    showPlaceDetailsBottomSheet(it)
                }
                false
            }


        } else {
            // Request location permission
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        }
    }

    private fun showPlaceDetailsBottomSheet(placeId: String) {
        val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_place_details, findViewById(android.R.id.content), false)
        val bottomSheetDialog = BottomSheetDialog(this)
        bottomSheetDialog.setContentView(bottomSheetView)

        val placeName: TextView = bottomSheetView.findViewById(R.id.placeName)
        val placeAddress: TextView = bottomSheetView.findViewById(R.id.placeAddress)
        val placePhone: TextView = bottomSheetView.findViewById(R.id.placePhone)
        val placeWebsite: TextView = bottomSheetView.findViewById(R.id.placeWebsite)
        val placeRating: TextView = bottomSheetView.findViewById(R.id.placeRating)
        val placePhoto: ViewPager2 = bottomSheetView.findViewById(R.id.viewPager)
        val placeHours: TextView = bottomSheetView.findViewById(R.id.placeHours)
        val placeDistance: TextView = bottomSheetView.findViewById(R.id.placeDistance)
        val btnClose: ImageButton = bottomSheetView.findViewById(R.id.closeButton)

        val placeFields = listOf(
            Place.Field.NAME,
            Place.Field.ADDRESS,
            Place.Field.PHONE_NUMBER,
            Place.Field.PHOTO_METADATAS,
            Place.Field.LAT_LNG,
            Place.Field.OPENING_HOURS // Ensure this is included for location info
        )
        val request = FetchPlaceRequest.builder(placeId, placeFields).build()

        // Fetch the place details
        placesClient.fetchPlace(request)
            .addOnSuccessListener { response ->
                val place = response.place
                placeName.text = place.name ?: "Unknown Place"
                placeAddress.text = place.address ?: "No Address Available"
                placePhone.text = place.phoneNumber ?: "No Phone Available"
                placeWebsite.text = place.websiteUri?.toString() ?: "No Website Available"
                placeRating.text = getString(R.string.rating, place.rating ?: "No Rating")

                // Handle Opening Hours
                val openingHours = place.openingHours
                if (openingHours != null) {
                    placeHours.text = getString(R.string.open, openingHours.weekdayText.joinToString("\n"))
                } else {
                    placeHours.text = getString(R.string.opening_hours_not_available)
                }

                checkLocationPermission()

                // Get user location using fusedLocationClient
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        val userLocation = LatLng(location.latitude, location.longitude)
                        val placeLocation = place.latLng

                        if (placeLocation != null) {
                            // Now call the Directions API using Retrofit
                            val apiKey = BuildConfig.MAPS_API_KEY
                            val origin = "${userLocation.latitude},${userLocation.longitude}"
                            val destination = "${placeLocation.latitude},${placeLocation.longitude}"

                            val api = createRetrofitInstance()
                            api.getDirections(origin, destination, apiKey)
                                .enqueue(object : retrofit2.Callback<PlaceDistanceResponse> {
                                    override fun onResponse(
                                        call: Call<PlaceDistanceResponse>, response: retrofit2.Response<PlaceDistanceResponse>
                                    ) {
                                        if (response.isSuccessful && response.body() != null) {
                                            val directionsResponse = response.body()
                                            if (directionsResponse?.routes?.isNotEmpty() == true) {
                                                val distance = directionsResponse.routes[0].legs[0].distance.text
                                                placeDistance.text = getString(
                                                    R.string.distance_from_location,
                                                    distance
                                                )
                                            } else {
                                                placeDistance.text = getString(R.string.distance_not_available)
                                            }
                                        } else {
                                            placeDistance.text = getString(R.string.distance_error)
                                        }
                                    }

                                    override fun onFailure(call: Call<PlaceDistanceResponse>, t: Throwable) {
                                        Log.e("RetrofitError", "Error fetching directions: ${t.message}")
                                        placeDistance.text = getString(R.string.distance_error)
                                    }
                                })
                        } else {
                            placeDistance.text = getString(R.string.distance_not_available)
                        }
                    } else {
                        placeDistance.text = getString(R.string.distance_location_not_available)
                    }
                }

                // Display place photos
                val photoMetadatas = place.photoMetadatas ?: emptyList()
                if (photoMetadatas.isNotEmpty()) {
                    loadPhotosIntoViewPager(photoMetadatas, placePhoto)
                }

                if (place.latLng == null) {
                    placeAddress.text = getString(R.string.location_details_not_available)
                }

                btnClose.setOnClickListener {
                    bottomSheetDialog.dismiss()
                }
            }
            .addOnFailureListener { exception ->
                Log.e("PlacesActivity", "Error fetching place details for bottom sheet", exception)
            }

        bottomSheetDialog.show()
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

    private fun createRetrofitInstance(): PlaceDistance {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://maps.googleapis.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(PlaceDistance::class.java)
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

    private fun navigateToActivity(activityClass: Class<*>) {
        val intent = Intent(this, activityClass)
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }
}
