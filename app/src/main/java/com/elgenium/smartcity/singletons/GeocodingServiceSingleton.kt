package com.elgenium.smartcity.singletons

import com.elgenium.smartcity.network.GeocodingService
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object GeocodingServiceSingleton {
    private const val BASE_URL = "https://maps.googleapis.com/maps/api/"

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val geocodingService: GeocodingService by lazy {
        retrofit.create(GeocodingService::class.java)
    }
}