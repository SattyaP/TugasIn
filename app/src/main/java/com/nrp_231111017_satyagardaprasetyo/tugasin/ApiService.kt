package com.nrp_231111017_satyagardaprasetyo.tugasin

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ApiService {
    @POST("get-tugas")
    suspend fun getTasks(@Body credentials: Map<String, String>): TasksResponse
}