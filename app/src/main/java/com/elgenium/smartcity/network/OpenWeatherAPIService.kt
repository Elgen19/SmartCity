package com.elgenium.smartcity.network

import com.elgenium.smartcity.network_reponses.WeatherResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenWeatherAPIService {
    @GET("data/2.5/weather")
    fun getCurrentWeatherData(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
        @Query("units") units: String = "metric", // For Celsius
        @Query("appid") apiKey: String
    ): Call<WeatherResponse>

    @GET("data/2.5/weather")
    fun getWeather(
        @Query("q") cityName: String,
        @Query("appid") apiKey: String
    ): Call<WeatherResponse>
}