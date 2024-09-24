package com.elgenium.smartcity.singletons

import com.elgenium.smartcity.network.PlusCodesApiService
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object PlusCodesSingleton {
    private const val BASE_URL = "https://plus.codes/"

    val instance: PlusCodesApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit.create(PlusCodesApiService::class.java)
    }
}
