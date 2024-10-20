package com.elgenium.smartcity.network

import com.elgenium.smartcity.network_reponses.RouteMatrixElement
import com.elgenium.smartcity.routes_network_request.RouteMatrixRequest
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface RoutesMatrixService {
    @POST("distanceMatrix/v2:computeRouteMatrix")
    suspend fun computeRouteMatrix(
        @Header("X-Goog-Api-Key") apiKey: String,
        @Header("X-Goog-FieldMask") fieldMask: String = "originIndex,destinationIndex,duration,distanceMeters,status,condition",
        @Body request: RouteMatrixRequest
    ): List<RouteMatrixElement> // Now this returns a list of RouteMatrixElement directly
}


