package com.elgenium.smartcity.network_reponses

data class Status(
    val code: Int,
    val message: String,
    val details: List<Any>
)

enum class RouteMatrixElementCondition {
    ROUTE_MATRIX_ELEMENT_CONDITION_UNSPECIFIED,
    ROUTE_EXISTS,
    ROUTE_NOT_FOUND
}

data class RouteMatrixElement(
    val originIndex: Int,
    val destinationIndex: Int,
    val status: Status,
    val condition: RouteMatrixElementCondition,
    val distanceMeters: Int,
    val duration: String,
    val staticDuration: String
)


data class RoutesMatrixResponse(
    val routes: List<RouteMatrixElement>
)

