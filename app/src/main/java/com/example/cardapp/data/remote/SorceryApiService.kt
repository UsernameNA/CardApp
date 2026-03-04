package com.example.cardapp.data.remote

import com.example.cardapp.data.remote.model.ApiCard
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

interface SorceryApiService {
    @GET("api/cards")
    suspend fun getCards(): List<ApiCard>

    companion object {
        fun create(): SorceryApiService = Retrofit.Builder()
            .baseUrl("https://api.sorcerytcg.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SorceryApiService::class.java)
    }
}
