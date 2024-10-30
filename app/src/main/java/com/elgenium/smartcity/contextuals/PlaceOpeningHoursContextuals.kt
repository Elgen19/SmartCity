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

class PlaceOpeningHoursContextuals(private val activityContext: Context) {

    private val placesClient by lazy { PlacesNewClientSingleton.getPlacesClient(activityContext) }
    private val textToSpeechHelper = TextToSpeechHelper()
    private var isClose = false

    init {
        textToSpeechHelper.initializeTTS(activityContext)
    }

    // Properties to hold place name and opening hours
    private var currentPlaceName: String? = null
    private var currentOpeningHours: List<String>? = null

    private fun fetchPlaceDetailsFromAPI(placeId: String, callback: (Place?) -> Unit) {
        Log.d(javaClass.simpleName, "Fetching details for Place ID: $placeId")

        val fields = listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.ADDRESS,
            Place.Field.CURRENT_OPENING_HOURS
        )
        val request = FetchPlaceRequest.builder(placeId, fields).build()

        placesClient.fetchPlace(request)
            .addOnSuccessListener { response ->
                Log.d(javaClass.simpleName, "Successfully fetched place details: ${response.place}")
                callback(response.place)
            }
            .addOnFailureListener { exception ->
                Log.e(javaClass.simpleName, "Error fetching place details", exception)
                callback(null) // Return null if the API call fails
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
        val dialogBinding = BottomSheetPlaceOpennowBinding.inflate(LayoutInflater.from(activityContext))
        val dialog = Dialog(activityContext)
        val currentHour = Calendar.getInstance()

        dialog.setContentView(dialogBinding.root)
        dialogBinding.textViewTitle.text = currentPlaceName ?: "Unknown Place"

        val progressAnimator = ValueAnimator.ofInt(0, 100)
        progressAnimator.duration = 5000 // 5 seconds
        progressAnimator.addUpdateListener { animator ->
            dialogBinding.progressBar.progress = animator.animatedValue as Int
        }
        progressAnimator.start()

        dialogBinding.lottieAnimation.setAnimation(R.raw.close)



        currentOpeningHours?.let { openingHours ->
            val currentDayIndex = currentHour.get(Calendar.DAY_OF_WEEK) - 1
            val todayOpening = openingHours[currentDayIndex]

            if (todayOpening.contains("Open 24 hours", ignoreCase = true)) {
                onProceed()
                return // Exit the function early since the place is always open
            }

            if (todayOpening.contains("Closed", ignoreCase = true)) {
                val message = "${currentPlaceName} is closed today."
                dialogBinding.textViewBody.text = message
                textToSpeechHelper.speakResponse(message)
            } else {
                try {
                    // Split and sanitize the time range
                    val timePart = todayOpening.split(": ")[1]
                    val hoursParts = timePart.split("–").map { it.replace(" ", " ").trim() }

                    if (hoursParts.size != 2) {
                        val message = "Unable to determine the opening hours for ${currentPlaceName}."
                        dialogBinding.textViewBody.text = message
                        textToSpeechHelper.speakResponse(message)
                        return
                    }

                    val timeFormat = SimpleDateFormat("h:mm a", Locale.US)
                    val openingTime = timeFormat.parse(hoursParts[0])
                    val closingTime = timeFormat.parse(hoursParts[1])

                    val openingCalendar = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, openingTime.hours)
                        set(Calendar.MINUTE, openingTime.minutes)
                    }
                    val closingCalendar = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, closingTime.hours)
                        set(Calendar.MINUTE, closingTime.minutes)
                    }

                    val minutesUntilClose = ((closingCalendar.timeInMillis - currentHour.timeInMillis) / 60000).toInt()

                    val message: String = when {
                        currentHour.before(openingCalendar) -> {
                            isClose = true
                            "$currentPlaceName is currently closed. It will open at ${timeFormat.format(openingCalendar.time)}."
                        }
                        currentHour.after(closingCalendar) -> {
                            isClose = true
                            "$currentPlaceName is already closed. It will open at ${timeFormat.format(openingCalendar.time)}."
                        }
                        minutesUntilClose <= 15 -> {
                            isClose = true
                            "$currentPlaceName is closing soon! You only have $minutesUntilClose minutes left until it closes."
                        }
                        else -> {
                            isClose = false
                            ""
                        }
                    }
                    dialogBinding.textViewBody.text = message
                    textToSpeechHelper.speakResponse(message)



                } catch (e: Exception) {
                    val error = "Unable to determine the opening hours for ${currentPlaceName}."
                    Log.e(javaClass.simpleName, "Error processing opening hours: $todayOpening. Error: ${e.message}")

                    dialogBinding.textViewBody.text = error
                    textToSpeechHelper.speakResponse(error)
                }
            }
        } ?: run {
            Log.e(javaClass.simpleName, "Error processing opening hours: NO OPENING HOURS AVAILABLE FOR THIS PLACE}")

        }

        dialogBinding.buttonCancel.setOnClickListener {
            onCancel()
        }

        dialogBinding.buttonProceed.setOnClickListener {
           onProceed()
        }

        if (isClose) {
            Handler(Looper.getMainLooper()).postDelayed({
                // Execute the onProceed action
                onProceed()

                dialog.dismiss()
            }, 5000) // Simulate loading for 2 seconds (replace with your actual loading operation)

            dialog.show()

        } else {
            onProceed()
        }
    }
}
