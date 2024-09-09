package com.elgenium.smartcity.network

import com.elgenium.smartcity.network_reponses.RoadsResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface RoadsApiService {
    @GET("v1/snapToRoads")
    fun getSnappedRoads(
        @Query("path") path: String,
        @Query("key") apiKey: String
    ): Call<RoadsResponse>
}
