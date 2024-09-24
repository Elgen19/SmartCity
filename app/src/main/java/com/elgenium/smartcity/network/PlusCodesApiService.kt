package com.elgenium.smartcity.network

import com.elgenium.smartcity.network_reponses.PlusCodeResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface PlusCodesApiService {
    @GET("api")
    fun getPlusCode(
        @Query("address") address: String,
        @Query("key") apiKey: String,
        @Query("language") language: String? = null
    ): Call<PlusCodeResponse>
}
