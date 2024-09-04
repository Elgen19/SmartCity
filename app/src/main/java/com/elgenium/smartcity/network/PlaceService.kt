package com.elgenium.smartcity.network

import com.elgenium.smartcity.models.PlacesResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface PlacesService {
    @GET("place/nearbysearch/json")
    fun getNearbyPlaces(
        @Query("location") location: String,
        @Query("radius") radius: Int,
        @Query("type") type: String,
        @Query("key") apiKey: String
    ): Call<PlacesResponse>
}

