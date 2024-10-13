package com.elgenium.smartcity.network

import com.elgenium.smartcity.network_reponses.WeatherAPIResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherAPIService {
    @GET("current.json")
    fun getCurrentWeather(
        @Query("key") apiKey: String,
        @Query("q") location: String
    ): Call<WeatherAPIResponse>
}