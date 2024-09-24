package com.elgenium.smartcity.network

import com.elgenium.smartcity.network_reponses.GeocodingResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface GeocodingService {
    @GET("geocode/json")
    fun getGeocoding(
        @Query("address") address: String,
        @Query("key") apiKey: String
    ): Call<GeocodingResponse>
}
