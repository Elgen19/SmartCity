package com.elgenium.smartcity.contextuals

import android.animation.ValueAnimator
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import com.elgenium.smartcity.R
import com.elgenium.smartcity.databinding.BottomSheetPlaceOpennowBinding
import com.elgenium.smartcity.network.RainCheckOpenMeteo
import com.elgenium.smartcity.network_responses.OpenMeteoResponse
import com.google.android.material.bottomsheet.BottomSheetDialog
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.LocalDateTime

class RainLikelihoodCalculator(private val context: Context) {

    // Base URL for Open Meteo API
    private val apiUrl = "https://api.open-meteo.com/v1/"
    private val apiService: RainCheckOpenMeteo

    init {
        Log.d("RainLikelihoodCalculator", "Initializing RainLikelihoodCalculator with API URL: $apiUrl")

        val retrofit = Retrofit.Builder()
            .baseUrl(apiUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(RainCheckOpenMeteo::class.java)
        Log.d("RainLikelihoodCalculator", "Retrofit service created")
    }

    // Function to fetch weather data and calculate the likelihood of rain for the next hour
    fun fetchRainLikelihoodAndShowDialog(latitude: Double, longitude: Double) {
        Log.e("RainLikelihoodCalculator", "Fetching rain likelihood from API for latitude: $latitude, longitude: $longitude")

        // Update the API call to include parameters for latitude and longitude
        apiService.getWeatherData(latitude, longitude).enqueue(object : Callback<OpenMeteoResponse> {
            override fun onResponse(
                call: Call<OpenMeteoResponse>,
                response: Response<OpenMeteoResponse>
            ) {
                if (response.isSuccessful && response.body() != null) {
                    Log.d("RainLikelihoodCalculator", "Weather data fetched successfully")
                    val likelihood = calculateRainLikelihood(response.body()!!)

                    // Show the bottom sheet only if the likelihood is high
                    showBottomSheet(likelihood)
                } else {
                    Log.e("RainLikelihoodCalculator", "Failed to fetch weather data: ${response.errorBody()?.string()}")
                }
            }

            override fun onFailure(call: Call<OpenMeteoResponse>, t: Throwable) {
                Log.e("RainLikelihoodCalculator", "Error fetching weather data: ${t.message}")
            }
        })
    }


    // Calculate the likelihood of rain for the next hour
    private fun calculateRainLikelihood(apiResponse: OpenMeteoResponse): Double {
        val currentHour = LocalDateTime.now().hour
        Log.d("RainLikelihoodCalculator", "Current hour: $currentHour")

        val times = apiResponse.hourly.time
        val precipitation = apiResponse.hourly.precipitation

        Log.d("RainLikelihoodCalculator", "Times: $times")
        Log.d("RainLikelihoodCalculator", "Precipitation: $precipitation")

        return calculateLikelihoodForNextHour(times, precipitation, currentHour)
    }

    // Calculate the likelihood of rain for the next hour
    private fun calculateLikelihoodForNextHour(
        times: List<String>,
        precipitation: List<Double>,
        currentHour: Int
    ): Double {
        val nextHourIndex = (currentHour + 1) % 24
        val nextHourTime = times[nextHourIndex]

        Log.d("RainLikelihoodCalculator", "Next hour time: $nextHourTime")

        val nextHourPrecipitation = precipitation[nextHourIndex]
        Log.d("RainLikelihoodCalculator", "Next hour precipitation: $nextHourPrecipitation")

        return when {
            nextHourPrecipitation > 1.0 -> {
                Log.d("RainLikelihoodCalculator", "High likelihood of rain")
                1.0 // High likelihood
            }
            nextHourPrecipitation > 0.0 -> {
                Log.d("RainLikelihoodCalculator", "Medium likelihood of rain")
                0.5 // Medium likelihood
            }
            else -> {
                Log.d("RainLikelihoodCalculator", "Low likelihood of rain")
                0.0 // Low likelihood
            }
        }
    }

    // Show the bottom sheet (displays only for high likelihood)
    private fun showBottomSheet(likelihood: Double) {
        Log.d("RainLikelihoodCalculator", "Showing bottom sheet with likelihood: $likelihood")

        val bottomSheetDialog = BottomSheetDialog(context)

        // Use ViewBinding to inflate the layout
        val binding = BottomSheetPlaceOpennowBinding.inflate(LayoutInflater.from(context))

        // Hide the buttons
        binding.buttonCancel.visibility = View.GONE
        binding.buttonProceed.visibility = View.GONE
        binding.lottieAnimation.setAnimation(R.raw.rain)

        // Set title and body text based on likelihood
        binding.textViewTitle.text = "Rain Check"
        when {
            likelihood > 1.0 -> {
                binding.textViewBody.text = "There is a high likelihood of rain"
            }
            likelihood > 0.0 ->
                binding.textViewBody.text = "There is a medium likelihood of rain"
            else ->
                binding.textViewBody.text = "There is a low likelihood of rain"
        }


        // Optionally, you can set a progress bar value if needed
        val progressAnimator = ValueAnimator.ofInt(0, 100)
        progressAnimator.duration = 5000 // 5 seconds
        progressAnimator.addUpdateListener { animator ->
            binding.progressBar.progress = animator.animatedValue as Int
        }
        progressAnimator.start()

        bottomSheetDialog.setContentView(binding.root)
        bottomSheetDialog.show()

        // Automatically dismiss the bottom sheet after 5 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            if (bottomSheetDialog.isShowing) {
                bottomSheetDialog.dismiss()
                Log.d("RainLikelihoodCalculator", "Bottom sheet dismissed automatically after 5 seconds")
            }
        }, 5000) // 5-second delay
    }

}

