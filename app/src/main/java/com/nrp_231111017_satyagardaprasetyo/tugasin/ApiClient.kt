package com.nrp_231111017_satyagardaprasetyo.tugasin

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(90, TimeUnit.SECONDS) // connection timeout
        .readTimeout(90, TimeUnit.SECONDS)    // read timeout
        .writeTimeout(90, TimeUnit.SECONDS)   // write timeout
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://dw8cqwgq-3000.asse.devtunnels.ms/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: ApiService = retrofit.create(ApiService::class.java)
}
