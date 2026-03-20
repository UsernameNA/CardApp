package com.github.username.cardapp.di

import android.content.Context
import androidx.room.Room
import com.github.username.cardapp.data.local.AppDatabase
import com.github.username.cardapp.data.local.CardDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "cardapp.db")
            .fallbackToDestructiveMigration(true)
            .build()

    @Provides
    @Singleton
    fun provideCardDao(db: AppDatabase): CardDao = db.cardDao()
}
