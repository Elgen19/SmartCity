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
        "X-Goog-FieldMask: routes.distanceMeters," +
                "routes.duration," +
                "routes.staticDuration," +
                "routes.description," +
                "routes.polyline.encodedPolyline," +
                "routes.legs.distanceMeters," +
                "routes.legs.duration," +
                "routes.legs.staticDuration," +
                "routes.legs.travelAdvisory," +
                "routes.legs.polyline," +
                "routes.travelAdvisory," +
                "routes.legs.steps.distanceMeters," +
                "routes.legs.steps.staticDuration," +
                "routes.legs.steps.polyline," +
                "routes.legs.steps.navigationInstruction," +
                "routes.legs.steps.startLocation," +
                "routes.legs.steps.endLocation"




    )
    fun getRoutes(@Body requestBody: RoutesRequest): Call<RoutesResponse>

}
