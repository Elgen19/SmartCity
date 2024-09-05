package com.elgenium.smartcity.network

import com.elgenium.smartcity.models.PlaceDistanceResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface PlaceDistance {
    @GET("maps/api/directions/json")
    fun getDirections(
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("key") apiKey: String
    ): Call<PlaceDistanceResponse>
}