package com.elgenium.smartcity.contextuals

import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import com.elgenium.smartcity.R
import com.elgenium.smartcity.databinding.BottomSheetPlaceOpennowBinding
import com.elgenium.smartcity.singletons.PlacesNewClientSingleton
import com.elgenium.smartcity.speech.TextToSpeechHelper
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class PlaceOpeningHoursContextuals(private val activityContext: Context) {

    private val placesClient by lazy { PlacesNewClientSingleton.getPlacesClient(activityContext) }
    private val textToSpeechHelper = TextToSpeechHelper()
    private var isClose = true

    init {
        Log.d(javaClass.simpleName, "Initializing TextToSpeech")
        textToSpeechHelper.initializeTTS(activityContext)
    }

    private var currentPlaceName: String? = null
    private var currentOpeningHours: List<String>? = null

    private fun fetchPlaceDetailsFromAPI(placeId: String, callback: (Place?) -> Unit) {
        Log.d(javaClass.simpleName, "Fetching details for Place ID: $placeId")

        val fields = listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.ADDRESS,
            Place.Field.CURRENT_OPENING_HOURS,
            Place.Field.OPENING_HOURS,
            Place.Field.UTC_OFFSET
        )
        val request = FetchPlaceRequest.builder(placeId, fields).build()

        placesClient.fetchPlace(request)
            .addOnSuccessListener { response ->
                Log.d(javaClass.simpleName, "Successfully fetched place details: ${response.place}")
                callback(response.place)
            }
            .addOnFailureListener { exception ->
                Log.e(javaClass.simpleName, "Error fetching place details", exception)
                callback(null)
            }
    }

    fun isPlaceOpenNow(placeId: String, onComplete: (Boolean) -> Unit) {
        Log.d(javaClass.simpleName, "Checking if place is open now for Place ID: $placeId")

        fetchPlaceDetailsFromAPI(placeId) { place ->
            Log.d(javaClass.simpleName, "Fetched Place: $place")
            val openingHours = place?.currentOpeningHours
            Log.d(javaClass.simpleName, "Opening Hours: ${openingHours?.weekdayText}")

            currentPlaceName = place?.name
            currentOpeningHours = openingHours?.weekdayText

            // Now that we have data, call onComplete to proceed
            onComplete(currentOpeningHours != null)
        }
    }

    fun showClosedPlaceDialog(onCancel: () -> Unit, onProceed: () -> Unit) {
        Log.d(javaClass.simpleName, "Preparing to show closed place dialog")
        val dialogBinding = BottomSheetPlaceOpennowBinding.inflate(LayoutInflater.from(activityContext))
        val dialog = Dialog(activityContext)

        // Get the screen width
        val displayMetrics = activityContext.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels

        val layoutParams = dialog.window?.attributes
        layoutParams?.width = (screenWidth * 0.8).toInt()

// Apply the updated layout parameters to the dialog
        dialog.window?.attributes = layoutParams
        val currentHour = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila"))

        dialog.setContentView(dialogBinding.root)
        dialogBinding.textViewTitle.text = currentPlaceName ?: "Unknown Place"

        val progressAnimator = ValueAnimator.ofInt(0, 100)
        progressAnimator.duration = 5000
        progressAnimator.addUpdateListener { animator ->
            dialogBinding.progressBar.progress = animator.animatedValue as Int
        }
        progressAnimator.start()

        dialogBinding.lottieAnimation.setAnimation(R.raw.close)
        Log.d(javaClass.simpleName, "Progress animation started for closed dialog")

        if (currentOpeningHours == null) {
            Log.e(javaClass.simpleName, "No opening hours available for this place, proceeding without showing dialog")
            onProceed()
            return
        }

        currentOpeningHours?.let { openingHours ->
            val currentDayIndex = when (currentHour.get(Calendar.DAY_OF_WEEK)) {
                Calendar.SUNDAY -> 6
                Calendar.MONDAY -> 0
                Calendar.TUESDAY -> 1
                Calendar.WEDNESDAY -> 2
                Calendar.THURSDAY -> 3
                Calendar.FRIDAY -> 4
                Calendar.SATURDAY -> 5
                else -> -1 // Just a fallback, though it should never happen
            }


            val currentDate = currentHour.time // get the current date and time for logging
            val dayOfWeek = currentHour.get(Calendar.DAY_OF_WEEK)
            Log.d(javaClass.simpleName, "Today is: $currentDate")
            Log.d(javaClass.simpleName, "Day of week (Calendar.DAY_OF_WEEK): $dayOfWeek")
            val todayOpening = openingHours[currentDayIndex]
            Log.d(javaClass.simpleName, "Today's Opening Info: $todayOpening")


            if (todayOpening.contains("Open 24 hours", ignoreCase = true)) {
                Log.d(javaClass.simpleName, "Place is open 24 hours; proceeding without dialog")
                onProceed()
                return
            }

            if (todayOpening.contains("Closed", ignoreCase = true)) {
                val message = "$currentPlaceName is closed today."
                dialogBinding.textViewBody.text = message
                textToSpeechHelper.speakResponse(message)
                Log.d(javaClass.simpleName, "Place is closed today: $message")
            } else {
                try {
                    // Split the opening hours string and extract the time ranges
                    val timeRanges = todayOpening.split(": ")[1].split(",").map { it.trim() }
                    val timeFormat = SimpleDateFormat("h:mm a", Locale.US)
                    val currentCalendar = Calendar.getInstance()


                    var nextOpeningTime: Calendar? = null

                    for (range in timeRanges) {
                        // Step 1: Split the time range into start and end times
                        val hoursParts = range.split("–").map { it.replace(" ", " ").trim() }.toMutableList()

                        // Step 2: Handle invalid time formats
                        if (hoursParts.size != 2) {
                            val message = "Unable to determine the opening hours for $currentPlaceName."
                            dialogBinding.textViewBody.text = message
                            textToSpeechHelper.speakResponse(message)
                            Log.e(javaClass.simpleName, "Invalid time range format: $todayOpening")
                            return
                        }

                        // Step 3: Add AM/PM if missing
                        if (!hoursParts[0].contains("AM", ignoreCase = true) && !hoursParts[0].contains("PM", ignoreCase = true)) {
                            val period = if (hoursParts[0].contains("AM", ignoreCase = true)) "AM" else "PM"
                            hoursParts[0] += " $period"
                        }

                        if (!hoursParts[1].contains("AM", ignoreCase = true) && !hoursParts[1].contains("PM", ignoreCase = true)) {
                            val period = if (hoursParts[0].contains("AM", ignoreCase = true)) "AM" else "PM"
                            hoursParts[1] += " $period"
                        }

                        // Step 4: Parse times
                        val openingTime = timeFormat.parse(hoursParts[0])
                        val closingTime = timeFormat.parse(hoursParts[1])

                        val openingCalendar = Calendar.getInstance().apply {
                            if (openingTime != null) time = openingTime
                            set(Calendar.YEAR, currentCalendar.get(Calendar.YEAR))
                            set(Calendar.MONTH, currentCalendar.get(Calendar.MONTH))
                            set(Calendar.DAY_OF_MONTH, currentCalendar.get(Calendar.DAY_OF_MONTH))
                        }

                        val closingCalendar = Calendar.getInstance().apply {
                            if (closingTime != null) time = closingTime
                            set(Calendar.YEAR, currentCalendar.get(Calendar.YEAR))
                            set(Calendar.MONTH, currentCalendar.get(Calendar.MONTH))
                            set(Calendar.DAY_OF_MONTH, currentCalendar.get(Calendar.DAY_OF_MONTH))
                        }

                        // Debugging: Log the opening and closing times
                        val timeFormats = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        Log.d(javaClass.simpleName, "Opening Time: ${timeFormats.format(openingCalendar.time)}")
                        Log.d(javaClass.simpleName, "Closing Time: ${timeFormats.format(closingCalendar.time)}")
                        Log.d(javaClass.simpleName, "Current Time: ${timeFormats.format(currentCalendar.time)}")


                        // Handle the case where the time crosses midnight (open from 5:00 PM to 3:00 AM next day)
                        Log.d(javaClass.simpleName, "currentCalendar.after(openingCalendar): ${currentCalendar.after(openingCalendar)}")
                        Log.d(javaClass.simpleName, "currentCalendar.before(closingCalendar): ${currentCalendar.before(closingCalendar)}")

                        if (currentCalendar.after(openingCalendar) && currentCalendar.before(closingCalendar) ) {
                            val message = "$currentPlaceName is currently open."
                            dialogBinding.textViewBody.text = message
                            textToSpeechHelper.speakResponse(message)
                            Log.d(javaClass.simpleName, "Place is currently open. Proceeding with the operation.")
                            isClose = false
                            onProceed()
                            return
                        } else {
                            // The place is closed
                            Log.d(javaClass.simpleName, "Place is closed.")
                        }


                        // Check for the next opening time if the place is closed
                        if (currentCalendar.before(openingCalendar) || currentCalendar.after(closingCalendar)) {
                            // If it's before opening, set the next opening time
                            if (nextOpeningTime == null || openingCalendar.before(nextOpeningTime)) {
                                nextOpeningTime = openingCalendar
                                Log.d(javaClass.simpleName, "Next opening time is set to: ${timeFormat.format(openingCalendar.time)}")
                            }
                        }



                    }

                    // Step 8: Handle case where the place is closed
                    if (nextOpeningTime != null) {
                        val message = "$currentPlaceName is currently closed. It will open at ${timeFormat.format(nextOpeningTime.time)}."
                        dialogBinding.textViewBody.text = message
                        textToSpeechHelper.speakResponse(message)
                        Log.d(javaClass.simpleName, "Place is closed. Next opening time: ${nextOpeningTime.time}")

                    } else   {
                        // If no next opening time is found, the place is closed today
                        val message = "$currentPlaceName is closed today."
                        dialogBinding.textViewBody.text = message
                        textToSpeechHelper.speakResponse(message)
                        Log.d(javaClass.simpleName, "Place is closed today.")
                    }
                } catch (e: Exception) {
                    val error = "Unable to determine the opening hours for $currentPlaceName."
                    Log.e(javaClass.simpleName, "Error processing opening hours: $todayOpening. Error: ${e.message}")
                    dialogBinding.textViewBody.text = error
                    textToSpeechHelper.speakResponse(error)
                }


            }
        } ?: run {
            Log.e(javaClass.simpleName, "No opening hours available for this place")
        }

        var hasProceeded = false // Flag to track if onProceed() has been called
        var isCancelled = false  // Flag to check if dialog was cancelled

        dialogBinding.buttonCancel.setOnClickListener {
            Log.d(javaClass.simpleName, "Cancel button clicked")
            isCancelled = true // Set the cancellation flag
            onCancel()
            dialog.dismiss()
        }

        dialogBinding.buttonProceed.setOnClickListener {
            if (!hasProceeded && !isCancelled) { // Check if proceed action is already triggered or cancelled
                Log.d(javaClass.simpleName, "Proceed button clicked")
                onProceed()
                hasProceeded = true // Set flag to true to prevent further calls
                dialog.dismiss()
            }
            return@setOnClickListener
        }

        if (isClose) {
            Handler(Looper.getMainLooper()).postDelayed({
                if (!hasProceeded && !isCancelled) { // Ensure onProceed is only called if not triggered or cancelled
                    Log.d(javaClass.simpleName, "Auto-closing dialog after delay")
                    Log.d(javaClass.simpleName, "Place is closed, triggering proceed")
                    onProceed()
                    hasProceeded = true // Set flag to true after calling onProceed
                    dialog.dismiss()
                }
            }, 5000)

            dialog.show()
            Log.d(javaClass.simpleName, "Dialog shown")
        } else {
            Log.d(javaClass.simpleName, "Place is open; proceeding without showing dialog")
            Log.d(javaClass.simpleName, "Place is closed in else case: $isClose")

            onProceed()
        }


    }

}
