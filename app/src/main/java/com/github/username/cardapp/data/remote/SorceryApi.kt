package com.github.username.cardapp.data.remote

import com.github.username.cardapp.data.model.CardJson
import retrofit2.http.GET

interface SorceryApi {
    @GET("api/cards")
    suspend fun getCards(): List<CardJson>
}
