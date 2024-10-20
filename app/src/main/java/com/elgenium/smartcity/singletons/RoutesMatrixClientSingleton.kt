package com.elgenium.smartcity.singletons


import com.elgenium.smartcity.network.RoutesMatrixService
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RoutesMatrixClientSingleton {
    private const val BASE_URL = "https://routes.googleapis.com/"

    val instance: RoutesMatrixService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create()) // or MoshiConverterFactory.create()
            .build()

        retrofit.create(RoutesMatrixService::class.java)
    }
}
