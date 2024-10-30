package com.elgenium.smartcity.network

import com.elgenium.smartcity.network_responses.OpenMeteoResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface RainCheckOpenMeteo {
    @GET("forecast")
    fun getWeatherData(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("hourly") hourly: String = "precipitation" // Adjust as needed
    ): Call<OpenMeteoResponse>
}
