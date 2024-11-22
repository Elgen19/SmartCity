package com.elgenium.smartcity

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.elgenium.smartcity.contextuals.ActivityPlaceRecommendation
import com.elgenium.smartcity.databinding.ActivityActiveActivitiesBinding
import com.elgenium.smartcity.databinding.BottomSheetActivityTripSummaryBinding
import com.elgenium.smartcity.databinding.BottomSheetAddActivityBinding
import com.elgenium.smartcity.databinding.BottomSheetViewActivityDetailBinding
import com.elgenium.smartcity.databinding.DialogActivitySuggestionsBinding
import com.elgenium.smartcity.intelligence.ActivityPlaceProcessor
import com.elgenium.smartcity.intelligence.ActivityPrioritizationOptimizer
import com.elgenium.smartcity.models.ActivityDetails
import com.elgenium.smartcity.models.LocationBasedPlaceRecommendationItems
import com.elgenium.smartcity.recyclerview_adapter.ActivityDetailsAdapter
import com.elgenium.smartcity.recyclerview_adapter.LocationBasedPlaceRecommendationAdapter
import com.elgenium.smartcity.routing.RouteFetcher
import com.elgenium.smartcity.singletons.NavigationBarColorCustomizerHelper
import com.elgenium.smartcity.singletons.PlacesNewClientSingleton
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

class ActiveActivitiesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityActiveActivitiesBinding
    private lateinit var bottomSheetBinding: BottomSheetAddActivityBinding
    private lateinit var placeAdapter: LocationBasedPlaceRecommendationAdapter
    private lateinit var activityAdapter: ActivityDetailsAdapter
    private val activityList = mutableListOf<ActivityDetails>()
    private val placesClient by lazy { PlacesNewClientSingleton.getPlacesClient(this) }
    private lateinit var prioritizationOptimizer: ActivityPrioritizationOptimizer
    private var placeId = "No place id"
    private var placeLatlng = "No latlng"
    private var containerId = ""
    private val latLngList = mutableListOf<String>()
    private val placeIdsList = mutableListOf<String>()
    private lateinit var routeFetcher: RouteFetcher

    private val searchActivityLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                val placeName = data?.getStringExtra("PLACE_NAME") ?: "Place Name"
                val placeAddress =
                    data?.getStringExtra("PLACE_ADDRESS") ?: "Address of the place here"
                val activity = data?.getStringExtra("ACTIVITY") ?: "No activity"
                val tempLatlng = data?.getStringExtra("PLACE_LATLNG") ?: ""
                placeLatlng = parseLatLng(tempLatlng) ?: "No latlng"
                placeId = data?.getStringExtra("PLACE_ID") ?: ""

                Log.e("ActivityPlaceProcessor", "ACTIVITY: $activity")
                Log.e("ActivityPlaceProcessor", "placeLatlng: $placeLatlng")

                showBottomSheet()

                // Update the CardView details
                bottomSheetBinding.tvActivityName.text = activity
                bottomSheetBinding.activityPrompter.visibility = View.GONE
                bottomSheetBinding.tvPlaceLabel.text = placeName
                bottomSheetBinding.tvAddressLabel.text = placeAddress
                bottomSheetBinding.mainContainer.visibility = View.VISIBLE
                bottomSheetBinding.btnConfirm.visibility = View.VISIBLE
                bottomSheetBinding.recommendationPlaceLayout.visibility = View.VISIBLE
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityActiveActivitiesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prioritizationOptimizer = ActivityPrioritizationOptimizer(this)
        routeFetcher = RouteFetcher(this, "DRIVE", latLngList)



        containerId = intent.getStringExtra("containerId") ?: ""

        // Initialize RecyclerView and Adapter
        activityAdapter = ActivityDetailsAdapter(activityList) { clickedActivity, position ->
            showActivityDetailsBottomSheet(clickedActivity, position)
        }
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@ActiveActivitiesActivity)
            adapter = activityAdapter
        }

        binding.btnConfirm.setOnClickListener {
            Log.d("ActiveActivitiesActivity", "LATLNG LIST SIZE: ${latLngList.size}")
            Log.d("ActiveActivitiesActivity", "LATLNG LIST: ${latLngList}")

            showTripSummaryBottomSheet()
        }


        fetchAndDisplayActivities(containerId)


        NavigationBarColorCustomizerHelper.setNavigationBarColor(this, R.color.primary_color)

        binding.fabAdd.setOnClickListener {
            showBottomSheet()
        }
    }


    private fun fetchAndUseCurrentLocation() {
        routeFetcher.getCurrentLocation(this) { latLng ->
            if (latLng != null) {
                Log.d(
                    "ActiveActivitiesActivity",
                    "Current Location: ${latLng.latitude}, ${latLng.longitude}"
                )

                val tempLatlng = parseLatLng(latLng.toString())
                if (tempLatlng != null) {
                    latLngList.add(tempLatlng)
                    Log.d(
                        "ActiveActivitiesActivity",
                        "LATLNG LIST AT CURRENT LOCATION: $latLngList"
                    )

                }
            } else {
                Log.e("ActiveActivitiesActivity", "Failed to fetch current location.")
            }
        }
    }

    private fun parseLatLng(latLngString: String): String? {
        // Regular expression to match the latitude and longitude
        val regex = Regex("""lat/lng: \(([^,]+),([^)]*)\)""")
        val matchResult = regex.find(latLngString)
        val (latitude, longitude) = matchResult?.destructured ?: return null
        return "$latitude,$longitude"
    }

    private fun showBottomSheet(clickedActivity: ActivityDetails? = null, position: Int? = null) {
        bottomSheetBinding = BottomSheetAddActivityBinding.inflate(layoutInflater)

        // Create a BottomSheetDialog
        val bottomSheetDialog = BottomSheetDialog(this)
        bottomSheetDialog.setContentView(bottomSheetBinding.root)

        var selectedPriority: String? = clickedActivity?.priorityLevel
        // Handle startTime and endTime when they are empty or null
        val format =
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) // Initialize SimpleDateFormat

        // Handling startTime only if it is not empty or null
        var startTime: Calendar? = if (!clickedActivity?.startTime.isNullOrBlank()) {
            val parsedStartTime =
                clickedActivity?.startTime?.let { format.parse(it) } // Parse the startTime string only if it is not blank
            parsedStartTime?.let { // Only create Calendar if parsing was successful
                Calendar.getInstance().apply { time = parsedStartTime }
            }
        } else {
            null // Do nothing if startTime is empty or null
        }

        // Handling endTime only if it is not empty or null
        var endTime: Calendar? = if (!clickedActivity?.endTime.isNullOrBlank()) {
            val parsedEndTime =
                clickedActivity?.endTime?.let { format.parse(it) } // Parse the endTime string only if it is not blank
            parsedEndTime?.let { // Only create Calendar if parsing was successful
                Calendar.getInstance().apply { time = parsedEndTime }
            }
        } else {
            null // Do nothing if endTime is empty or null
        }

        if (clickedActivity != null && position != null) {
            Log.d("INDEX", "position: $position")
            Log.d("INDEX", "activity list size: ${activityList.size}")


            bottomSheetBinding.mainContainer.visibility = View.VISIBLE
            bottomSheetBinding.btnConfirm.visibility = View.VISIBLE
            bottomSheetBinding.etActivity.setText(clickedActivity.activityName)
            bottomSheetBinding.tvPlaceLabel.text = clickedActivity.placeName
            bottomSheetBinding.tvActivityName.text = clickedActivity.activityName
            bottomSheetBinding.tvAddressLabel.text = clickedActivity.placeAddress
            when (clickedActivity.priorityLevel) {
                "High" -> togglePrioritySelection(
                    bottomSheetBinding.btnHighPriority,
                    bottomSheetBinding.btnMediumPriority,
                    bottomSheetBinding.btnLowPriority
                )

                "Medium" -> togglePrioritySelection(
                    bottomSheetBinding.btnMediumPriority,
                    bottomSheetBinding.btnHighPriority,
                    bottomSheetBinding.btnLowPriority
                )

                "Low" -> togglePrioritySelection(
                    bottomSheetBinding.btnLowPriority,
                    bottomSheetBinding.btnHighPriority,
                    bottomSheetBinding.btnMediumPriority
                )
            }
            startTime?.let {
                bottomSheetBinding.btnStartTime.text =
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(it.time)
            }
            endTime?.let {
                bottomSheetBinding.btnEndTime.text =
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(it.time)
            }
        }

        bottomSheetBinding.switchDisableLocation.isChecked = false
        var isLocationBasedRecommendationDisabled = false
        bottomSheetBinding.switchDisableLocation.setOnCheckedChangeListener { _, isChecked ->
            isLocationBasedRecommendationDisabled = isChecked
        }

        bottomSheetBinding.btnSet.setOnClickListener {
            bottomSheetBinding.recyclerViewRecommendations.visibility = View.GONE
            bottomSheetBinding.mainContainer.visibility = View.GONE
            bottomSheetBinding.btnConfirm.visibility = View.GONE
            bottomSheetBinding.tvPlaceRecomLabel.visibility = View.GONE
            bottomSheetBinding.activityPrompter.visibility = View.GONE

            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(bottomSheetBinding.root.windowToken, 0)

            val userQuery = bottomSheetBinding.etActivity.text.toString().trim()

            if (!isLocationBasedRecommendationDisabled) {
                if (userQuery.isNotEmpty()) {
                    bottomSheetBinding.lottieAnimation.visibility = View.VISIBLE
                    bottomSheetBinding.emptyDataLabel.visibility = View.VISIBLE
                    // Call the function to process the query asynchronously
                    lifecycleScope.launch {
                        val result = ActivityPlaceProcessor().processUserQuery(userQuery)

                        if (result != null) {
                            Log.d("ActivityPlaceProcessor", "Place: $result")
                            showToast("Displaying results")

                            // Assuming that you now need to get places related to this activity:
                            fetchPlacesBasedOnActivity(result)
                        } else {
                            Log.e("ActivityPlaceProcessor", "No valid result received")
                        }
                    }
                } else {
                    Log.e("ActivityPlaceProcessor", "User query is empty")
                    showToast("Please enter an activity.")
                }
            } else {
                bottomSheetDialog.dismiss()
                val intent = Intent(this, SearchActivity::class.java)
                intent.putExtra("FROM_ACTIVE_ACTIVITIES", true)
                intent.putExtra("ACTIVITY", userQuery)
                Log.e("ActivityPlaceProcessor", "Activity: ${bottomSheetBinding.etActivity.text}")

                searchActivityLauncher.launch(intent)
            }

        }

        // Handle priority button clicks
        bottomSheetBinding.btnHighPriority.setOnClickListener {
            togglePrioritySelection(
                bottomSheetBinding.btnHighPriority,
                bottomSheetBinding.btnMediumPriority,
                bottomSheetBinding.btnLowPriority
            )
            selectedPriority = if (bottomSheetBinding.btnHighPriority.isSelected) "High" else null
        }

        bottomSheetBinding.btnMediumPriority.setOnClickListener {
            togglePrioritySelection(
                bottomSheetBinding.btnMediumPriority,
                bottomSheetBinding.btnHighPriority,
                bottomSheetBinding.btnLowPriority
            )

            selectedPriority =
                if (bottomSheetBinding.btnMediumPriority.isSelected) "Medium" else null
        }

        bottomSheetBinding.btnLowPriority.setOnClickListener {
            togglePrioritySelection(
                bottomSheetBinding.btnLowPriority,
                bottomSheetBinding.btnHighPriority,
                bottomSheetBinding.btnMediumPriority
            )
            selectedPriority = if (bottomSheetBinding.btnLowPriority.isSelected) "Low" else null
            bottomSheetBinding.timeContraints.visibility = View.GONE
        }

        // Set start time with validation
        bottomSheetBinding.btnStartTime.setOnClickListener {
            showDateTimePicker { selectedCalendar ->
                if (selectedCalendar.after(Calendar.getInstance())) {
                    // Temporarily set startTime
                    val tempStartTime = selectedCalendar

                    // Check if this time is valid with other activities
                    if (endTime != null && !isTimeValid(tempStartTime, endTime!!, activityList)) {
                        return@showDateTimePicker
                    }

                    startTime = tempStartTime
                    bottomSheetBinding.btnStartTime.text = SimpleDateFormat(
                        "yyyy-MM-dd HH:mm",
                        Locale.getDefault()
                    ).format(startTime!!.time)
                } else {
                    showToast("Start time cannot be in the past.")
                }
            }
        }


        // Set end time with validation
        bottomSheetBinding.btnEndTime.setOnClickListener {
            showDateTimePicker { selectedCalendar ->
                if (selectedCalendar.after(Calendar.getInstance())) {

                    // Validate end time with start time and other activities
                    if (startTime != null && selectedCalendar.before(startTime)) {
                        showToast("End time cannot be before start time.")
                        return@showDateTimePicker
                    }

                    if (startTime != null && areTimesEqual(startTime!!, selectedCalendar)) {
                        showToast("End time cannot be the same as the start time.")
                        return@showDateTimePicker
                    }

                    // Check if the time is valid with other activities
                    if (!isTimeValid(startTime!!, selectedCalendar, activityList)) {
                        return@showDateTimePicker
                    }

                    endTime = selectedCalendar
                    bottomSheetBinding.btnEndTime.text = SimpleDateFormat(
                        "yyyy-MM-dd HH:mm",
                        Locale.getDefault()
                    ).format(endTime!!.time)
                } else {
                    showToast("End time cannot be in the past.")
                }
            }
        }

        // Confirm button logic
        bottomSheetBinding.btnConfirm.setOnClickListener {
            val activityName = bottomSheetBinding.tvActivityName.text.toString().replaceFirstChar {
                if (it.isLowerCase()) it.titlecase() else it.toString()
            }
            val placeName = bottomSheetBinding.tvPlaceLabel.text.toString()
            val placeAddress = bottomSheetBinding.tvAddressLabel.text.toString()
            var priority = selectedPriority
            val startTimeFormatted = startTime?.let {
                SimpleDateFormat(
                    "yyyy-MM-dd HH:mm",
                    Locale.getDefault()
                ).format(it.time)
            }
            val endTimeFormatted = endTime?.let {
                SimpleDateFormat(
                    "yyyy-MM-dd HH:mm",
                    Locale.getDefault()
                ).format(it.time)
            }

            if (activityName.isBlank() || placeName == "Place Name" || placeAddress == "Address of the place here") {
                showToast("Please fill in all required fields.")
                return@setOnClickListener
            }

            if (selectedPriority.isNullOrEmpty()) {
                priority = "Low"
            }


            if ((selectedPriority == "High" || selectedPriority == "Medium") && (startTime == null || endTime == null)) {
                showToast("Time constraints are required for High or Medium priority.")
                return@setOnClickListener
            }


            val activityDetails = ActivityDetails(
                activityName = activityName,
                placeName = placeName,
                placeAddress = placeAddress,
                priorityLevel = priority,
                startTime = startTimeFormatted,
                endTime = endTimeFormatted,
                placeId = placeId,
                placeLatlng = placeLatlng,

                )

            if (clickedActivity != null && position != null) {
                // Update the existing activity
                activityList[position] = activityDetails
                activityAdapter.notifyItemChanged(position)
            } else {
                // Add a new activity
                activityList.add(activityDetails)
                activityAdapter.notifyItemInserted(activityList.size - 1)
            }

            // Update Firebase and dismiss the dialog
            saveAllActivitiesToFirebase(activityList, containerId)
            latLngList.add(placeLatlng)
            checkRecyclerViewData()
            bottomSheetDialog.dismiss()
        }

        // Cancel button logic
        bottomSheetBinding.btnCancel.setOnClickListener {
            fetchAndDisplayActivities(containerId)
            bottomSheetDialog.dismiss()
        }


        bottomSheetDialog.show()
    }

    private fun showActivityDetailsBottomSheet(clickedActivity: ActivityDetails, position: Int) {
        // Inflate the bottom sheet layout using ViewBinding
        val bottomSheetBinding = BottomSheetViewActivityDetailBinding.inflate(layoutInflater)

        // Set the values for the views in the bottom sheet using the clickedActivity object
        bottomSheetBinding.tvActivityName.text = clickedActivity.activityName
        bottomSheetBinding.tvPlaceName.text = clickedActivity.placeName
        bottomSheetBinding.tvPlaceAddress.text = clickedActivity.placeAddress
        bottomSheetBinding.tvPriority.text =
            clickedActivity.priorityLevel ?: "No priority level set"
        bottomSheetBinding.tvTimeRange.text =
            formatTimeRange(clickedActivity.startTime, clickedActivity.endTime)

        // Create the BottomSheetDialog and set the view using ViewBinding
        val bottomSheetDialog = BottomSheetDialog(this)
        bottomSheetDialog.setContentView(bottomSheetBinding.root)

        // Handle the button actions inside the bottom sheet
        bottomSheetBinding.btnEdit.setOnClickListener {
            showBottomSheet(clickedActivity, position)
            bottomSheetDialog.dismiss()
        }

        bottomSheetBinding.btnDelete.setOnClickListener {
            // Get the activity to delete
            val activityToDelete = activityList[position]
            val activityId = activityToDelete.activityId

            Log.d("Delete", "Activity ID: $activityId")  // Add this line for debugging
            Log.d("Delete", "Activity: $activityToDelete")  // Add this line for debugging


            if (activityId.isNullOrBlank()) {
                activityAdapter.removeActivity(position)
                Toast.makeText(
                    this@ActiveActivitiesActivity,
                    "Activity deleted",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val userId = FirebaseAuth.getInstance().currentUser?.uid
            if (userId != null) {
                // Reference to the Firebase node
                val databaseReference = FirebaseDatabase.getInstance().reference
                    .child("Users")
                    .child(userId)
                    .child("MyActivities")
                    .child(containerId)
                    .child("activities")

                // Remove the activity from Firebase using its activityId
                databaseReference.child(activityId).removeValue()
                    .addOnSuccessListener {
                        // Successfully deleted the activity from Firebase
                        Log.d("Firebase", "Activity deleted successfully")
                        // Remove the activity from the adapter and update the RecyclerView
                        activityList.removeAt(position)
                        activityAdapter.notifyItemRemoved(position)
                        checkRecyclerViewData() // Your custom method to check if there is data left in the list
                    }
                    .addOnFailureListener { exception ->
                        // Handle error if the deletion fails
                        Log.e("Firebase", "Failed to delete activity: ${exception.message}")
                        Toast.makeText(
                            this@ActiveActivitiesActivity,
                            "Failed to delete activity",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }

            fetchAndDisplayActivities(containerId)
            // Dismiss the bottom sheet
            bottomSheetDialog.dismiss()
        }


        bottomSheetBinding.btnClose.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        // Show the BottomSheetDialog
        bottomSheetDialog.show()
    }

    private fun fetchPlacesBasedOnActivity(places: List<String>) {
        Log.d("ActiveActivitiesActivity", "Fetching places for: '$places'")
        ActivityPlaceRecommendation(this).performTextSearch(placesClient, places) { placeItems ->
            Log.d("ActiveActivitiesActivity", "Fetched ${placeItems.size} place recommendations")

            // Initialize the RecyclerView with default visibility
            setupRecyclerView(placeItems)

            // Store the original list for filters
            val originalPlaceItems = placeItems

            // Set up filter logic
            setupChipListeners(originalPlaceItems)

        }
    }


    private fun setupRecyclerView(placeItems: List<LocationBasedPlaceRecommendationItems>) {
        bottomSheetBinding.recyclerViewRecommendations.layoutManager = LinearLayoutManager(this)
        bottomSheetBinding.tvPlaceRecomLabel.visibility = View.VISIBLE
        bottomSheetBinding.recommendationPlaceLayout.visibility = View.VISIBLE

        updateRecyclerViewAdapter(placeItems)
    }

    private fun updateRecyclerViewAdapter(filteredItems: List<LocationBasedPlaceRecommendationItems>) {
        // Find the closest place
        val closestPlace = filteredItems.minByOrNull {
            it.distance.replace(" km", "").toDoubleOrNull() ?: Double.MAX_VALUE
        }

        placeAdapter = LocationBasedPlaceRecommendationAdapter(filteredItems) { selectedPlace ->
            Log.d(
                "ActiveActivitiesActivity",
                "Clicked on place: ${selectedPlace.name}, ${selectedPlace.address}"
            )

            val selectedDistance = selectedPlace.distance.replace(" km", "").toDoubleOrNull()
            val closestDistance = closestPlace?.distance?.replace(" km", "")?.toDoubleOrNull()

            if (selectedDistance != null && closestDistance != null && selectedDistance > closestDistance) {
                // Show dialog when the selected place is farther than the closest one
                showSuggestionsDialog(
                    title = "Closer Place Found",
                    message = "You selected <b>${selectedPlace.name}</b> (<b>${selectedPlace.distance}</b>), but a closer option is available at " +
                            "<b>${closestPlace.name}</b> in only <b>${closestPlace.distance}</b>. Do you want to continue?",
                    isActionButtonVisible = true,
                    onDismiss = {
                        // Finalize the selection if dismissed
                        Log.d(
                            "ActiveActivitiesActivity",
                            "Selection finalized for ${selectedPlace.name}"
                        )
                        displaySelectedPlace(selectedPlace)
                    },
                    onAction = {
                        // Allow re-selection
                        Log.d("ActiveActivitiesActivity", "User opted to reselect a place")
                        Toast.makeText(this, "Please select a closer place", Toast.LENGTH_SHORT)
                            .show()
                    }
                )
            } else {
                // Finalize selection if no dialog is shown
                displaySelectedPlace(selectedPlace)
            }
        }

        bottomSheetBinding.recyclerViewRecommendations.adapter = placeAdapter

        // Manage empty state
        if (filteredItems.isNotEmpty()) {
            bottomSheetBinding.recyclerViewRecommendations.visibility = View.VISIBLE
            bottomSheetBinding.lottieAnimation.visibility = View.GONE
            bottomSheetBinding.emptyDataLabel.visibility = View.GONE
        } else {
            bottomSheetBinding.recyclerViewRecommendations.visibility = View.GONE
            bottomSheetBinding.lottieAnimation.setAnimation(R.raw.no_data)
            bottomSheetBinding.emptyDataLabel.text =
                getString(R.string.no_places_found_please_try_again)
        }
    }


    private fun filterPlacesByDistance(
        placeItems: List<LocationBasedPlaceRecommendationItems>,
        maxDistanceKm: Double
    ) {
        val filteredItems = placeItems.filter { place ->
            val distanceInKm = place.distance.replace(" km", "").toDoubleOrNull()
            distanceInKm != null && distanceInKm <= maxDistanceKm
        }
        Log.d("Filter", "Filtered places within $maxDistanceKm km: ${filteredItems.size}")
        updateRecyclerViewAdapter(filteredItems)
    }

    private fun filterPlacesByRating(
        placeItems: List<LocationBasedPlaceRecommendationItems>,
        minRating: Double,
        maxRating: Double
    ) {
        val filteredItems = placeItems.filter { place ->
            val rating = place.ratings.toDoubleOrNull()
            rating != null && rating in minRating..maxRating
        }
        updateRecyclerViewAdapter(filteredItems)
    }

    private fun displaySelectedPlace(selectedPlace: LocationBasedPlaceRecommendationItems) {
        bottomSheetBinding.tvActivityName.text = bottomSheetBinding.etActivity.text
        bottomSheetBinding.tvPlaceLabel.text = selectedPlace.name
        bottomSheetBinding.tvAddressLabel.text = selectedPlace.address
        bottomSheetBinding.mainContainer.visibility = View.VISIBLE
        bottomSheetBinding.btnConfirm.visibility = View.VISIBLE
        bottomSheetBinding.recommendationPlaceLayout.visibility = View.GONE
        placeId = selectedPlace.placeId
        placeLatlng = parseLatLng(selectedPlace.placeLatlng) ?: "No latlng"
    }

    private fun setupChipListeners(
        originalPlaceItems: List<LocationBasedPlaceRecommendationItems>
    ) {
        // Show All Chip
        bottomSheetBinding.chipShowAll.setOnClickListener {
            // Change the background color when clicked
            bottomSheetBinding.chipShowAll.chipBackgroundColor =
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.brand_color))

            // Reset background color for other chips
            resetChipBackground(bottomSheetBinding.chipUnder1Km, bottomSheetBinding.chipPopular)

            updateRecyclerViewAdapter(originalPlaceItems)
        }

        // < 1km Chip
        bottomSheetBinding.chipUnder1Km.setOnClickListener {
            Log.d("ChipUnder1Km", "Clicked")
            // Change the background color when clicked
            bottomSheetBinding.chipUnder1Km.chipBackgroundColor =
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.brand_color))

            // Reset background color for other chips
            resetChipBackground(bottomSheetBinding.chipShowAll, bottomSheetBinding.chipPopular)

            filterPlacesByDistance(originalPlaceItems, 1.0)
        }

        // Popular Chip
        bottomSheetBinding.chipPopular.setOnClickListener {
            Log.d("ChipPopular", "Clicked")
            // Change the background color when clicked
            bottomSheetBinding.chipPopular.chipBackgroundColor =
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.brand_color))

            // Reset background color for other chips
            resetChipBackground(bottomSheetBinding.chipShowAll, bottomSheetBinding.chipUnder1Km)

            filterPlacesByRating(originalPlaceItems, 4.0, 5.0)
        }
    }

    // Reset the background color of other chips
    private fun resetChipBackground(vararg chips: Chip) {
        chips.forEach { chip ->
            chip.chipBackgroundColor =
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary_color))
        }
    }

    private fun togglePrioritySelection(
        selectedButton: MaterialButton,
        vararg otherButtons: MaterialButton
    ) {
        val defaultColorMap = mapOf(
            R.id.btnHighPriority to R.color.red,
            R.id.btnMediumPriority to R.color.bronze,
            R.id.btnLowPriority to R.color.green
        )

        // Deselect other buttons
        otherButtons.forEach { button ->
            button.isSelected = false
            button.setBackgroundColor(
                getColor(
                    defaultColorMap[button.id] ?: R.color.secondary_color
                )
            )
        }

        // Toggle the selected button
        selectedButton.isSelected = !selectedButton.isSelected
        bottomSheetBinding.timeContraints.visibility =
            if (selectedButton.isSelected) View.VISIBLE else View.GONE
        val defaultColor = defaultColorMap[selectedButton.id] ?: R.color.secondary_color
        selectedButton.setBackgroundColor(
            if (selectedButton.isSelected) getColor(R.color.brand_color) else getColor(defaultColor)
        )
    }

    private fun showDateTimePicker(onDateTimeSelected: (Calendar) -> Unit) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                TimePickerDialog(this, { _, hour, minute ->
                    val selectedCalendar = Calendar.getInstance().apply {
                        set(year, month, day, hour, minute)
                    }
                    onDateTimeSelected(selectedCalendar)
                }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun areTimesEqual(startTime: Calendar, endTime: Calendar): Boolean {
        // Normalize both times by setting milliseconds and seconds to 0
        startTime.set(Calendar.MILLISECOND, 0)
        startTime.set(Calendar.SECOND, 0)

        endTime.set(Calendar.MILLISECOND, 0)
        endTime.set(Calendar.SECOND, 0)

        return startTime.compareTo(endTime) == 0
    }

    private fun formatTimeRange(startTime: String?, endTime: String?): String {
        return when {
            startTime.isNullOrBlank() && endTime.isNullOrBlank() -> "No time constraints"
            startTime == "" && endTime == "" -> "No time constraints"  // Handle case for empty strings
            startTime.isNullOrBlank() -> "Ends at ${formatTime(endTime)}"
            endTime.isNullOrBlank() -> "Starts at ${formatTime(startTime)}"
            else -> "${formatTime(startTime)} - ${formatTime(endTime)}"
        }
    }

    private fun formatTime(time: String?): String {
        // Handle cases where time is null, empty, or contains placeholder strings
        if (time.isNullOrBlank() || time.contains("No")) {
            return "No time set"
        }

        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val outputFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val date = inputFormat.parse(time)
            outputFormat.format(date)
        } catch (e: ParseException) {
            Log.e("FormatTime", "Error parsing time: $time", e)
            "Invalid time"
        }
    }

    private fun checkRecyclerViewData() {
        if (activityList.isEmpty()) {
            // Show loading animation and text
            binding.lottieAnimation.visibility = View.VISIBLE
            binding.loadingText.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
            binding.btnConfirm.visibility = View.GONE
        } else {
            // Hide loading animation and text, show RecyclerView
            binding.lottieAnimation.visibility = View.GONE
            binding.loadingText.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
            binding.btnConfirm.visibility = View.VISIBLE

        }
    }

    private fun isTimeValid(
        startTime: Calendar,
        endTime: Calendar,
        existingActivities: List<ActivityDetails>
    ): Boolean {
        for (activity in existingActivities) {
            if (activity.priorityLevel in listOf("High", "Medium")) {
                val existingStart = activity.startTime?.let { parseToCalendar(it) }
                val existingEnd = activity.endTime?.let { parseToCalendar(it) }
                val dateFormatter = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
                val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())

                val scheduledTime = if (existingStart != null && existingEnd != null) {
                    if (dateFormatter.format(existingStart.time) == dateFormatter.format(existingEnd.time)) {
                        // Same day: Condense to single date with time range
                        "${dateFormatter.format(existingStart.time)} from ${
                            timeFormatter.format(
                                existingStart.time
                            )
                        } to ${timeFormatter.format(existingEnd.time)}"
                    } else {
                        // Different days: Show full dates and times
                        "${dateFormatter.format(existingStart.time)} at ${
                            timeFormatter.format(
                                existingStart.time
                            )
                        } to ${dateFormatter.format(existingEnd.time)} at ${
                            timeFormatter.format(
                                existingEnd.time
                            )
                        }"
                    }
                } else {
                    "Unavailable" // Fallback message for null values
                }

                val selectedTime =
                    if (dateFormatter.format(startTime.time) == dateFormatter.format(endTime.time)) {
                        // Same day: Condense to single date with time range
                        "${dateFormatter.format(startTime.time)} from ${
                            timeFormatter.format(
                                startTime.time
                            )
                        } to ${timeFormatter.format(endTime.time)}"
                    } else {
                        // Different days: Show full dates and times
                        "${dateFormatter.format(startTime.time)} at ${timeFormatter.format(startTime.time)} to ${
                            dateFormatter.format(
                                endTime.time
                            )
                        } at ${timeFormatter.format(endTime.time)}"
                    }


                // Skip activities without proper time data
                if (existingStart == null || existingEnd == null) continue

                // Check for overlap
                val isOverlapping =
                    startTime.timeInMillis < existingEnd.timeInMillis && endTime.timeInMillis > existingStart.timeInMillis
                if (isOverlapping) {
                    showSuggestionsDialog(
                        title = "Time Conflict",
                        message = """
                            The selected time for this activity overlaps with another activity, <b>${activity.activityName}</b>.
                            <br/><br/>
                            <ul>
                                <li><b>Scheduled Time:</b> $scheduledTime</li>
                                <li><b>Your Selected Time:</b> $selectedTime</li>
                            </ul>
                            <br/>
                            Choose a different start or end time for this activity to avoid overlapping with the existing one.
                        """.trimIndent(),
                        isActionButtonVisible = false,
                        onDismiss = {
                            Log.d("ConflictDialog", "User dismissed the dialog.")
                        },
                        onAction = {
                            Log.d("ConflictDialog", "User will adjust activity timing.")
                        }
                    )


                    return false
                }

                // Check for buffer time (15 minutes)
                val bufferTimeMillis = 15 * 60 * 1000
                val hasBuffer =
                    (abs(existingEnd.timeInMillis - startTime.timeInMillis) >= bufferTimeMillis) &&
                            (abs(endTime.timeInMillis - existingStart.timeInMillis) >= bufferTimeMillis)
                if (!hasBuffer) {
                    showSuggestionsDialog(
                        title = "Insufficient Buffer Time",
                        message = """
                            <p>There is not enough buffer time between this activity and another, <b>${activity.activityName}</b>.</p>
                            
                            <ul>
                                <li><b>Conflict Activity:</b> ${activity.activityName}</li>
                                <li><b>Scheduled Time:</b> $scheduledTime</li>
                                <li><b>Your Selected Time:</b> $selectedTime</li>
                            </ul>
                            
                        <p>Please adjust the start time of this activity to allow at least a 15-minute buffer for this activity.</p>
                        """.trimIndent(),
                        isActionButtonVisible = false,
                        onDismiss = {
                            Log.d("ConflictDialog", "User dismissed the dialog.")
                        },
                        onAction = {
                            Log.d("ConflictDialog", "User will adjust activity timing.")
                        }
                    )

                    return false
                }
            }
        }
        return true
    }

    private fun parseToCalendar(dateString: String): Calendar {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return Calendar.getInstance().apply { time = format.parse(dateString)!! }
    }

    private fun showSuggestionsDialog(
        title: String,
        message: String,
        isActionButtonVisible: Boolean,
        onDismiss: () -> Unit,
        onAction: () -> Unit
    ) {
        val binding = DialogActivitySuggestionsBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this).setView(binding.root).create()

        // Set dialog title and message
        binding.dialogTitle.text = title
        binding.dialogMessage.text = HtmlCompat.fromHtml(message, HtmlCompat.FROM_HTML_MODE_LEGACY)

        // Handle "Dismiss" button (finalize selection)
        binding.btnDismiss.setOnClickListener {
            dialog.dismiss()
            onDismiss()
        }


        if (isActionButtonVisible) {
            binding.btnAction.visibility = View.VISIBLE
            binding.btnAction.setOnClickListener {
                dialog.dismiss()
                onAction()
            }
        }

        dialog.show()
    }


    private fun saveAllActivitiesToFirebase(
        activityDetailsList: List<ActivityDetails>,
        containerId: String
    ) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show()
            return
        }

        val databaseReference = FirebaseDatabase.getInstance().reference
            .child("Users")
            .child(userId)
            .child("MyActivities")
            .child(containerId)
            .child("activities")

        // First, clear existing activities
        databaseReference.removeValue().addOnCompleteListener { removeTask ->
            if (removeTask.isSuccessful) {
                // Proceed to save new activities
                val batchUpdates = mutableListOf<Task<Void>>()

                for (activityDetails in activityDetailsList) {
                    val activityId = databaseReference.push().key // Generate a unique key

                    if (activityId != null) {
                        val activityWithId = activityDetails.copy(activityId = activityId)

                        val task = databaseReference.child(activityId).setValue(activityWithId)
                            .addOnFailureListener { exception ->
                                Log.e("Firebase", "Failed to save activity: ${exception.message}")
                            }
                        batchUpdates.add(task)
                    }
                }

                if (batchUpdates.isNotEmpty()) {
                    Tasks.whenAll(batchUpdates).addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(
                                this,
                                "All activities saved successfully!",
                                Toast.LENGTH_SHORT
                            ).show()

                            // Fetch updated list and update RecyclerView
                            fetchAndDisplayActivities(containerId)
                        } else {
                            Toast.makeText(
                                this,
                                "Failed to save some activities.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    // If no activities to save, just fetch the latest data
                    fetchAndDisplayActivities(containerId)
                }
            } else {
                Toast.makeText(this, "Failed to clear existing activities.", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun fetchAndDisplayActivities(containerId: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show()
            Log.e("FetchActivities", "User not logged in.")
            return
        }

        val databaseReference = FirebaseDatabase.getInstance().reference
            .child("Users")
            .child(userId)
            .child("MyActivities")
            .child(containerId)
            .child("activities")

        databaseReference.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val newActivityList = mutableListOf<ActivityDetails>()

                    for (activitySnapshot in snapshot.children) {
                        val activity = activitySnapshot.getValue(ActivityDetails::class.java)
                        if (activity != null) {
                            newActivityList.add(activity)
                        }
                    }

                    if (newActivityList.isNotEmpty()) {
                        // Before updating the activityList, prioritize the activities
                        val currentTime = System.currentTimeMillis()
                        prioritizationOptimizer.prioritizeActivities(
                            newActivityList,
                            currentTime
                        ) { prioritizedList ->
                            Log.d(
                                "ActiveActivitiesActivity",
                                "Prioritized activities: $prioritizedList"
                            )

                            // Update the activityList with the prioritized list
                            activityList.clear()
                            activityList.addAll(prioritizedList)

                            // Notify the adapter about the changes
                            activityAdapter.notifyDataSetChanged()

                            // Update the LatLng list from activities after prioritization
                            updateLatLngListFromActivities()
                            updatePlaceIdsListFromActivities()
                        }
                    } else {
                        checkRecyclerViewData()
                        updateLatLngListFromActivities()
                        updatePlaceIdsListFromActivities()
                    }
                } else {
                    checkRecyclerViewData()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(
                    this@ActiveActivitiesActivity,
                    "Failed to fetch activities: ${error.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun updateLatLngListFromActivities() {
        latLngList.clear()
        fetchAndUseCurrentLocation()
        for (activity in activityList) {
            activity.placeLatlng.let { latLng ->
                latLngList.add(latLng)
            }
        }

        rearrangeLatLngList()

        Log.e("ActiveActivitiesActivity", "Updated latLngList: $latLngList")
    }

    private fun updatePlaceIdsListFromActivities() {
        placeIdsList.clear()
        for (activity in activityList) {
            activity.placeId.let { placeids ->
                placeIdsList.add(placeids)
            }
        }

        rearrangeLatLngList()

        Log.e("ActiveActivitiesActivity", "PLACE IDS: $placeIdsList")
    }

    private fun rearrangeLatLngList() {
        // Check if the list has at least two elements to rearrange
        if (latLngList.size > 1) {
            // Reverse the list to have the oldest location at the front
            latLngList.reverse()

            Log.e("ActiveActivitiesActivity", "Rearranged latLngList: $latLngList")
        }
    }

    private fun showTripSummaryBottomSheet() {
        // Initialize the View Binding
        val binding = BottomSheetActivityTripSummaryBinding.inflate(layoutInflater)

        // Initialize the BottomSheetDialog
        val bottomSheetDialog = BottomSheetDialog(this)
        bottomSheetDialog.setContentView(binding.root)

        // Fetch route and update UI when ready
        routeFetcher.fetchRoute {
            binding.tvTotalActivities.text = "Total Activities: ${activityAdapter.itemCount}"
            binding.tvTotalDistance.text = "Total Distance: ${routeFetcher.getTotalDistance()}"
            binding.tvTotalDuration.text = "Duration: ${routeFetcher.getTotalDuration()}"
            binding.tvTrafficCondition.text =
                "Traffic Condition: ${routeFetcher.determineOverallTrafficCondition()}"
            val routeToken = routeFetcher.getCustomRouteToken()
            // Handle Close button click
            binding.btnClose.setOnClickListener {
                bottomSheetDialog.dismiss()
            }

            // Handle Start Navigation button click
            binding.btnStartNavigation.setOnClickListener {
                val intent = Intent(
                    this@ActiveActivitiesActivity,
                    StartNavigationsActivity::class.java
                ).apply {
                    putExtra("IS_SIMULATED", false)
                    putExtra("ROUTE_TOKEN", routeToken)
                    putExtra("IS_SIMULATED", false)
                    putExtra("TRAVEL_MODE", "DRIVE")
                    putStringArrayListExtra("PLACE_IDS", ArrayList(placeIdsList))
                }
                // Log the intent before starting the activity
                Log.e("ActiveActivitiesActivity", intent.extras.toString())

                startActivity(intent)
                bottomSheetDialog.dismiss()
            }

            // Handle Simulate button click
            binding.btnSimulate.setOnClickListener {
                val intent = Intent(
                    this@ActiveActivitiesActivity,
                    StartNavigationsActivity::class.java
                ).apply {
                    putExtra("IS_SIMULATED", false)
                    putExtra("ROUTE_TOKEN", routeToken)
                    putExtra("IS_SIMULATED", true)
                    putExtra("TRAVEL_MODE", "DRIVE")
                    putStringArrayListExtra("PLACE_IDS", ArrayList(placeIdsList))
                }
                // Log the intent before starting the activity
                Log.e("ActiveActivitiesActivity", intent.extras.toString())

                startActivity(intent)
                bottomSheetDialog.dismiss()
            }

            // Show the BottomSheetDialog
            bottomSheetDialog.show()
        }
    }


}
