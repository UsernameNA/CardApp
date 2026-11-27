package com.github.username.cardapp.di

import javax.inject.Qualifier

/** The app-wide [kotlinx.coroutines.CoroutineScope], for work that must outlive any screen. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
