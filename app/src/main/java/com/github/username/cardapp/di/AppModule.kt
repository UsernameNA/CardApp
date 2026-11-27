package com.github.username.cardapp.di

import android.content.Context
import com.github.username.cardapp.data.AssetTextReader
import com.github.username.cardapp.data.BundledImageIndex
import com.github.username.cardapp.data.local.AppDatabase
import com.github.username.cardapp.data.local.CardDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun provideAssetTextReader(@ApplicationContext context: Context): AssetTextReader =
        AssetTextReader { fileName ->
            context.assets.open(fileName).bufferedReader().use { it.readText() }
        }

    @Provides
    @Singleton
    fun provideBundledImageIndex(@ApplicationContext context: Context): BundledImageIndex =
        BundledImageIndex {
            context.assets.list("images").orEmpty()
                .filter { it.endsWith(".webp") }
                .mapTo(HashSet()) { it.removeSuffix(".webp") }
        }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase = AppDatabase.create(context)

    @Provides
    fun provideCardDao(db: AppDatabase): CardDao = db.cardDao()
}
