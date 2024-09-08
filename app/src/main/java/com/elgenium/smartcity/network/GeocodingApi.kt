package com.elgenium.smartcity.network

import com.elgenium.smartcity.models.GeocodingResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface GeocodingApi {
    @GET("search/2/reverseGeocode/json")
    fun getReverseGeocoding(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
        @Query("key") apiKey: String
    ): Call<GeocodingResponse>
}
