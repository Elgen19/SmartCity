package com.elgenium.smartcity.contextuals

import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import com.elgenium.smartcity.R
import com.elgenium.smartcity.databinding.BottomSheetPlaceOpennowBinding
import com.elgenium.smartcity.speech.TextToSpeechHelper
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchByTextRequest


class FuelStopsRecommendation(contextInActivity: Context) {
    private val context = contextInActivity
    private val textToSpeechHelper = TextToSpeechHelper()

    init {
        textToSpeechHelper.initializeTTS(context)
    }

     fun showPlaceDialog(onProceedClicked: () -> Unit): AlertDialog {
        val binding = BottomSheetPlaceOpennowBinding.inflate(LayoutInflater.from(context))
        val dialog = AlertDialog.Builder(context)
            .setView(binding.root)
            .setCancelable(true)
            .create()


        val progressAnimator = ValueAnimator.ofInt(0, 100)
        progressAnimator.duration = 5000 // 5 seconds
        progressAnimator.addUpdateListener { animator ->
            binding.progressBar.progress = animator.animatedValue as Int
        }
        progressAnimator.start()

         binding.lottieAnimation.setAnimation(R.raw.gas)
        binding.textViewTitle.text = "Fuel check!"
        binding.textViewBody.text = "Here are some fuel stations along your route to help you refuel."
        textToSpeechHelper.speakResponse("Would you like to make a fuel stop? Here are some fuel stations along your route that you can check out.")

        Log.d("FuelStopsRecommendation", "Displaying place dialog with binding.")

        binding.buttonCancel.setOnClickListener {
            Log.d("FuelStopsRecommendation", "Cancel button clicked.")
            dialog.dismiss()
        }

        binding.buttonProceed.setOnClickListener {
            Log.d("FuelStopsRecommendation", "Proceed button clicked.")
            textToSpeechHelper.speakResponse("Preparing to plot places with fuel stations.")
            dialog.dismiss()
            onProceedClicked()  // Execute the callback function
        }

        dialog.show()
        return dialog
    }


    fun performOptimizedTextSearch(
        placesClient: PlacesClient,
        allLatlngs: List<LatLng>,
        context: Context,
        callback: (List<Place>) -> Unit
    ) {
        val placeFields = listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.ADDRESS,
            Place.Field.TYPES,
            Place.Field.RATING,
            Place.Field.USER_RATINGS_TOTAL,
            Place.Field.LAT_LNG,
            Place.Field.PHOTO_METADATAS
        )

        val placesList = mutableListOf<Place>()
        val query = "gas station"

        // Sample every 10th point to reduce requests
        val sampledLatLngs = allLatlngs.filterIndexed { index, _ -> index % 5 == 0 }

        Log.d("FuelStopsRecommendation", "Starting optimized search for gas stations along the route...")

        sampledLatLngs.forEachIndexed { index, latLng ->
            Log.d("FuelStopsRecommendation", "Searching at route sample #$index: $latLng")

            // Set a larger location bias radius (e.g., 3 km)
            val locationBias = CircularBounds.newInstance(latLng, 3000.0)

            val searchByTextRequest = SearchByTextRequest.builder(query, placeFields)
                .setMaxResultCount(3)  // Request up to 3 results per sample
                .setLocationBias(locationBias)
                .setOpenNow(true)
                .setRankPreference(SearchByTextRequest.RankPreference.DISTANCE)
                .build()

            placesClient.searchByText(searchByTextRequest)
                .addOnSuccessListener { response ->
                    Log.d("FuelStopsRecommendation", "Search successful at sample #$index, found ${response.places.size} places")
                    placesList.addAll(response.places)
                }
                .addOnFailureListener { exception ->
                    Log.e("FuelStopsRecommendation", "Search failed at sample #$index: ${exception.message}")
                }
                .addOnCompleteListener {
                    if (index == sampledLatLngs.size - 1) {
                        Log.d("FuelStopsRecommendation", "Completed optimized search across all sampled points.")
                        callback(placesList)
                    }
                }
        }
    }



}