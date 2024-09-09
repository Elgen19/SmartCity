package com.elgenium.smartcity.network

import com.elgenium.smartcity.network_reponses.TrafficResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface TomTomApiService {
    @GET("traffic/services/4/flowSegmentData/relative0/10/json")
    fun getTrafficData(
        @Query("key") apiKey: String,
        @Query("point") point: String
    ): Call<TrafficResponse>
}
