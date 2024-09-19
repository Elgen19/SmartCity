package com.elgenium.smartcity.singletons

import com.elgenium.smartcity.network.RoutesService
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClientRoutes {
    private const val BASE_URL = "https://routes.googleapis.com/"

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val routesApi: RoutesService by lazy {
        retrofit.create(RoutesService::class.java)
    }
}