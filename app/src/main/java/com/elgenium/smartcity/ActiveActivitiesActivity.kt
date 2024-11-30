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
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.elgenium.smartcity.contextuals.ActivityPlaceRecommendation
import com.elgenium.smartcity.databinding.ActivityActiveActivitiesBinding
import com.elgenium.smartcity.databinding.BottomSheetActivityTripSummaryBinding
import com.elgenium.smartcity.databinding.BottomSheetAddActivityBinding
import com.elgenium.smartcity.databinding.BottomSheetViewActivityDetailBinding
import com.elgenium.smartcity.databinding.DialogActivitySuggestionsBinding
import com.elgenium.smartcity.intelligence.ActivityPlaceProcessor
import com.elgenium.smartcity.intelligence.ActivityPrioritizationOptimizer
import com.elgenium.smartcity.intelligence.ProximityCalculator
import com.elgenium.smartcity.models.ActivityDetails
import com.elgenium.smartcity.models.LocationBasedPlaceRecommendationItems
import com.elgenium.smartcity.recyclerview_adapter.ActivityDetailsAdapter
import com.elgenium.smartcity.recyclerview_adapter.LocationBasedPlaceRecommendationAdapter
import com.elgenium.smartcity.routing.RouteFetcher
import com.elgenium.smartcity.singletons.NavigationBarColorCustomizerHelper
import com.elgenium.smartcity.singletons.PlacesNewClientSingleton
import com.elgenium.smartcity.work_managers.ActivityNotificationWorker
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

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
    private var containerStatus = ""
    private val latLngList = mutableListOf<String>()
    private val placeIdsList = mutableListOf<String>()
    private lateinit var routeFetcher: RouteFetcher
    private val finishedActivitiesList = mutableListOf<ActivityDetails>()
    private lateinit var finishedActivitiesAdapter: ActivityDetailsAdapter
    private var selectedActivity: ActivityDetails? = null
    private var selectedActivityPosition: Int? = null
    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    private val scheduledActivities = mutableSetOf<String>()
    private lateinit var proximityCalculator: ProximityCalculator
    private var placeTypes = ""



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


               if (selectedActivity!= null)
                   showBottomSheet(selectedActivity, selectedActivityPosition)
                else
                   showBottomSheet()

                // Update the CardView details
                bottomSheetBinding.tvActivityName.text = activity
                bottomSheetBinding.activityPrompter.visibility = View.GONE
                bottomSheetBinding.tvPlaceLabel.text = placeName
                bottomSheetBinding.tvAddressLabel.text = placeAddress
                bottomSheetBinding.mainContainer.visibility = View.VISIBLE
                bottomSheetBinding.btnConfirm.visibility = View.VISIBLE
                bottomSheetBinding.recommendationPlaceLayout.visibility = View.GONE
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityActiveActivitiesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prioritizationOptimizer = ActivityPrioritizationOptimizer(this)
        routeFetcher = RouteFetcher(this, "DRIVE", latLngList)



        containerId = intent.getStringExtra("containerId") ?: ""
        containerStatus = intent.getStringExtra("containerStatus") ?: ""



        // Initialize RecyclerView and Adapter
        activityAdapter = ActivityDetailsAdapter(activityList) { clickedActivity, position ->
            selectedActivity = clickedActivity
            selectedActivityPosition = position
            showActivityDetailsBottomSheet(selectedActivity!!, selectedActivityPosition!!)
        }
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@ActiveActivitiesActivity)
            adapter = activityAdapter
        }

        proximityCalculator = ProximityCalculator(this, placesClient)

        finishedActivitiesAdapter = ActivityDetailsAdapter(finishedActivitiesList) { clickedActivity, position ->
            selectedActivity = clickedActivity
            selectedActivityPosition = position
            showActivityDetailsBottomSheet(selectedActivity!!, selectedActivityPosition!!)
        }
        binding.recyclerViewEndActivities.apply {
            layoutManager = LinearLayoutManager(this@ActiveActivitiesActivity)
            adapter = finishedActivitiesAdapter
        }

        binding.finishedLabel.visibility = if (finishedActivitiesList.isNotEmpty()) View.VISIBLE else View.GONE
        binding.btnCancel.visibility = if (containerStatus == "Scheduled") View.VISIBLE else View.GONE
        binding.activeLabel.visibility = if (containerStatus == "Scheduled") View.VISIBLE else View.GONE
        binding.addAndConfirmButtonRows.visibility = if (containerStatus == "Unscheduled" || containerStatus == "None") View.VISIBLE else View.GONE


        binding.btnConfirm.setOnClickListener {
            Log.d("ActiveActivitiesActivity", "LATLNG LIST SIZE: ${latLngList.size}")
            Log.d("ActiveActivitiesActivity", "LATLNG LIST: $latLngList")

            showTripSummaryBottomSheet()
        }

        binding.btnCancel.setOnClickListener {
            val workManager = WorkManager.getInstance(this)
            workManager.cancelAllWork()
            Toast.makeText(this, "All work requests have been canceled.", Toast.LENGTH_SHORT).show()

            activityList.forEach { activity ->
                activity.containerStatus = "Unscheduled"
            }
            updateActivityStatus(containerId, "Unscheduled")
            containerStatus = "Unscheduled"
            activityAdapter.notifyDataSetChanged()
            fetchAndDisplayActivities(containerId)
            binding.activeLabel.visibility = View.GONE
            binding.btnCancel.visibility = View.GONE
            binding.addAndConfirmButtonRows.visibility = View.VISIBLE

        }



        fetchAndDisplayActivities(containerId)

        Log.e("listAfterDeletion", "ActivityList: $activityList")
        Log.e("listAfterDeletion", "FinishedActivity list: $finishedActivitiesList")

        NavigationBarColorCustomizerHelper.setNavigationBarColor(this, R.color.primary_color)

        binding.fabAdd.setOnClickListener {
            selectedActivity = null
            selectedActivityPosition = null
            showBottomSheet(null, null)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Define the behavior when the back button is pressed
                val intent = Intent(this@ActiveActivitiesActivity, MyActivitiesActivity::class.java)
                startActivity(intent)
                finish() // This will finish the current activity
            }
        })
    }


    private fun fetchAndUseCurrentLocation(callback: (String?) -> Unit) {
        routeFetcher.getCurrentLocation(this) { latLng ->
            if (latLng != null) {
                Log.d(
                    "ActiveActivitiesActivity",
                    "Current Location: ${latLng.latitude}, ${latLng.longitude}"
                )

                val tempLatlng = parseLatLng(latLng.toString())
                if (tempLatlng != null) {
                    callback(tempLatlng)
                } else {
                    Log.e("ActiveActivitiesActivity", "Failed to parse current location.")
                    callback(null)
                }
            } else {
                Log.e("ActiveActivitiesActivity", "Failed to fetch current location.")
                callback(null)
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

        var selectedPriority: String? = clickedActivity?.priorityLevel ?: "Low"
        // Handle startTime and endTime when they are empty or null
        val format =
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) // Initialize SimpleDateFormat

        var startTime: Calendar? = if (!clickedActivity?.startTime.isNullOrBlank()) {
            val parsedStartTime = clickedActivity?.startTime?.let { format.parse(it) }
            parsedStartTime?.let { Calendar.getInstance().apply { time = parsedStartTime } }
        } else {
            null // Set to null or use a default value if needed
        }

        var endTime: Calendar? = if (!clickedActivity?.endTime.isNullOrBlank()) {
            val parsedEndTime = clickedActivity?.endTime?.let { format.parse(it) }
            parsedEndTime?.let { Calendar.getInstance().apply { time = parsedEndTime } }
        } else {
            null // Set to null or use a default value if needed
        }


        if (clickedActivity != null && position != null) {
            Log.d("INDEX", "position: $position")
            Log.d("INDEX", "activity list size: ${activityList.size}")

            placeLatlng = clickedActivity.placeLatlng
            placeId = clickedActivity.placeId
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
        bottomSheetBinding.timeContraints.visibility = if(selectedPriority == "Low") View.GONE else View.VISIBLE

        bottomSheetBinding.btnSet.setOnClickListener {
            if (!bottomSheetBinding.etActivity.text.isNullOrEmpty()){
                bottomSheetBinding.recyclerViewRecommendations.visibility = View.GONE
                bottomSheetBinding.mainContainer.visibility = View.GONE
                bottomSheetBinding.btnConfirm.visibility = View.GONE
                bottomSheetBinding.tvPlaceRecomLabel.visibility = View.GONE
                bottomSheetBinding.activityPrompter.visibility = View.GONE

                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(bottomSheetBinding.root.windowToken, 0)

                val userQuery = bottomSheetBinding.etActivity.text.toString().trim()


                if (userQuery.isNotEmpty()) {
                    bottomSheetBinding.lottieAnimation.visibility = View.VISIBLE
                    bottomSheetBinding.emptyDataLabel.visibility = View.VISIBLE

                    // Process the user query asynchronously
                    lifecycleScope.launch {
                        val result = ActivityPlaceProcessor().processUserQuery(userQuery)

                        withContext(Dispatchers.Main){
                            if (result != null) {
                                Log.d("ActivityPlaceProcessor", "Place: $result")
                                showToast("Displaying results")

                                // Check if location-based recommendations are disabled
                                if (isLocationBasedRecommendationDisabled) {
                                    // Fetch and display the list of recommendations
                                    fetchPlacesBasedOnActivity(bottomSheetDialog, userQuery, result)
                                } else {
                                    // Fetch and display only the nearest place
                                    fetchNearestPlace(bottomSheetDialog, userQuery, result)
                                }
                            } else {
                                Log.e("ActivityPlaceProcessor", "No valid result received")
                                showToast("No valid result found.")
                            }
                        }
                    }
                } else {
                    Log.e("ActivityPlaceProcessor", "User query is empty")
                    showToast("Please enter an activity.")
                }

            } else {
                showToast("Please enter an activity")
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
            // If Low priority is selected, remove time constraints
            if (selectedPriority == "Low") {
                startTime = null
                endTime = null
                bottomSheetBinding.btnStartTime.text = getString(R.string.set_start_time)
                bottomSheetBinding.btnEndTime.text = getString(R.string.set_end_time)
                bottomSheetBinding.timeContraints.visibility = View.GONE // Optionally hide time constraints UI
            }
        }

        // Set start time with validation
        bottomSheetBinding.btnStartTime.setOnClickListener {
            showDateTimePicker { selectedCalendar ->
                if (selectedCalendar.after(Calendar.getInstance())) {
                    // Temporarily set startTime
                    val tempStartTime = selectedCalendar

                    // Check if this time is valid with other activities
                    if (endTime != null && !isTimeValid(tempStartTime, endTime!!, activityList, clickedActivity?.activityId)) {
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
                    if (!isTimeValid(startTime!!, selectedCalendar, activityList, clickedActivity?.activityId)) {
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
            Log.d("ActivityPlaceProcessor", "Place types: $placeTypes")

            val activityDetails = ActivityDetails(
                activityName = activityName,
                placeName = placeName,
                placeAddress = placeAddress,
                priorityLevel = priority,
                startTime = startTimeFormatted,
                endTime = endTimeFormatted,
                placeId = placeId,
                placeLatlng = placeLatlng,
                containerStatus = containerStatus,
                placeTypes = placeTypes

                )

            if (clickedActivity != null && position != null) {
                if (clickedActivity.status == "Finished") {
                    showToast("Editing a Finished activity")
                    // Editing a Finished activity. adding it back to the activity list
                    finishedActivitiesList.removeAt(position)
                    finishedActivitiesAdapter.notifyItemRemoved(position)
                    activityList.add(activityDetails)
                    activityAdapter.notifyItemInserted(activityList.size - 1)
                } else {
                    // Editing a a non Finished activity.
                    activityList[position] = activityDetails
                    activityAdapter.notifyItemChanged(position)
                    showToast("Editing")
                }

            }
            else {
                // Add a new activity
                activityList.add(activityDetails)
                activityAdapter.notifyItemInserted(activityList.size - 1)
                showToast("Adding")
            }

            // Update Firebase and dismiss the dialog
            selectedActivity = null
            selectedPriority = null
            Log.d("ActivityList", "Activity list is: $activityList")
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

    private fun fetchNearestPlace(bottomSheetDialog: BottomSheetDialog, userQuery: String, places: List<String>) {
        Log.d("ActiveActivitiesActivity", "Fetching nearest place for: '$places'")
        bottomSheetBinding.tvPlaceRecomLabel.text = getString(R.string.nearest_place_found)
        bottomSheetBinding.tvDescriptionRecommendation.text =
            getString(R.string.here_s_the_nearest_place_found_within_your_location)


        bottomSheetBinding.chipGroupFilters.visibility = View.GONE
        ActivityPlaceRecommendation(this).performTextSearch(placesClient, places, activityList, this) { placeItems ->
            if (placeItems.isNotEmpty()) {
                // Find the nearest place by converting distance to numeric
                val nearestPlace = placeItems.minByOrNull {
                    it.distance.toIntOrNull() ?: Int.MAX_VALUE
                }

                if (nearestPlace != null) {
                    Log.d("ActiveActivitiesActivity", "Nearest place: ${nearestPlace.name}")

                    // Update RecyclerView to show only the nearest place
                    setupRecyclerView(bottomSheetDialog, userQuery, listOf(nearestPlace))
                } else {
                    Log.e("ActiveActivitiesActivity", "No valid nearest place found.")
                    showToast("No nearby places found.")
                }
            } else {
                Log.e("ActiveActivitiesActivity", "No place recommendations available.")
                showToast("No place recommendations found.")
            }
        }
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

        bottomSheetBinding.actionButtons.visibility = if (clickedActivity.containerStatus == "Scheduled") View.GONE else View.VISIBLE
        bottomSheetBinding.btnEdit.text = if (clickedActivity.status == "Finished") "Reschedule" else "Edit"

        // Handle the button actions inside the bottom sheet
        bottomSheetBinding.btnEdit.setOnClickListener {
            showBottomSheet(clickedActivity, position)
            bottomSheetDialog.dismiss()
        }

        bottomSheetBinding.btnDelete.setOnClickListener {
            // Display a toast for feedback
            showToast("Deleting activity: ${clickedActivity.activityName}")

            val activityId = clickedActivity.activityId

            // Validate the Firebase user ID and activity ID
            val userId = FirebaseAuth.getInstance().currentUser?.uid
            if (userId != null && !activityId.isNullOrEmpty()) {
                val databaseReference = FirebaseDatabase.getInstance().reference
                    .child("Users")
                    .child(userId)
                    .child("MyActivities")
                    .child(containerId)
                    .child("activities")

                // Attempt to delete the activity from Firebase
                databaseReference.child(activityId).removeValue()
                    .addOnSuccessListener {
                        Log.d("Firebase", "Activity deleted successfully from Firebase")

                        // Remove activity from the appropriate list
                        if (clickedActivity.status == "Finished") {
                            // Remove from finished activities list
                            val index = finishedActivitiesList.indexOfFirst { it.activityId == activityId }
                            if (index != -1) {
                                finishedActivitiesList.removeAt(index)
                                finishedActivitiesAdapter.notifyItemRemoved(index)
                            }
                        } else {
                            // Remove from active activities list
                            val index = activityList.indexOfFirst { it.activityId == activityId }
                            if (index != -1) {
                                activityList.removeAt(index)
                                activityAdapter.notifyItemRemoved(index)
                            }
                        }

                        // Log the updated lists for debugging
                        Log.d("listAfterDeletion", "ActivityList: $activityList")
                        Log.d("listAfterDeletion", "FinishedActivityList: $finishedActivitiesList")

                        Toast.makeText(this@ActiveActivitiesActivity, "Activity deleted", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { exception ->
                        Log.e("Firebase", "Failed to delete activity: ${exception.message}")
                        Toast.makeText(this@ActiveActivitiesActivity, "Failed to delete activity", Toast.LENGTH_SHORT).show()
                    }
            } else {
                // Handle invalid user or activity ID scenario
                Toast.makeText(this@ActiveActivitiesActivity, "Error: Unable to delete activity", Toast.LENGTH_SHORT).show()
            }

            // Dismiss the bottom sheet
            bottomSheetDialog.dismiss()
        }





        bottomSheetBinding.btnClose.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        // Show the BottomSheetDialog
        bottomSheetDialog.show()
    }

    private fun fetchPlacesBasedOnActivity(bottomSheetDialog: BottomSheetDialog,userQuery: String, places: List<String>) {
        Log.d("ActiveActivitiesActivity", "Fetching places for: '$places'")
        ActivityPlaceRecommendation(this).performTextSearch(placesClient, places, activityList, this) { placeItems ->
            Log.d("ActiveActivitiesActivity", "Fetched ${placeItems.size} place recommendations")

            // Initialize the RecyclerView with default visibility
            setupRecyclerView(bottomSheetDialog, userQuery, placeItems)

            // Store the original list for filters
            val originalPlaceItems = placeItems

            // Set up filter logic
            setupChipListeners(originalPlaceItems)

        }
    }


    private fun setupRecyclerView(bottomSheetDialog: BottomSheetDialog,userQuery: String, placeItems: List<LocationBasedPlaceRecommendationItems>) {
        bottomSheetBinding.recyclerViewRecommendations.layoutManager = LinearLayoutManager(this)
        bottomSheetBinding.tvPlaceRecomLabel.visibility = View.VISIBLE
        bottomSheetBinding.recommendationPlaceLayout.visibility = View.VISIBLE
        bottomSheetBinding.btnReselect.visibility = View.VISIBLE
        bottomSheetBinding.btnReselect.setOnClickListener {
            bottomSheetDialog.dismiss()
            val intent = Intent(this@ActiveActivitiesActivity, SearchActivity::class.java)
            intent.putExtra("FROM_ACTIVE_ACTIVITIES", true)
            intent.putExtra("ACTIVITY", userQuery)
            Log.e("ActivityPlaceProcessor", "Activity: ${bottomSheetBinding.etActivity.text}")

            searchActivityLauncher.launch(intent)
        }

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
        bottomSheetBinding.btnReselect.visibility = View.GONE
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
        if (activityList.isEmpty() && finishedActivitiesList.isEmpty()) {
            // Show loading animation and text if both lists are empty
            binding.lottieAnimation.visibility = View.VISIBLE
            binding.loadingText.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
            binding.btnConfirm.visibility = View.GONE
            binding.recyclerViewEndActivities.visibility = View.GONE  // Assuming this RecyclerView exists
        } else {
            // Hide loading animation and text, show active RecyclerView
            binding.lottieAnimation.visibility = View.GONE
            binding.loadingText.visibility = View.GONE
            binding.recyclerView.visibility = if (activityList.isNotEmpty()) View.VISIBLE else View.GONE
            binding.btnConfirm.visibility = if (activityList.isNotEmpty()) View.VISIBLE else View.GONE
            // Handle finished activities visibility
            binding.recyclerViewEndActivities.visibility = if (finishedActivitiesList.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }


    private fun isTimeValid(
        startTime: Calendar,
        endTime: Calendar,
        existingActivities: List<ActivityDetails>,
        editedActivityId: String? // Pass null if adding a new activity
    ): Boolean {
        Log.d("TimeValidation", "Start time: ${startTime.time}, End time: ${endTime.time}")
        Log.d("TimeValidation", "Existing Activities: $existingActivities")

        if (existingActivities.isEmpty()) {
            Log.d("TimeValidation", "No existing activities. No conflicts.")
            return true // No conflicts if there are no other activities
        }

        for (activity in existingActivities) {
            Log.d("TimeValidation", "Checking activity: ${activity.activityName} (ID: ${activity.activityId})")

            // Skip the activity being edited
            if (editedActivityId != null && activity.activityId == editedActivityId) {
                Log.d("TimeValidation", "Skipping edited activity: ${activity.activityId}")
                continue
            }

            // Skip "Low" priority activities as they don't require time validation
            if (activity.priorityLevel == "Low") {
                Log.d("TimeValidation", "Skipping Low priority activity: ${activity.activityName}")
                continue
            }

            // Parse existing activity times
            val existingStart = activity.startTime?.let { parseToCalendar(it) }
            val existingEnd = activity.endTime?.let { parseToCalendar(it) }

            // Skip activities with invalid times
            if (existingStart == null || existingEnd == null) {
                Log.d("TimeValidation", "Skipping activity with invalid times: ${activity.activityName}")
                continue
            }

            Log.d("TimeValidation", "Existing activity start: ${existingStart.time}, end: ${existingEnd.time}")

            // Check for overlapping times
            val isOverlapping = startTime.timeInMillis < existingEnd.timeInMillis &&
                    endTime.timeInMillis > existingStart.timeInMillis
            if (isOverlapping) {
                Log.d("TimeValidation", "Overlap detected with activity: ${activity.activityName}")
                showSuggestionsDialog(
                    title = "Time Conflict",
                    message = """
                    The selected time overlaps with another activity, <b>${activity.activityName}</b>.
                    <ul>
                        <li><b>Scheduled Time:</b> ${formatActivityTime(existingStart, existingEnd)}</li>
                        <li><b>Your Selected Time:</b> ${formatActivityTime(startTime, endTime)}</li>
                    </ul>
                    Please choose a different time to avoid overlapping.
                """.trimIndent(),
                    isActionButtonVisible = false,
                    onDismiss = { Log.d("ConflictDialog", "User dismissed the dialog.") },
                    onAction = {
                        Log.d("ConflictDialog", "User will adjust activity timing.")
                    }
                )
                return false
            }

            // Ensure buffer time between activities
            val bufferTimeMillis = 15 * 60 * 1000 // 15 minutes in milliseconds
            val hasBuffer = (startTime.timeInMillis >= existingEnd.timeInMillis + bufferTimeMillis) ||
                    (endTime.timeInMillis <= existingStart.timeInMillis - bufferTimeMillis)
            if (!hasBuffer) {
                Log.d("TimeValidation", "Insufficient buffer time detected with activity: ${activity.activityName}")
                showSuggestionsDialog(
                    title = "Insufficient Buffer Time",
                    message = """
                    There is not enough buffer time between this activity and another, <b>${activity.activityName}</b>.
                    <ul>
                        <li><b>Conflict Activity:</b> ${activity.activityName}</li>
                        <li><b>Scheduled Time:</b> ${formatActivityTime(existingStart, existingEnd)}</li>
                        <li><b>Your Selected Time:</b> ${formatActivityTime(startTime, endTime)}</li>
                    </ul>
                    Please allow at least a 15-minute buffer between activities.
                """.trimIndent(),
                    isActionButtonVisible = false,
                    onDismiss = { Log.d("ConflictDialog", "User dismissed the dialog.") },
                    onAction = {
                        Log.d("ConflictDialog", "User will adjust activity timing.")
                    }
                )
                return false
            }
        }

        Log.d("TimeValidation", "No conflicts found.")
        return true // No conflicts found
    }




    private fun formatActivityTime(start: Calendar, end: Calendar): String {
        val dateFormatter = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
        val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())

        return if (dateFormatter.format(start.time) == dateFormatter.format(end.time)) {
            // Same day
            "${dateFormatter.format(start.time)} from ${timeFormatter.format(start.time)} to ${timeFormatter.format(end.time)}"
        } else {
            // Different days
            "${dateFormatter.format(start.time)} at ${timeFormatter.format(start.time)} to " +
                    "${dateFormatter.format(end.time)} at ${timeFormatter.format(end.time)}"
        }
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

        // Merge global finishedActivitiesList with the provided activityDetailsList
        val allActivitiesList = activityDetailsList + finishedActivitiesList

        // Clear existing activities
        databaseReference.removeValue().addOnCompleteListener { removeTask ->
            if (removeTask.isSuccessful) {
                // Proceed to save merged activities
                val batchUpdates = mutableListOf<Task<Void>>()

                for (activityDetails in allActivitiesList) {
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

        Log.d("FetchActivities", "Fetching activities for containerId: $containerId")

        databaseReference.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d("FetchActivities", "onDataChange triggered. Data exists: ${snapshot.exists()}")

                if (snapshot.exists()) {
                    val activeActivityList = mutableListOf<ActivityDetails>()
                    val finishedActivityList = mutableListOf<ActivityDetails>()

                    // Clear finished activities list before adding new ones
                    finishedActivitiesList.clear()

                    for (activitySnapshot in snapshot.children) {
                        val activity = activitySnapshot.getValue(ActivityDetails::class.java)
                        if (activity != null) {
                            activity.containerStatus = containerStatus

                            // Update status if activity is overdue
                            val currentTimeMillis = System.currentTimeMillis()
                            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                            try {
                                val activityStartTimeMillis = activity.startTime?.let { dateFormat.parse(it)?.time }
                                if (activityStartTimeMillis != null && activity.status == "Upcoming" && currentTimeMillis > activityStartTimeMillis) {
                                    activity.status = "Finished"
                                    activitySnapshot.ref.child("status").setValue("Finished")
                                        .addOnCompleteListener { task ->
                                            if (task.isSuccessful) {
                                                Log.d("FetchActivities", "Activity status updated to Finished: ${activitySnapshot.key}")
                                            } else {
                                                Log.e("FetchActivities", "Failed to update activity status: ${task.exception?.message}")
                                            }
                                        }
                                }
                            } catch (e: Exception) {
                                Log.e("FetchActivities", "Failed to parse start time: ${e.message}")
                            }

                            // Add all activities to the general list (allActivitiesList for reference, not to activityList)
                            activitySnapshot.ref.child("containerStatus").setValue(containerStatus)
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        Log.d("FetchActivities", "containerStatus updated successfully for activity: ${activitySnapshot.key}")
                                    } else {
                                        Log.e("FetchActivities", "Failed to update containerStatus: ${task.exception?.message}")
                                    }
                                }

                            // Add finished activities to finishedActivityList
                            if (activity.status == "Finished") {
                                finishedActivityList.add(activity)
                            }

                            // Add non-finished activities to activeActivityList
                            if ((containerStatus == "Scheduled" || containerStatus == "Unscheduled") && activity.status != "Finished") {
                                activeActivityList.add(activity)
                            }
                        } else {
                            Log.w("FetchActivities", "Null activity encountered in snapshot.")
                        }
                    }

                    // Update the active activities list (only non-finished ones) and notify the adapter
                    activityList.clear()
                    activityList.addAll(activeActivityList)
                    activityAdapter.notifyDataSetChanged()



                    // Prioritize active activities if containerStatus is "Scheduled" or "Unscheduled"
                    if (activeActivityList.isNotEmpty()) {
                        prioritizationOptimizer.prioritizeActivities(activeActivityList) { prioritizedList ->
                            // Update activityList with the prioritized activities
                            activityList.clear()
                            activityList.addAll(prioritizedList)
                            activityAdapter.notifyDataSetChanged()

                            // Clear and update latLngList and placeIdsList from the prioritized activities
                            latLngList.clear()
                            placeIdsList.clear()

                            for (activity in activityList) {
                                if (activity.placeLatlng.isNotEmpty()) {
                                    latLngList.add(activity.placeLatlng)
                                }
                                if (activity.placeId.isNotEmpty()) {
                                    placeIdsList.add(activity.placeId)
                                }
                            }

                            // Optionally update other lists or perform further processing
                            updateLatLngListFromActivities()
                            updatePlaceIdsListFromActivities()
                        }
                    }


                    // Set visibility for finished activities RecyclerView (after the active RecyclerView is updated)
                    if (finishedActivityList.isNotEmpty()) {
                        binding.recyclerViewEndActivities.visibility = View.VISIBLE
                        finishedActivitiesList.clear()
                        finishedActivitiesList.addAll(finishedActivityList)
                    } else {
                        binding.recyclerViewEndActivities.visibility = View.GONE
                    }

                    // Update finished activities RecyclerView only after both lists are updated
                    finishedActivitiesAdapter.notifyDataSetChanged()

                    // Check RecyclerView data
                    checkRecyclerViewData()
                } else {
                    Log.w("FetchActivities", "No activities found for the specified container.")
                    activityList.clear()
                    activityAdapter.notifyDataSetChanged()
                    checkRecyclerViewData()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FetchActivities", "Database error: ${error.message}")
                Toast.makeText(this@ActiveActivitiesActivity, "Failed to fetch activities: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateLatLngListFromActivities() {
        latLngList.clear()

        // Fetch current location first and then update the list
        fetchAndUseCurrentLocation { currentLatLng ->
            // After current location is added, add latLng from activities (only non-Finished ones)
            for (activity in activityList) {
                if (activity.status != "Finished") {  // Check if the activity is not finished
                    activity.placeLatlng.let { latLng ->
                        latLngList.add(latLng)
                    }
                }
            }
            if (currentLatLng != null) {
                // Add current location to the beginning of the list
                latLngList.add(0, currentLatLng)  // Add at index 0 to make it the first element
                Log.d("ActiveActivitiesActivity", "LATLNG LIST AT CURRENT LOCATION: $latLngList")
            }

            Log.e("ActiveActivitiesActivity", "LATLNG LIST: $latLngList")
        }
    }

    private fun updatePlaceIdsListFromActivities() {
        placeIdsList.clear()

        for (activity in activityList) {
            // Only add placeId if the activity is not finished
            if (activity.status != "Finished") {  // Check if the activity is not finished
                activity.placeId.let { placeId ->
                    placeIdsList.add(placeId)
                }
            }
        }
        Log.e("ActiveActivitiesActivity", "PLACE IDS BEFORE: $placeIdsList")
        Log.e("ActiveActivitiesActivity", "PLACE IDS AFTER: $placeIdsList")
    }


    private fun showTripSummaryBottomSheet() {
        // Initialize the View Binding
        val sheetBinding = BottomSheetActivityTripSummaryBinding.inflate(layoutInflater)

        // Initialize the BottomSheetDialog
        val bottomSheetDialog = BottomSheetDialog(this)
        bottomSheetDialog.setContentView(sheetBinding.root)

        // Fetch route and update UI when ready
        routeFetcher.fetchRoute {
            sheetBinding.tvTotalActivities.text = "Total Activities: ${activityAdapter.itemCount}"
            sheetBinding.tvTotalDistance.text = "Total Distance: ${routeFetcher.getTotalDistance()}"
            sheetBinding.tvTotalDuration.text = "Duration: ${routeFetcher.getTotalDuration()}"
            sheetBinding.tvTrafficCondition.text =
                "Traffic Condition: ${routeFetcher.determineOverallTrafficCondition()}"
            val routeToken = routeFetcher.getCustomRouteToken()
            // Handle Close button click
            sheetBinding.btnClose.setOnClickListener {
                bottomSheetDialog.dismiss()
            }

            // Handle Start Navigation button click
            sheetBinding.btnStartNavigation.setOnClickListener {
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

            sheetBinding.btnSimulate.setOnClickListener {
                for (activity in activityList) {
                    scheduleNotificationsForActivity(activityList, containerId)
                }
                binding.activeLabel.visibility = View.VISIBLE
                binding.finishedLabel.visibility = if (finishedActivitiesList.isNotEmpty()) View.VISIBLE else View.GONE
                binding.btnCancel.visibility = View.VISIBLE
                binding.addAndConfirmButtonRows.visibility = View.GONE
                showToast("Starting scheduled activities in background.")

                updateActivityStatus(containerId, "Scheduled")
                containerStatus = "Scheduled"
                activityAdapter.notifyDataSetChanged()
                fetchAndDisplayActivities(containerId)

                bottomSheetDialog.dismiss()
            }
            // Show the BottomSheetDialog
            bottomSheetDialog.show()
        }
    }

//    private fun scheduleNotificationsForActivity() {
//        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
//        var previousEndTimeInMillis: Long? = null // Keeps track of the previous activity's end time
//
//        activityList.forEach { activity ->
//            var startTimeInMillis: Long? = null
//            var endTimeInMillis: Long? = null
//
//            // If both start time and end time are provided
//            if (!activity.startTime.isNullOrEmpty() && !activity.endTime.isNullOrEmpty()) {
//                try {
//                    startTimeInMillis = sdf.parse(activity.startTime)?.time
//                    endTimeInMillis = sdf.parse(activity.endTime)?.time
//                } catch (e: Exception) {
//                    Log.e("NotificationScheduler", "Error parsing start or end time for activity: ${activity.activityName}")
//                }
//            } else {
//                // If both start time and end time are missing, calculate them sequentially based on the previous activity
//                startTimeInMillis = previousEndTimeInMillis ?: System.currentTimeMillis() // Start time based on previous end time or now
//                endTimeInMillis = startTimeInMillis + TimeUnit.HOURS.toMillis(1) // End time is 1 hour after start
//
//                Log.w("NotificationScheduler", "Start or end time missing for activity: ${activity.activityName}, setting based on previous activity.")
//            }
//
//            // Log the calculated times for the activity
//            Log.d("NotificationScheduler", "Calculated times for activity '${activity.activityName}' - Start: $startTimeInMillis, End: $endTimeInMillis")
//
//
//
//            // Now, schedule notifications for reminder, start, and end
//            startTimeInMillis?.let { startMillis ->
//                val reminderTime = startMillis - 10 * 60 * 1000 // 10 minutes before
//                if (reminderTime > System.currentTimeMillis()) {
//                    Log.d("NotificationScheduler", "Scheduling reminder for activity '${activity.activityName}' at $reminderTime")
//                    scheduleWork(containerId, activity.activityId, activity.activityName, "Reminder: ${activity.activityName} starts soon!", reminderTime, "Upcoming")
//                } else {
//                    Log.d("NotificationScheduler", "Reminder time for activity '${activity.activityName}' has already passed.")
//                }
//
//                // Schedule the "Start" notification
//                if (startMillis > System.currentTimeMillis()) {
//                    Log.d("NotificationScheduler", "Scheduling start notification for activity '${activity.activityName}' at $startMillis")
//                    scheduleWork(containerId, activity.activityId, activity.activityName, "Activity Started: ${activity.activityName}", startMillis, "In Progress")
//                } else {
//                    Log.d("NotificationScheduler", "Start time for activity '${activity.activityName}' has already passed.")
//                }
//            }
//
//            endTimeInMillis?.let { endMillis ->
//                // Schedule the "End" notification
//                if (endMillis > System.currentTimeMillis()) {
//                    Log.d("NotificationScheduler", "Scheduling end notification for activity '${activity.activityName}' at $endMillis")
//                    scheduleWork(containerId, activity.activityId, activity.activityName, "Activity Ended: ${activity.activityName}", endMillis, "Finished")
//                } else {
//                    Log.d("NotificationScheduler", "End time for activity '${activity.activityName}' has already passed.")
//                }
//            }
//
//            // Update the previous end time for the next activity
//            previousEndTimeInMillis = endTimeInMillis
//        }
//    }
private fun scheduleNotificationsForActivity(activityList: List<ActivityDetails>, containerId: String) {
    // Separate activities by priority
    val highAndMediumPriorityActivities = activityList.filter { it.priorityLevel == "High" || it.priorityLevel == "Medium" }
    val lowPriorityActivities = activityList.filter { it.priorityLevel == "Low" }

    // Generate available time slots based on high/medium-priority activities
    val availableTimeSlots = generateAvailableTimeSlotsForHighAndMedium(highAndMediumPriorityActivities)

    // Schedule high and medium-priority activities
    highAndMediumPriorityActivities.forEach { activity ->
        scheduleNotificationsForSpecificActivity(activity, containerId)
    }

    // Schedule low-priority activities without time constraints
    scheduleLowPriorityActivitiesWithoutConstraints(lowPriorityActivities, containerId)

    activityList.forEach { e->
        Log.e("Low Profiles", "activities: ${e.activityName}")
    }

   lowPriorityActivities.forEach { e->
       Log.e("Low Profiles", "Low profile activities: ${e.activityName}")
   }
}



    private fun generateAvailableTimeSlotsForHighAndMedium(existingActivities: List<ActivityDetails>): MutableList<Pair<Long, Long>> {
        val timeSlots = mutableListOf<Pair<Long, Long>>()
        var currentStart = System.currentTimeMillis()

        // Sort activities by start time
        val sortedActivities = existingActivities.sortedBy { it.startTime?.let { sdf.parse(it)?.time } }

        sortedActivities.forEach { activity ->
            val activityStart = activity.startTime?.let { sdf.parse(it)?.time }
            val activityEnd = activity.endTime?.let { sdf.parse(it)?.time }

            if (activityStart != null && activityEnd != null && activityStart > currentStart) {
                // Add the gap before this activity as a time slot
                timeSlots.add(Pair(currentStart, activityStart))
                currentStart = activityEnd // Move currentStart to the end of this activity
            }
        }

        return timeSlots
    }

    private fun scheduleLowPriorityActivitiesWithoutConstraints(
        lowPriorityActivities: List<ActivityDetails>,
        containerId: String
    ) {
        val DEFAULT_DURATION = TimeUnit.HOURS.toMillis(1) // Default duration: 1 hour
        val BUFFER_TIME = TimeUnit.MINUTES.toMillis(2) // Buffer time: 2 minutes
        val MIN_FUTURE_TIME = TimeUnit.MINUTES.toMillis(1) // Minimum time before starting an activity (5 minutes)
        var nextAvailableStartTime = System.currentTimeMillis() + MIN_FUTURE_TIME // Track the next start time


        lowPriorityActivities.forEach { activity ->
            // Skip if notifications have already been scheduled for this activity
            if (scheduledActivities.contains(activity.activityId)) {
                return@forEach
            }

            // Dynamically schedule activities without predefined constraints
            val startMillis = nextAvailableStartTime
            val endMillis = startMillis + DEFAULT_DURATION

            // Log scheduling details
            Log.d("Scheduler", "Low-priority activity '${activity.activityName}' scheduled from $startMillis to $endMillis")

            // Schedule notifications
            scheduleWork(containerId, activity.activityId, activity.activityName, "${activity.activityName} has started.", startMillis, "In Progress")
            scheduleWork(containerId, activity.activityId, activity.activityName, "${activity.activityName} has ended.", endMillis, "Finished")

            // Update nextAvailableStartTime to avoid overlapping with the current activity
            nextAvailableStartTime = endMillis + BUFFER_TIME

            // Mark this activity as scheduled
            activity.activityId?.let { scheduledActivities.add(it) }
        }
    }



    // Update a time slot after scheduling an activity
    private fun MutableList<Pair<Long, Long>>.updateSlot(start: Long, end: Long) {
        val updatedSlots = mutableListOf<Pair<Long, Long>>() // Temporary list for new slots

        this.forEach { slot ->
            when {
                // Case 1: The new activity fits entirely within this slot
                start >= slot.first && end <= slot.second -> {
                    if (start > slot.first) updatedSlots.add(Pair(slot.first, start)) // Add the first part
                    if (end < slot.second) updatedSlots.add(Pair(end, slot.second)) // Add the second part
                }
                // Case 2: No overlap, retain the original slot
                else -> updatedSlots.add(slot)
            }
        }

        // Replace the old list with the updated list
        clear()
        addAll(updatedSlots)
    }



    // Schedule notifications for a specific activity
    private fun scheduleNotificationsForSpecificActivity(activity: ActivityDetails, containerId: String) {
        // Check if notifications for this activity have already been scheduled
        if (scheduledActivities.contains(activity.activityId)) {
            return
        }

        val startMillis = activity.startTime?.let { sdf.parse(it)?.time }
        val endMillis = activity.endTime?.let { sdf.parse(it)?.time }

        startMillis?.let {
            val reminderTime = it - TimeUnit.MINUTES.toMillis(10) // Reminder 10 minutes before start
            if (reminderTime > System.currentTimeMillis()) {
                scheduleWork(containerId, activity.activityId, activity.activityName, "Activity ${activity.activityName} will start 10 minutes from now.", reminderTime, "Active")
                fetchAndDisplayActivities(containerId)
            }
            if (it > System.currentTimeMillis()) {
                scheduleWork(containerId, activity.activityId, activity.activityName, "Activity ${activity.activityName} has started.", startMillis, "In Progress")
                fetchAndDisplayActivities(containerId)
            }
        }

        endMillis?.let {
            if (it > System.currentTimeMillis()) {
                scheduleWork(containerId, activity.activityId, activity.activityName, "Activity ${activity.activityName} has ended.", endMillis, "Finished")
                fetchAndDisplayActivities(containerId)

            }
        }

        // Mark this activity as having its notifications scheduled
        activity.activityId?.let { scheduledActivities.add(it) }
    }


    private fun scheduleWork(containerId: String, activityId: String?, activityName: String, message: String, triggerAtMillis: Long, newStatus: String) {
        val delay = triggerAtMillis - System.currentTimeMillis() // Calculate delay in milliseconds
        Log.d("NotificationScheduler", "Scheduling work for activity '$activityName' with delay: $delay ms")

        // Prepare input data with additional fields
        val data = Data.Builder()
            .putString("activityName", activityName)
            .putString("notificationMessage", message)
            .putString("containerId", containerId)
            .putString("activityId", activityId)
            .putString("newStatus", newStatus)
            .build()

        // Create a one-time work request with delay
        val workRequest = OneTimeWorkRequestBuilder<ActivityNotificationWorker>()
            .setInputData(data)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()

        // Enqueue the work
        WorkManager.getInstance(this).enqueue(workRequest)
        Log.d("NotificationScheduler", "Work request for activity '$activityName' enqueued successfully.")
    }

    private fun updateActivityStatus(containerId: String, status: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            // Handle the case where the user is not logged in
            Log.e("UpdateContainerStatus", "User not logged in.")
            return
        }

        // Reference to the container's location in the Firebase Database
        val databaseReference = FirebaseDatabase.getInstance().reference
            .child("Users")
            .child(userId)
            .child("MyActivities")
            .child(containerId)

        // Update the status field of the container
        databaseReference.child("status").setValue(status)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("UpdateContainerStatus", "Status successfully updated to: $status")
                } else {
                    Log.e("UpdateContainerStatus", "Failed to update status: ${task.exception?.message}")
                }
            }
    }
}
