package com.example.cardapp.data.remote

import com.example.cardapp.data.remote.model.DriveFileList
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface DriveApiService {

    @GET("drive/v3/files")
    suspend fun listFiles(
        @Query("q") query: String,
        @Query("fields") fields: String = "nextPageToken,files(id,name)",
        @Query("pageSize") pageSize: Int = 1000,
        @Query("key") apiKey: String,
        @Query("pageToken") pageToken: String? = null,
    ): DriveFileList

    @Streaming
    @GET("drive/v3/files/{fileId}")
    suspend fun downloadFile(
        @Path("fileId") fileId: String,
        @Query("alt") alt: String = "media",
        @Query("key") apiKey: String,
    ): ResponseBody

    companion object {
        fun create(): DriveApiService = Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DriveApiService::class.java)
    }
}
