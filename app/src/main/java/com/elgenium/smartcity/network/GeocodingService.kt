package com.elgenium.smartcity.network

import com.elgenium.smartcity.network_reponses.GeocodingResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface GeocodingService {
    @GET("maps/api/geocode/json")
    fun getCityName(
        @Query("latlng") latLng: String,
        @Query("key") apiKey: String
    ): Call<GeocodingResponse>
}

