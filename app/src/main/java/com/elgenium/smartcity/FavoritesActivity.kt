package com.elgenium.smartcity

import PlacesClientSingleton
import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.elgenium.smartcity.databinding.ActivityFavoritesBinding
import com.elgenium.smartcity.databinding.BottomSheetEventActionsBinding
import com.elgenium.smartcity.databinding.BottomSheetFavoriteEventsBinding
import com.elgenium.smartcity.databinding.BottomSheetFavoritePlaceBinding
import com.elgenium.smartcity.models.Event
import com.elgenium.smartcity.models.SavedPlace
import com.elgenium.smartcity.network.PlaceDistanceService
import com.elgenium.smartcity.singletons.ActivityNavigationUtils
import com.elgenium.smartcity.singletons.BottomNavigationManager
import com.elgenium.smartcity.singletons.NavigationBarColorCustomizerHelper
import com.elgenium.smartcity.viewpager_adapter.EventImageAdapter
import com.elgenium.smartcity.viewpager_adapter.FavoritesViewPagerAdapter
import com.elgenium.smartcity.viewpager_adapter.PhotoPagerAdapter
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.PhotoMetadata
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPhotoRequest
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.await
import retrofit2.converter.gson.GsonConverterFactory
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class FavoritesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFavoritesBinding
    private lateinit var database: DatabaseReference
    private lateinit var savedPlaces: MutableList<SavedPlace>
    private lateinit var savedEvents: MutableList<Event>
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var userLocationLatLngStringed: String
    @Suppress("PrivatePropertyName")
    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    private val placesClient by lazy { PlacesClientSingleton.getClient(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inflate the layout using view binding
        binding = ActivityFavoritesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        Log.d("FavoritesActivity", "Activity created")

        // Set the color of the navigation bar making it more personalized
        NavigationBarColorCustomizerHelper.setNavigationBarColor(this, R.color.secondary_color)

        // Singleton object that will handle bottom navigation functionality
        BottomNavigationManager.setupBottomNavigation(this, binding.bottomNavigation, FavoritesActivity::class.java)

        savedPlaces = mutableListOf()
        savedEvents = mutableListOf()
        userLocationLatLngStringed = "No location"

        database = FirebaseDatabase.getInstance().getReference("Users")
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        // load savePlaces and saveEvents data from firebase db
        if (userId != null) {
            loadSavedPlaces(userId)
            loadSavedEvents(userId)
        }

        // setup viewpager for Tabs
        viewPagerSetupForTabs()
    }

    private fun getTravelDistance(event: Event, userLocation: LatLng): Deferred<Double?> = CoroutineScope(
        Dispatchers.IO).async {
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

    private fun formatDistance(distance: Double?): String {
        return distance?.let {
            String.format(Locale.US, "%.1f km away from location", it) // Format to one decimal place
        } ?: "Distance not available"
    }

    private fun getUserLocation(callback: (LatLng?) -> Unit) {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val userLocation = LatLng(location.latitude, location.longitude)
                    Log.d("FavoritesActivity", "User Location: $userLocation")
                    callback(userLocation)
                } else {
                    Log.e("FavoritesActivity", "Location is null")
                    callback(null)
                }
            }
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE)
        }
    }

    private fun parseLatLng(latLngString: String): Pair<Double, Double>? {
        // Regular expression to match the latitude and longitude
        val regex = Regex("""lat/lng: \(([^,]+),([^)]*)\)""")
        val matchResult = regex.find(latLngString)
        val (latitude, longitude) = matchResult?.destructured ?: return null
        return Pair(latitude.toDouble(), longitude.toDouble())
    }

    private fun viewPagerSetupForTabs() {
        // Set up the ViewPager with the sections adapter
        val adapter = FavoritesViewPagerAdapter(
            savedPlaces = savedPlaces,
            savedEvents = savedEvents,
            onPlaceClick = { place ->
                Log.d("FavoritesActivity", "Place clicked: ${place.name}")
                showPlaceDetails(place)
            },
            onPlaceLongClick = { place ->
                Log.d("FavoritesActivity", "Place long-clicked: ${place.name}")
                showBottomSheetPlaceActions(place)
            },
            onEventClick = { event ->
                Log.d("FavoritesActivity", "Event clicked: ${event.eventName}")
                showEventDetailsBottomSheetDialog(event)
            },
            onEventLongClick = { event ->
                Log.d("FavoritesActivity", "Event long-clicked: ${event.eventName}")
                showBottomSheetEventActions(event)
            }
        )

        // Set the adapter to the ViewPager
        binding.viewPager.adapter = adapter


        // Link the TabLayout with the ViewPager2
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            when (position) {
                0 -> tab.text = "Saved Places"
                1 -> tab.text = "Saved Events"
            }
        }.attach()

    }

    private fun loadSavedPlaces(userId: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Log.w("FavoritesActivity", "User is not authenticated. Skipping loadSavedPlaces.")
            return
        }

        database.child(userId).child("saved_places").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                savedPlaces.clear()
                for (placeSnapshot in snapshot.children) {
                    val place = placeSnapshot.getValue(SavedPlace::class.java)
                    if (place != null) {
                        Log.d("FavoritesActivity", "Saved place loaded: ${place.name}")
                        savedPlaces.add(place)
                    }
                }
                (binding.viewPager.adapter as FavoritesViewPagerAdapter).updateSavedPlaces(savedPlaces)
                Log.d("FavoritesActivity", "Saved places updated. Total count: ${savedPlaces.size}")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FavoritesActivity", "Failed to load saved places", error.toException())
            }
        })
    }

    private fun loadSavedEvents(userId: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Log.w("FavoritesActivity", "User is not authenticated. Skipping loadSavedEvents.")
            return
        }

        database.child(userId).child("saved_events").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                savedEvents.clear()
                for (placeSnapshot in snapshot.children) {
                    val event = placeSnapshot.getValue(Event::class.java)
                    if (event != null) {
                        Log.d("FavoritesActivity", "Saved event loaded: ${event.eventName}")
                        savedEvents.add(event)
                    }
                }
                (binding.viewPager.adapter as FavoritesViewPagerAdapter).updateSavedEvents(savedEvents)
                Log.d("FavoritesActivity", "Saved events updated. Total count: ${savedEvents.size}")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FavoritesActivity", "Failed to load saved events", error.toException())
            }
        })
    }

    private fun getPhotoMetadatas(placeId: String, bottomSheetBinding: BottomSheetFavoritePlaceBinding) {
        Log.d("FavoritesActivity", "Fetching photo metadata for place ID: $placeId")

        val placePhotoMetadatas = listOf(
            Place.Field.PHOTO_METADATAS
        )

        // Build the request to fetch the place details
        val request = FetchPlaceRequest.builder(placeId, placePhotoMetadatas).build()

        // Fetch the place details using the Places API
        placesClient.fetchPlace(request)
            .addOnSuccessListener { response ->
                Log.d("FavoritesActivity", "Place details fetched successfully")
                val placeDetails = response.place
                getAndLoadPhotoMetadatasFromPlace(placeDetails, bottomSheetBinding)
            }
            .addOnFailureListener { exception ->
                Log.e("FavoritesActivity", "Error fetching place details for bottom sheet", exception)
            }
    }

    private fun showEventDetailsBottomSheetDialog(event: Event) {
        val bottomSheetDialog = BottomSheetDialog(this)
        val bottomSheetBinding = BottomSheetFavoriteEventsBinding.inflate(LayoutInflater.from(this))
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

            setupGetDirectionsButton(bottomSheetBinding, bottomSheetDialog)
        }

        bottomSheetDialog.show()
    }

    private fun showBottomSheetEventActions(event: Event) {
        val bottomSheetDialog = BottomSheetDialog(this)
        val bottomSheetBinding = BottomSheetEventActionsBinding.inflate(LayoutInflater.from(this))
        bottomSheetDialog.setContentView(bottomSheetBinding.root)

        // Format the date and time
        val inputFormat = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
        val outputFormat = SimpleDateFormat("MMMM d, yyyy 'at' h:mm a", Locale.getDefault())

        // Populate event details in bottom sheet
        with(bottomSheetBinding) {
            shareOptionLayout.setOnClickListener {
                val shareText = """
            📍 Let's go to this event:
            
                Event Name: ${event.eventName}
                Place: ${event.location}
                Address: ${event.additionalInfo}
                Time Started: ${formatDate(event.startedDateTime, inputFormat, outputFormat)}
                Time Ended: ${formatDate(event.endedDateTime, inputFormat, outputFormat)}
                Event Details: ${event.eventDescription}
            """.trimIndent()

                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, shareText)
                    type = "text/plain"
                }

                bottomSheetDialog.dismiss()
                startActivity(Intent.createChooser(shareIntent, "Share via"))
            }

            removeOptionLayout.setOnClickListener {
                // Handle deleting the place
                deleteSavedEvent(event)
                bottomSheetDialog.dismiss()
            }

        }

        bottomSheetDialog.show()
    }

    private fun setupGetDirectionsButton(bottomSheetBinding: BottomSheetFavoriteEventsBinding, bottomSheetDialog: BottomSheetDialog) {

        bottomSheetBinding.btnGetDirections.setOnClickListener{
            bottomSheetDialog.dismiss()

            ActivityNavigationUtils.navigateToActivity(this, DirectionsActivity::class.java, false)
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

    private fun showBottomSheetPlaceActions(place: SavedPlace) {
        val bottomSheetDialog = BottomSheetDialog(this)
        val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_place_actions, binding.root,false)
        bottomSheetDialog.setContentView(bottomSheetView)

        val shareOptionLayout: LinearLayout = bottomSheetView.findViewById(R.id.shareOptionLayout)
        val removeOptionLayout: LinearLayout = bottomSheetView.findViewById(R.id.removeOptionLayout)
        val reportOptionLayout: LinearLayout = bottomSheetView.findViewById(R.id.reportOptionLayout)

        shareOptionLayout.setOnClickListener {
            val shareText = """
        📍 Check out this place:
        
        Name: ${place.name}
        Address: ${place.address}
        Phone: ${place.phoneNumber ?: "No phone number available"}
        Rating: ${place.rating ?: "No rating available."}
    """.trimIndent()

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, shareText)
                type = "text/plain"
            }
            startActivity(Intent.createChooser(shareIntent, "Share via"))
        }

        removeOptionLayout.setOnClickListener {
            // Handle deleting the place
            deleteSavedPlace(place)
            bottomSheetDialog.dismiss()
        }

        reportOptionLayout.setOnClickListener{
            // navigate to ReportEventActivity
            val intent = Intent(this, ReportEventActivity::class.java)
            intent.putExtra("PLACE_NAME", place.name)
            intent.putExtra("PLACE_ADDRESS", place.address)
            intent.putExtra("PLACE_LATLNG", place.latLngString)
            val options = ActivityOptions.makeCustomAnimation(
                this,
                R.anim.fade_in,
                R.anim.fade_out
            )
            this.startActivity(intent, options.toBundle())
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }

    private fun deleteSavedPlace(place: SavedPlace) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            // Find the correct key in the Firebase database
            database.child(userId).child("saved_places")
                .orderByChild("id")
                .equalTo(place.id)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        for (placeSnapshot in snapshot.children) {
                            // Remove the matching place
                            placeSnapshot.ref.removeValue()
                                .addOnSuccessListener {
                                    Toast.makeText(this@FavoritesActivity, "Place deleted", Toast.LENGTH_SHORT).show()
                                }
                                .addOnFailureListener {
                                    Toast.makeText(this@FavoritesActivity, "Failed to delete place", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Toast.makeText(this@FavoritesActivity, "Failed to delete place", Toast.LENGTH_SHORT).show()
                    }
                })
        } else {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteSavedEvent(event: Event) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            // Find the correct key in the Firebase database
            database.child(userId).child("saved_events")
                .orderByChild("checker")
                .equalTo(event.checker)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        for (placeSnapshot in snapshot.children) {
                            // Remove the matching event
                            placeSnapshot.ref.removeValue()
                                .addOnSuccessListener {
                                    Toast.makeText(this@FavoritesActivity, "Event deleted", Toast.LENGTH_SHORT).show()
                                }
                                .addOnFailureListener {
                                    Toast.makeText(this@FavoritesActivity, "Failed to delete event", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Toast.makeText(this@FavoritesActivity, "Failed to delete event", Toast.LENGTH_SHORT).show()
                    }
                })
        } else {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPlaceDetails(place: SavedPlace) {
        Log.d("FavoritesActivity", "Showing details for place: $place")
        val bottomSheetDialog = BottomSheetDialog(this)
        val bottomSheetBinding = BottomSheetFavoritePlaceBinding.inflate(LayoutInflater.from(this))
        bottomSheetDialog.setContentView(bottomSheetBinding.root)


        with (bottomSheetBinding) {
            bottomSheetBinding.placeName.text = place.name
            bottomSheetBinding.placeAddress.text = place.address
            bottomSheetBinding.openStatus.text = place.openingStatus
            bottomSheetBinding.placeDistance.text = place.distance
            bottomSheetBinding.placePhone.text = place.phoneNumber ?: "No phone number available"
            bottomSheetBinding.placeWebsite.text = place.websiteUri ?: "No website available"
            bottomSheetBinding.placeRating.text = place.rating ?: "N/A"


            // gets the photo metadata for display in viewpager
            place.id?.let { getPhotoMetadatas(it, bottomSheetBinding) }

            // close button listener
            bottomSheetBinding.closeButton.setOnClickListener {
                // Dismiss the bottom sheet dialog
                bottomSheetDialog.dismiss()
            }

            bottomSheetBinding.btnGetDirections.setOnClickListener {
                Log.d("FavoritesActivity", "Get Directions button clicked")

                // Fetch user location asynchronously
                getUserLocation { userLocation ->
                    if (userLocation != null) {
                        val origin = "${userLocation.latitude},${userLocation.longitude}"
                        val regex = """lat/lng: \((\-?\d+\.\d+),(\-?\d+\.\d+)\)""".toRegex()
                        val destinationLatLng =
                            place.latLngString?.let { it1 ->
                                regex.find(it1)?.let { matchResult ->
                                    "${matchResult.groupValues[1]},${matchResult.groupValues[2]}"
                                }
                            }
                        val destination = "${place.name}==${place.address}==${destinationLatLng}"
                        Log.d("FavoritesActivity", "Origin: $origin, Destination: $destinationLatLng")

                        if (destination.isNotBlank()) {
                            val intent = Intent(this@FavoritesActivity, DirectionsActivity::class.java)
                            intent.putExtra("DESTINATION", destination)
                            intent.putExtra("ORIGIN", origin)
                            startActivity(intent)
                            bottomSheetDialog.dismiss()
                        } else {
                            Log.e("FavoritesActivity", "Destination is missing")
                        }
                    } else {
                        Log.e("FavoritesActivity", "User location not available")
                    }
                }
            }


            // Update the UI based on the open/closed status.
            if ((bottomSheetBinding.openStatus.text as String?).equals("Open", ignoreCase = true) ||
                (openStatus.text as String?).equals("Open 24 hours", ignoreCase = true) ) {
                bottomSheetBinding.openStatus.text = getString(R.string.open_status)
                bottomSheetBinding.openStatus.setBackgroundResource(R.drawable.open_pill_background)
            } else {
                bottomSheetBinding.openStatus.text = getString(R.string.closed)
                bottomSheetBinding.openStatus.setBackgroundResource(R.drawable.closed_pill_background)
            }

            // Split the openingDaysAndTime string into days and times
            val daysAndTimes = place.openingDaysAndTime?.split("==") ?: listOf("", "")

            // Ensure that the splitting results in the expected format
            if (daysAndTimes.size == 2) {
                val days = daysAndTimes[0]
                val times = daysAndTimes[1]

                // Populate the TextViews with days and times
                bottomSheetBinding.placeHoursDays.text = days
                bottomSheetBinding.placeHoursTime.text = times
            } else {
                // Handle cases where data might be incorrectly formatted
                bottomSheetBinding.placeHoursDays.text = getString(R.string.no_data_available)
                bottomSheetBinding.placeHoursTime.text = getString(R.string.no_data_available)
            }

            // Check opening hours availability and determine if the indicator be visible or not
            if (bottomSheetBinding.placeHoursDays.text.isNullOrEmpty() || bottomSheetBinding.placeHoursTime.text.isNullOrEmpty()) {
                // Hide the open status if no opening hours information is available.
                bottomSheetBinding.openStatus.visibility = View.GONE
                // Adjust margin for placeDistance TextView.
                val layoutParams = bottomSheetBinding.placeDistance.layoutParams as ViewGroup.MarginLayoutParams
                layoutParams.marginStart = 0
                bottomSheetBinding.placeDistance.layoutParams = layoutParams
            } else {
                bottomSheetBinding.openStatus.visibility = View.VISIBLE
            }


        }

        bottomSheetDialog.show()
    }

    private fun loadPhotosIntoViewPager(photoMetadatas: List<PhotoMetadata>, viewPager: ViewPager2) {
        Log.d("FavoritesActivity", "Loading ${photoMetadatas.size} photos into ViewPager")
        val photoBitmaps = mutableListOf<Bitmap>()

        // Fetch each photo
        photoMetadatas.forEach { photoMetadata ->
            Log.d("FavoritesActivity", "Fetching photo for metadata: $photoMetadata")
            val photoRequest = FetchPhotoRequest.builder(photoMetadata)
                .setMaxWidth(400)
                .setMaxHeight(400)
                .build()
            placesClient.fetchPhoto(photoRequest)
                .addOnSuccessListener { response ->
                    Log.d("FavoritesActivity", "Photo fetched successfully")
                    val photoBitmap = response.bitmap
                    photoBitmap.let {
                        photoBitmaps.add(it)
                        if (photoBitmaps.size == photoMetadatas.size) {
                            Log.d("FavoritesActivity", "All photos fetched, setting adapter")
                            viewPager.adapter = PhotoPagerAdapter(photoBitmaps)
                        }
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("FavoritesActivity", "Error fetching photo", exception)
                }
        }
    }

    private fun getAndLoadPhotoMetadatasFromPlace(placeDetails: Place, bottomSheetBinding: BottomSheetFavoritePlaceBinding) {
        Log.d("FavoritesActivity", "Extracting photo metadata from place details")
        val photoMetadatas = placeDetails.photoMetadatas
        val viewPager = bottomSheetBinding.viewPager

        if (photoMetadatas != null && photoMetadatas.isNotEmpty()) {
            viewPager.visibility = View.VISIBLE
            loadPhotosIntoViewPager(photoMetadatas, viewPager)
        } else {
            Log.d("FavoritesActivity", "No photo metadata available")
            viewPager.visibility = View.GONE
        }
    }
}
