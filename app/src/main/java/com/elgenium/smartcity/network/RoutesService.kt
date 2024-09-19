package com.elgenium.smartcity.network


import com.elgenium.smartcity.BuildConfig
import com.elgenium.smartcity.network_reponses.RoutesResponse
import com.elgenium.smartcity.routes_network_request.RoutesRequest
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface RoutesService {
    @POST("directions/v2:computeRoutes")
    @Headers(
        "Content-Type: application/json",
        "X-Goog-Api-Key: ${BuildConfig.MAPS_API_KEY}",
        "X-Goog-FieldMask: routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline"
    )
    fun getRoutes(@Body requestBody: RoutesRequest): Call<RoutesResponse>
}
