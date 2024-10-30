package com.elgenium.smartcity

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.elgenium.smartcity.databinding.ActivityEventsBinding
import com.elgenium.smartcity.databinding.BottomSheetEventDetailsBinding
import com.elgenium.smartcity.models.Event
import com.elgenium.smartcity.network.PlaceDistanceService
import com.elgenium.smartcity.recyclerview_adapter.EventAdapter
import com.elgenium.smartcity.recyclerview_adapter.MyEventsAdapter
import com.elgenium.smartcity.singletons.ActivityNavigationUtils
import com.elgenium.smartcity.singletons.BottomNavigationManager
import com.elgenium.smartcity.singletons.LayoutStateManager
import com.elgenium.smartcity.singletons.NavigationBarColorCustomizerHelper
import com.elgenium.smartcity.viewpager_adapter.EventImageAdapter
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
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
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.await
import retrofit2.converter.gson.GsonConverterFactory
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class EventsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEventsBinding
    private lateinit var eventAdapter: EventAdapter
    private lateinit var database: DatabaseReference
    private val eventList = mutableListOf<Event>()
    private var selectedCategoryButton: MaterialButton? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    private lateinit var myEventsAdapter: MyEventsAdapter



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityEventsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        searchViewAppearance()

        // Initialize Firebase Database reference
        database = FirebaseDatabase.getInstance().getReference("Events")

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)


        val layoutManager = LinearLayoutManager(this)
        binding.eventsRecyclerView.layoutManager = layoutManager
        eventAdapter = EventAdapter(eventList, { event ->
            // Handle item click
            showEventDetailsBottomSheetDialog(event)
        }, binding.lottieAnimation, binding.emptyDataLabel)

        binding.eventsRecyclerView.adapter = eventAdapter



        // Load events from Firebase
        loadEventsFromFirebase()

        // Set up the search view
        setupSearchView()

        setupCategoryButtons()

        setupFilterButton()

        BottomNavigationManager.setupBottomNavigation(this, binding.bottomNavigation, EventsActivity::class.java)
        NavigationBarColorCustomizerHelper.setNavigationBarColor(this, R.color.secondary_color)
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

            Log.e("EventsActivity", "Start Navigate button clicked")
            val intent =
                Intent(this@EventsActivity, StartNavigationsActivity::class.java)
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
                                LayoutStateManager.showFailureLayout(this@EventsActivity, "The event has already been saved to Favorites. Please select another event.", "Return to Events", EventsActivity::class.java)
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
                                LayoutStateManager.showLoadingLayout(this@EventsActivity, "Please wait while we are saving your event")

                                // Save to Firebase Realtime Database
                                newEventRef.setValue(savedEventData).addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        Toast.makeText(bottomSheetBinding.root.context, "Event saved successfully!", Toast.LENGTH_SHORT).show()
                                        LayoutStateManager.showSuccessLayout(this@EventsActivity, "Event saved successfully!", "You can now view this saved event in Favorites.", EventsActivity::class.java)
                                    } else {
                                        Toast.makeText(bottomSheetBinding.root.context, "Failed to save event.", Toast.LENGTH_SHORT).show()
                                        LayoutStateManager.showFailureLayout(this@EventsActivity, "Something went wrong. Please check your connection or try again.", "Return to Events", EventsActivity::class.java)
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

    private fun parseLatLng(latLngString: String): Pair<Double, Double>? {
        // Regular expression to match the latitude and longitude
        val regex = Regex("""lat/lng: \(([^,]+),([^)]*)\)""")
        val matchResult = regex.find(latLngString)
        val (latitude, longitude) = matchResult?.destructured ?: return null
        return Pair(latitude.toDouble(), longitude.toDouble())
    }

    private fun getUserLocation(callback: (LatLng?) -> Unit) {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val userLocation = LatLng(location.latitude, location.longitude)
                    Log.d("EventsActivity", "User Location: $userLocation")
                    callback(userLocation)
                } else {
                    Log.e("EventsActivity", "Location is null")
                    callback(null)
                }
            }
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE)
        }
    }

    private fun filterNearbyEvents(userLocation: LatLng) {
        val radiusInMeters = 2500 // 2.5 km
        val apiKey = BuildConfig.MAPS_API_KEY

        val retrofit = Retrofit.Builder()
            .baseUrl("https://maps.googleapis.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(PlaceDistanceService::class.java)

        fun getDistance(event: Event): Deferred<Boolean> = CoroutineScope(Dispatchers.IO).async {
            val placeLatLngString = event.placeLatLng ?: return@async false
            val placeLatLng = parseLatLng(placeLatLngString) ?: return@async false
            val (eventLat, eventLng) = placeLatLng

            val origin = "${userLocation.latitude},${userLocation.longitude}"
            val destination = "$eventLat,$eventLng"

            try {
                val response = service.getDirections(origin, destination, apiKey).await()
                val distanceValue = response.routes[0].legs[0].distance.value
                distanceValue <= radiusInMeters.toDouble()
            } catch (e: Exception) {
                Log.e("EventsActivity", "API Call Failure for event ${event.eventName}: ${e.message}")
                false
            }
        }

        CoroutineScope(Dispatchers.Main).launch {
            val eventRequests = eventList.map { event -> getDistance(event) }
            val results = eventRequests.awaitAll() // Collect results from all requests
            val filtered = eventList.filterIndexed { index, _ -> results[index] }
            Log.d("EventsActivity", "Filtered Events: $filtered")
            eventAdapter.updateEvents(filtered)
        }
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

    private fun checkLocationPermissionAndFetchNearbyEvents() {
        // Check if location permissions are granted
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {

            getUserLocation { userLocation ->
                if (userLocation != null) {
                    filterNearbyEvents(userLocation)
                } else {
                    // Handle case where user location is not available
                    Log.e("EventsActivity", "User location is not available")
                }
            }

        } else {
            Log.e("EventsActivity", "Location permission not granted")
            Toast.makeText(this, "Please grant location permission", Toast.LENGTH_SHORT).show()
        }
    }

    private fun filterOngoingEvents() {
        val currentDateTime = Date() // Current date and time

        val filteredEvents = eventList.filter { event ->
            val sdf = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
            try {
                val startDateTime = event.startedDateTime?.let { sdf.parse(it) }
                val endDateTime = event.endedDateTime?.let { sdf.parse(it) }

                if (startDateTime != null && endDateTime != null) {
                    // If start and end are the same, consider the event ongoing until the end of the current day (11:59 PM)
                    if (startDateTime == endDateTime) {
                        val cal = Calendar.getInstance()
                        cal.time = startDateTime
                        cal.set(Calendar.HOUR_OF_DAY, 23)
                        cal.set(Calendar.MINUTE, 59)
                        cal.set(Calendar.SECOND, 59)
                        val endOfDay = cal.time

                        currentDateTime.before(endOfDay)
                    } else {
                        // Regular ongoing event check
                        currentDateTime.after(startDateTime) && currentDateTime.before(endDateTime)
                    }
                } else {
                    false
                }
            } catch (e: Exception) {
                Log.e("EventsActivity", "error: ${e.message}")
                false
            }
        }

        eventAdapter.updateEvents(filteredEvents)
    }

    private fun filterUpcomingEvents() {
        val currentDateTime = Date() // Current date and time

        val filteredEvents = eventList.filter { event ->
            val sdf = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
            try {
                val startDateTime = event.startedDateTime?.let { sdf.parse(it) }
                Log.e("EventsActivity", "start Datetime: $startDateTime, ")

                startDateTime != null && currentDateTime.before(startDateTime)
            } catch (e: Exception) {
                Log.e("EventsActivity", "error: ${e.message}")
                false
            }
        }

        eventAdapter.updateEvents(filteredEvents)
    }

    private fun filterPastEvents() {
        val currentDateTime = Date() // Current date and time

        val filteredEvents = eventList.filter { event ->
            val sdf = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
            try {
                val startDateTime = event.startedDateTime?.let { sdf.parse(it) }
                val endDateTime = event.endedDateTime?.let { sdf.parse(it) }

                if (startDateTime != null && endDateTime != null) {
                    // If start and end are the same, do not treat the event as past until 11:59 PM of that day
                    if (startDateTime == endDateTime) {
                        val cal = Calendar.getInstance()
                        cal.time = startDateTime
                        cal.set(Calendar.HOUR_OF_DAY, 23)
                        cal.set(Calendar.MINUTE, 59)
                        cal.set(Calendar.SECOND, 59)
                        val endOfDay = cal.time

                        currentDateTime.after(endOfDay) // Consider past only after 11:59 PM
                    } else {
                        // Regular past event check
                        currentDateTime.after(endDateTime)
                    }
                } else {
                    false
                }
            } catch (e: Exception) {
                false
            }
        }

        eventAdapter.updateEvents(filteredEvents)
    }

    private fun setupFilterButton() {
        binding.filterButton.setOnClickListener {
            // Handle option clicks
            setupBottomSheetMainOptions()
        }
    }

    private fun showOptionsBottomSheet(checker: String) {
        val bottomSheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_options, binding.root, false)

        // Create BottomSheetDialog and set the inflated view
        val bottomSheetDialog = BottomSheetDialog(this)
        bottomSheetDialog.setContentView(bottomSheetView)

        val optionEdit = bottomSheetView.findViewById<LinearLayout>(R.id.optionEdit)
        val optionDelete = bottomSheetView.findViewById<LinearLayout>(R.id.optionDelete)

        optionEdit.setOnClickListener {
            bottomSheetDialog.dismiss()
            val intent = Intent(this, ReportEventActivity::class.java)
            intent.putExtra("FROM_EVENTS_ACTIVITY", checker)
            startActivity(intent)
        }

        optionDelete.setOnClickListener {
            bottomSheetDialog.dismiss()
            confirmDeleteEvent(checker)
        }

        bottomSheetDialog.show()
    }

    private fun confirmDeleteEvent(checker: String) {
        // Create a custom alert dialog layout for confirmation
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_delete, null)
        val alertDialogBuilder = AlertDialog.Builder(this)
            .setView(dialogView)

        val dialog = alertDialogBuilder.create()

        val buttonCancel = dialogView.findViewById<MaterialButton>(R.id.buttonCancel)
        val buttonDelete = dialogView.findViewById<MaterialButton>(R.id.buttonDelete)

        buttonCancel.setOnClickListener {
            dialog.dismiss() // Dismiss the dialog
        }

        buttonDelete.setOnClickListener {
            deleteEventByChecker(checker)
            dialog.dismiss() // Dismiss the dialog after deletion
        }

        dialog.show()
    }

    private fun deleteEventByChecker(checker: String) {
        val eventsRef = FirebaseDatabase.getInstance().getReference("Events")
        eventsRef.orderByChild("checker").equalTo(checker).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (eventSnapshot in snapshot.children) {
                    // Delete the event with the matching checker
                    eventSnapshot.ref.removeValue().addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.e("DeleteEvent", "Event with checker $checker deleted successfully.")
                            // Optionally, notify the adapter to refresh the UI
                            loadUserEvents() // Refresh the event list
                        } else {
                            Log.e("DeleteEvent", "Failed to delete event: ${task.exception?.message}")
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("DeleteEvent", "Failed to load events: ${error.message}")
            }
        })
    }

    private fun setupBottomSheetMainOptions(){
        val bottomSheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_events_main_options, binding.root, false)

        // Create BottomSheetDialog and set the inflated view
        val bottomSheetDialog = BottomSheetDialog(this)
        bottomSheetDialog.setContentView(bottomSheetView)

        Log.e("EventsActivity", "ALL EVENTS: $eventList")

        val optionFilter = bottomSheetView.findViewById<LinearLayout>(R.id.optionFilter)
        val optionViewHideMyEvents = bottomSheetView.findViewById<LinearLayout>(R.id.optionShowMyEvents)

        optionFilter.setOnClickListener {
            bottomSheetDialog.dismiss()
            setupBottomSheetOptions()
        }

        optionViewHideMyEvents.setOnClickListener {
           ActivityNavigationUtils.navigateToActivity(this, MyEventsActivity::class.java, false)

            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }

    private fun setupBottomSheetOptions() {
        val bottomSheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_filtering_options, binding.root, false)

        // Create BottomSheetDialog and set the inflated view
        val bottomSheetDialog = BottomSheetDialog(this)
        bottomSheetDialog.setContentView(bottomSheetView)

        val ongoingOption = bottomSheetView.findViewById<LinearLayout>(R.id.ongoingOptionLayout)
        val upcomingOption = bottomSheetView.findViewById<LinearLayout>(R.id.upcomingOptionLayout)
        val pastOption = bottomSheetView.findViewById<LinearLayout>(R.id.pastOptionLayout)
        val nearbyOption = bottomSheetView.findViewById<LinearLayout>(R.id.nearbyOptionLayout)
        val showAllOption = bottomSheetView.findViewById<LinearLayout>(R.id.showAllOptionLayout)


        ongoingOption.setOnClickListener {
            filterOngoingEvents()
            bottomSheetDialog.dismiss()
        }

        upcomingOption.setOnClickListener {
            filterUpcomingEvents()
            bottomSheetDialog.dismiss()
        }

        pastOption.setOnClickListener {
            filterPastEvents()
            bottomSheetDialog.dismiss()
        }

        nearbyOption.setOnClickListener {
            checkLocationPermissionAndFetchNearbyEvents()
            bottomSheetDialog.dismiss()
        }

        showAllOption.setOnClickListener {
            eventAdapter.updateEvents(eventList)
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }

    private fun setupCategoryButtons() {
        val buttonFestival = binding.btnFestival
        val buttonSales = binding.btnSales
        val buttonConcert = binding.btnConcert
        val buttonOutdoor = binding.btnOutdoor
        val buttonWorkshop = binding.btnWorkShop
        val buttonOthers = binding.btnOthers

        val categoryButtons = listOf(
            buttonFestival,
            buttonSales,
            buttonOutdoor,
            buttonConcert,
            buttonWorkshop,
            buttonOthers,
        )

        categoryButtons.forEach { button ->
            button.setOnClickListener {
                if (selectedCategoryButton == button) {
                    // Deselect the currently selected button and show all events
                    selectedCategoryButton = null
                    categoryButtons.forEach { btn ->
                        btn.setBackgroundColor(getColor(R.color.primary_color))
                        btn.setTextColor(ContextCompat.getColor(this, R.color.secondary_color))
                        btn.strokeColor = ColorStateList.valueOf(getColor(R.color.secondary_color))
                        btn.iconTint = ColorStateList.valueOf(getColor(R.color.secondary_color))
                    }
                    // Show all events
                    eventAdapter.updateEvents(eventList)
                } else {
                    // Deselect the previously selected button
                    selectedCategoryButton?.let { prevButton ->
                        prevButton.setBackgroundColor(getColor(R.color.primary_color))
                        prevButton.setTextColor(ContextCompat.getColor(this, R.color.secondary_color))
                        prevButton.strokeColor = ColorStateList.valueOf(getColor(R.color.secondary_color))
                        prevButton.iconTint = ColorStateList.valueOf(getColor(R.color.secondary_color))
                    }
                    // Select the new button
                    button.setBackgroundColor(getColor(R.color.brand_color))
                    button.setTextColor(ContextCompat.getColor(this, R.color.primary_color))
                    button.iconTint = ColorStateList.valueOf(getColor(R.color.primary_color))
                    button.strokeColor = ColorStateList.valueOf(getColor(R.color.primary_color))

                    // Update the selected category button
                    selectedCategoryButton = button

                    // Filter events by the selected category
                    val category = button.text.toString()
                    filterEventsByCategory(category)
                }
            }
        }
    }

    private fun filterEventsByCategory(category: String) {
        val filteredEvents = eventList.filter { event ->
            when (category) {
                "Festival" -> event.eventCategory?.contains("Festivals & Celebrations", ignoreCase = true) == true
                "Concert" -> event.eventCategory?.contains("Concerts & Live Performances", ignoreCase = true) == true
                "Sales" -> event.eventCategory?.contains("Sales & Promotions", ignoreCase = true) == true
                "Workshop" -> event.eventCategory?.contains("Workshop & Seminars", ignoreCase = true) == true
                "Outdoor Events" -> event.eventCategory?.startsWith("Outdoor & Adventure Events", ignoreCase = true) == true
                "Others" -> event.eventCategory?.startsWith("Others:", ignoreCase = true) == true
                else -> false
            }
        }

        eventAdapter.updateEvents(filteredEvents)
    }

    private fun loadEventsFromFirebase() {
        val firebaseAuth = FirebaseAuth.getInstance()
        val user = firebaseAuth.currentUser

        if (user != null) {
            // User is authenticated, proceed with loading events
            database.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    eventList.clear() // Clear the list before adding new data
                    for (eventSnapshot in snapshot.children) {
                        val event = eventSnapshot.getValue(Event::class.java)
                        Log.d("EventsActivity", "Images type: ${event?.images?.javaClass?.simpleName}")

                        // Only add events that have a status of "Verified"
                        if (event?.status == "Verified") {
                            eventList.add(event)
                        }
                    }

                    eventAdapter.updateEvents(eventList)
                    eventAdapter.notifyDataSetChanged() // Notify adapter about data changes

                    Log.e("EventsActivity", "ALL VERIFIED EVENTS: $eventList")
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("EventsActivity", "Error loading data: ${error.message}")
                }
            })
        } else {
            // User is not authenticated
            Log.e("EventsActivity", "User is not authenticated. Unable to load events.")
            // Optionally handle the UI for non-authenticated state
        }
    }

    private fun loadUserEvents() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            val eventsRef = FirebaseDatabase.getInstance().getReference("Events")
            eventsRef.orderByChild("userId").equalTo(userId).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    eventList.clear() // Clear the list before adding new items
                    for (eventSnapshot in snapshot.children) {
                        val event = eventSnapshot.getValue(Event::class.java)
                        if (event != null) {
                            eventList.add(event)
                        }
                    }
                    myEventsAdapter.notifyDataSetChanged() // Notify adapter of data change
                    Log.e("Events", "Events: $eventList")

                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("Events", "Failed to load events: ${error.message}")
                }
            })
        } else {
            Log.e("Events", "User is not signed in.")
        }
    }

    private fun searchViewAppearance(){
        // set search view text hint color
        val searchEditText = binding.searchEvent.findViewById<EditText>(androidx.appcompat.R.id.search_src_text)
        searchEditText.setTextColor(ContextCompat.getColor(this, R.color.gray))
        searchEditText.setHintTextColor(ContextCompat.getColor(this, R.color.dark_gray))

        // close icon color
        val closeIcon = binding.searchEvent.findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)
        closeIcon.setColorFilter(ContextCompat.getColor(this, R.color.red))
    }

    private fun setupSearchView() {
        binding.searchEvent.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let {
                    eventAdapter.filter(it)
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                newText?.let {
                    eventAdapter.filter(it)
                }
                return true
            }
        })
    }
}
