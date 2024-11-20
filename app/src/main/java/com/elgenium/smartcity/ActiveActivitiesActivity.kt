package com.elgenium.smartcity

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.elgenium.smartcity.contextuals.ActivityPlaceRecommendation
import com.elgenium.smartcity.databinding.ActivityActiveActivitiesBinding
import com.elgenium.smartcity.databinding.BottomSheetAddActivityBinding
import com.elgenium.smartcity.databinding.BottomSheetViewActivityDetailBinding
import com.elgenium.smartcity.databinding.DialogActivitySuggestionsBinding
import com.elgenium.smartcity.intelligence.ActivityPlaceProcessor
import com.elgenium.smartcity.intelligence.ActivityPrioritizationOptimizer
import com.elgenium.smartcity.models.ActivityDetails
import com.elgenium.smartcity.recyclerview_adapter.ActivityDetailsAdapter
import com.elgenium.smartcity.recyclerview_adapter.LocationBasedPlaceRecommendationAdapter
import com.elgenium.smartcity.singletons.NavigationBarColorCustomizerHelper
import com.elgenium.smartcity.singletons.PlacesNewClientSingleton
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
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
    private var containerId = ""


    private val searchActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val placeName = data?.getStringExtra("PLACE_NAME") ?: "Place Name"
            val placeAddress = data?.getStringExtra("PLACE_ADDRESS") ?: "Address of the place here"
            val activity = data?.getStringExtra("ACTIVITY") ?: "No activity"
            placeId = data?.getStringExtra("PLACE_ID") ?: ""

            Log.e("ActivityPlaceProcessor", "ACTIVITY: $activity")

            showBottomSheet()

            // Update the CardView details
            bottomSheetBinding.tvActivityName.text = activity
            bottomSheetBinding.activityPrompter.visibility = View.GONE
            bottomSheetBinding.tvPlaceLabel.text = placeName
            bottomSheetBinding.tvAddressLabel.text = placeAddress
            bottomSheetBinding.mainContainer.visibility = View.VISIBLE
            bottomSheetBinding.btnConfirm.visibility = View.VISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityActiveActivitiesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prioritizationOptimizer = ActivityPrioritizationOptimizer(this)



        containerId = intent.getStringExtra("containerId") ?: ""

        // Initialize RecyclerView and Adapter
        activityAdapter = ActivityDetailsAdapter(activityList) { clickedActivity, position ->
            showActivityDetailsBottomSheet(clickedActivity, position)
        }
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@ActiveActivitiesActivity)
            adapter = activityAdapter
        }

        fetchAndDisplayActivities(containerId)


        NavigationBarColorCustomizerHelper.setNavigationBarColor(this, R.color.primary_color)

        binding.fabAdd.setOnClickListener {
            showBottomSheet()
        }
    }






    private fun showBottomSheet(clickedActivity: ActivityDetails? = null, position: Int? = null) {
        bottomSheetBinding = BottomSheetAddActivityBinding.inflate(layoutInflater)

        // Create a BottomSheetDialog
        val bottomSheetDialog = BottomSheetDialog(this)
        bottomSheetDialog.setContentView(bottomSheetBinding.root)

        var selectedPriority: String? = clickedActivity?.priorityLevel
        // Handle startTime and endTime when they are empty or null
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) // Initialize SimpleDateFormat

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

        // Temporary copy of the activity to restore if canceled
        val backupActivity = clickedActivity?.copy()
        if (clickedActivity != null && position != null) {
            Log.d("INDEX", "position: $position")
            Log.d("INDEX", "activity list size: ${activityList.size}")

            activityList.removeAt(position)
            activityAdapter.notifyItemRemoved(position)
            bottomSheetBinding.mainContainer.visibility = View.VISIBLE
            bottomSheetBinding.btnConfirm.visibility = View.VISIBLE
            bottomSheetBinding.etActivity.setText(clickedActivity.activityName)
            bottomSheetBinding.tvPlaceLabel.text = clickedActivity.placeName
            bottomSheetBinding.tvActivityName.text = clickedActivity.activityName
            bottomSheetBinding.tvAddressLabel.text = clickedActivity.placeAddress
            when (clickedActivity.priorityLevel) {
                "High" -> togglePrioritySelection(bottomSheetBinding.btnHighPriority, bottomSheetBinding.btnMediumPriority, bottomSheetBinding.btnLowPriority)
                "Medium" -> togglePrioritySelection(bottomSheetBinding.btnMediumPriority, bottomSheetBinding.btnHighPriority, bottomSheetBinding.btnLowPriority)
                "Low" -> togglePrioritySelection(bottomSheetBinding.btnLowPriority, bottomSheetBinding.btnHighPriority, bottomSheetBinding.btnMediumPriority)
            }
            startTime?.let {
                bottomSheetBinding.btnStartTime.text = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(it.time)
            }
            endTime?.let {
                bottomSheetBinding.btnEndTime.text = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(it.time)
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
            bottomSheetBinding.timeContraints.visibility = if (bottomSheetBinding.btnHighPriority.isSelected) View.VISIBLE else View.GONE
        }

        bottomSheetBinding.btnMediumPriority.setOnClickListener {
            togglePrioritySelection(
                bottomSheetBinding.btnMediumPriority,
                bottomSheetBinding.btnHighPriority,
                bottomSheetBinding.btnLowPriority
            )

            selectedPriority = if (bottomSheetBinding.btnMediumPriority.isSelected) "Medium" else null
            bottomSheetBinding.timeContraints.visibility = if (bottomSheetBinding.btnMediumPriority.isSelected) View.VISIBLE else View.GONE
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
                    bottomSheetBinding.btnStartTime.text = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(startTime!!.time)
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
            val startTimeFormatted = startTime?.let { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(it.time) }
            val endTimeFormatted = endTime?.let { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(it.time) }

            if (activityName.isBlank() || placeName == "Place Name" || placeAddress == "Address of the place here") {
                showToast("Please fill in all required fields.")
                return@setOnClickListener
            }

            if (selectedPriority.isNullOrEmpty()){
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
                placeId =  placeId

            )
            activityList.add(activityDetails)
            activityAdapter.notifyItemInserted(activityList.size - 1)
            checkRecyclerViewData()

            val currentTime = System.currentTimeMillis()
            prioritizationOptimizer.prioritizeActivities(activityList, currentTime) { prioritizedList ->
                Log.d("ActiveActivitiesActivity", "Prioritized activities: $prioritizedList")

                // Update the activityList with the prioritized list
                activityList.clear()
                activityList.addAll(prioritizedList)

                // Notify the adapter about the changes
                activityAdapter.notifyDataSetChanged()
            }

            saveAllActivitiesToFirebase(activityList, containerId)

            bottomSheetDialog.dismiss()
        }

        // Cancel button logic
        bottomSheetBinding.btnCancel.setOnClickListener {
            if (backupActivity != null && position != null) {
                activityList.add(position, backupActivity)
                activityAdapter.notifyItemInserted(position)
            }
            bottomSheetDialog.dismiss()
        }

        binding.btnConfirm.setOnClickListener {
            Log.d("TAKER", "CONTAINER ID: $containerId" )

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
        bottomSheetBinding.tvPriority.text = clickedActivity.priorityLevel ?: "No priority level set"
        bottomSheetBinding.tvTimeRange.text = formatTimeRange(clickedActivity.startTime, clickedActivity.endTime)

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
                Toast.makeText(this@ActiveActivitiesActivity, "Activity deleted", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(this@ActiveActivitiesActivity, "Failed to delete activity", Toast.LENGTH_SHORT).show()
                    }
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




    private fun fetchPlacesBasedOnActivity(places: List<String>) {
        // Log the places being searched for
        Log.d("ActiveActivitiesActivity", "Fetching places for the following: '$places'")

        // Assume the ActivityPlaceRecommendation class is already set up
        ActivityPlaceRecommendation(this).performTextSearch(placesClient, places) { placeItems ->
            // Log the number of place items fetched
            Log.d("ActiveActivitiesActivity", "Fetched ${placeItems.size} place recommendations")

            // Update the RecyclerView with the fetched place items
            // Set the LayoutManager
            bottomSheetBinding.recyclerViewRecommendations.layoutManager = LinearLayoutManager(this)
            bottomSheetBinding.tvPlaceRecomLabel.visibility = View.VISIBLE


            // Set the Adapter
            placeAdapter = LocationBasedPlaceRecommendationAdapter(placeItems) { selectedPlace ->
                Log.d("ActiveActivitiesActivity", "Clicked on place: ${selectedPlace.name}, ${selectedPlace.address}")
                // Perform additional actions
                bottomSheetBinding.tvActivityName.text = bottomSheetBinding.etActivity.text
                bottomSheetBinding.tvPlaceLabel.text = selectedPlace.name
                bottomSheetBinding.tvAddressLabel.text = selectedPlace.address
                bottomSheetBinding.recyclerViewRecommendations.visibility = View.GONE
                bottomSheetBinding.mainContainer.visibility = View.VISIBLE
                bottomSheetBinding.btnConfirm.visibility = View.VISIBLE
                bottomSheetBinding.tvPlaceRecomLabel.visibility = View.GONE
                placeId = selectedPlace.placeId


            }
            bottomSheetBinding.recyclerViewRecommendations.adapter = placeAdapter


            Log.d("ActiveActivitiesActivity", "PLACE ITEMS SIZE: ${placeItems.size}")

            // Log the visibility state of the RecyclerView
            if (placeItems.isNotEmpty()) {
                Log.d("ActiveActivitiesActivity", "Setting RecyclerView visibility to VISIBLE")
                bottomSheetBinding.recyclerViewRecommendations.visibility = View.VISIBLE
                // Hide loading animation and empty text
                bottomSheetBinding.lottieAnimation.visibility = View.GONE
                bottomSheetBinding.emptyDataLabel.visibility = View.GONE
            } else {
                Log.d("ActiveActivitiesActivity", "Setting RecyclerView visibility to GONE")
                bottomSheetBinding.recyclerViewRecommendations.visibility = View.GONE
                bottomSheetBinding.lottieAnimation.setAnimation(R.raw.no_data)
                bottomSheetBinding.emptyDataLabel.text =
                    getString(R.string.no_places_found_please_try_again)
            }
        }
    }



    // Helper to toggle priority button selection
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
            button.setBackgroundColor(getColor(defaultColorMap[button.id] ?: R.color.secondary_color))
        }

        // Toggle the selected button
        selectedButton.isSelected = !selectedButton.isSelected
        val defaultColor = defaultColorMap[selectedButton.id] ?: R.color.secondary_color
        selectedButton.setBackgroundColor(
            if (selectedButton.isSelected) getColor(R.color.brand_color) else getColor(defaultColor)
        )
    }


    // Helper to show a date and time picker
    private fun showDateTimePicker(onDateTimeSelected: (Calendar) -> Unit) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, day ->
            TimePickerDialog(this, { _, hour, minute ->
                val selectedCalendar = Calendar.getInstance().apply {
                    set(year, month, day, hour, minute)
                }
                onDateTimeSelected(selectedCalendar)
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }


    // Utility function to show a toast
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

    // Helper function to format time range
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

    private fun isTimeValid(startTime: Calendar, endTime: Calendar, existingActivities: List<ActivityDetails>): Boolean {
        for (activity in existingActivities) {
            if (activity.priorityLevel in listOf("High", "Medium")) {
                val existingStart = activity.startTime?.let { parseToCalendar(it) }
                val existingEnd = activity.endTime?.let { parseToCalendar(it) }

                // Skip activities without proper time data
                if (existingStart == null || existingEnd == null) continue

                // Check for overlap
                val isOverlapping = startTime.timeInMillis < existingEnd.timeInMillis && endTime.timeInMillis > existingStart.timeInMillis
                if (isOverlapping) {
                    showConflictDialog(
                        title = "Time Conflict",
                        message = Html.fromHtml(
                            """
                            <p>The selected time for this activity overlaps with another activity: "<b>${activity.activityName}</b>".</p>
                            
                            <h4>Details:</h4>
                            <ul>
                                <li><b>Overlapping Activity:</b> ${activity.activityName}</li>
                                <li><b>Scheduled Time:</b> 
                                    ${existingStart.let { SimpleDateFormat("MMMM dd, yyyy 'at' HH:mm", Locale.getDefault()).format(it.time) }} to 
                                    ${existingEnd.let { SimpleDateFormat("MMMM dd, yyyy 'at' HH:mm", Locale.getDefault()).format(it.time) }}
                                </li>
                                <li><b>Your Selected Time:</b> 
                                    ${SimpleDateFormat("MMMM dd, yyyy 'at' HH:mm", Locale.getDefault()).format(startTime.time)} to 
                                    ${SimpleDateFormat("MMMM dd, yyyy 'at' HH:mm", Locale.getDefault()).format(endTime.time)}
                                </li>
                            </ul>
                            
                            <h4>Resolution:</h4>
                            <p>Choose a different start or end time for this activity to avoid overlapping with the existing one.</p>
                            """.trimIndent(),
                            Html.FROM_HTML_MODE_LEGACY
                        ).toString()
                    )

                    return false
                }

                // Check for buffer time (15 minutes)
                val bufferTimeMillis = 15 * 60 * 1000
                val hasBuffer =
                    (abs(existingEnd.timeInMillis - startTime.timeInMillis) >= bufferTimeMillis) &&
                            (abs(endTime.timeInMillis - existingStart.timeInMillis) >= bufferTimeMillis)
                if (!hasBuffer) {
                    showConflictDialog(
                        title = "Insufficient Buffer Time",
                        message = Html.fromHtml(
                            """
                            <p>There is not enough buffer time between this activity and another: "<b>${activity.activityName}</b>".</p>
                            
                            <h4>Details:</h4>
                            <ul>
                                <li><b>Conflict Activity:</b> ${activity.activityName}</li>
                                <li><b>Scheduled Time:</b> ${existingStart.let { SimpleDateFormat("MMMM dd, yyyy 'at' HH:mm", Locale.getDefault()).format(it.time) }} to ${existingEnd.let { SimpleDateFormat("MMMM dd, yyyy 'at' HH:mm", Locale.getDefault()).format(it.time) }}</li>
                                <li><b>Your Selected Time:</b> ${SimpleDateFormat("MMMM dd, yyyy 'at' HH:mm", Locale.getDefault()).format(startTime.time)} to ${SimpleDateFormat("MMMM dd, yyyy 'at' HH:mm", Locale.getDefault()).format(endTime.time)}</li>
                                <li><b>Minimum Buffer Required:</b> 15 minutes</li>
                            </ul>
                            
                            <h4>Resolution:</h4>
                            <p>Adjust the start or end time of your activity to allow at least 15 minutes between the two activities.</p>
                            """.trimIndent(),
                            Html.FROM_HTML_MODE_LEGACY
                        ).toString()
                    )

                    return false
                }
            }
        }
        return true
    }

    // Helper to parse activity start/end time strings into Calendar objects
    private fun parseToCalendar(dateString: String): Calendar {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return Calendar.getInstance().apply { time = format.parse(dateString)!! }
    }

    private fun showConflictDialog(title: String, message: String) {
        // Inflate the dialog layout using ViewBinding
        val binding = DialogActivitySuggestionsBinding.inflate(layoutInflater)

        // Create an AlertDialog
        val dialog = AlertDialog.Builder(this).setView(binding.root).create()

        // Set title and message
        binding.dialogTitle.text = title
        binding.dialogMessage.text = message

        // Handle "Dismiss" button
        binding.btnDismiss.setOnClickListener {
            dialog.dismiss()
        }

        // Hide the "Fix" button since no callback is provided
        binding.btnAction.visibility = View.GONE

        // Show the dialog
        dialog.show()
    }

    private fun saveAllActivitiesToFirebase(activityDetailsList: List<ActivityDetails>, containerId: String) {
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
                            Toast.makeText(this, "All activities saved successfully!", Toast.LENGTH_SHORT).show()

                            // Fetch updated list and update RecyclerView
                            fetchAndDisplayActivities(containerId)
                        } else {
                            Toast.makeText(this, "Failed to save some activities.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    // If no activities to save, just fetch the latest data
                    fetchAndDisplayActivities(containerId)
                }
            } else {
                Toast.makeText(this, "Failed to clear existing activities.", Toast.LENGTH_SHORT).show()
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
                        activityList.clear()
                        activityList.addAll(newActivityList)
                        activityAdapter.notifyDataSetChanged() // Notify changes to the adapter
                    } else {
                        checkRecyclerViewData()
                    }
                } else {
                    checkRecyclerViewData()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@ActiveActivitiesActivity, "Failed to fetch activities: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }







}
