package com.elgenium.smartcity.network_reponses

data class WeatherAPIResponse(
    val location: WeatherLocation,
    val current: CurrentWeather
)

data class WeatherLocation(
    val name: String,
    val region: String,
    val country: String,
    val lat: Double,
    val lon: Double
)

data class CurrentWeather(
    val temp_c: Double,
    val condition: WeatherCondition
)

data class WeatherCondition(
    val text: String,
    val icon: String,
    val code: Int
)
