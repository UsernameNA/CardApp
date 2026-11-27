package com.github.username.cardapp.di

import com.github.username.cardapp.ui.collection.CollectionFileReader
import com.github.username.cardapp.ui.collection.ContentResolverCollectionFileReader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Module
@InstallIn(ViewModelComponent::class)
abstract class CollectionFileModule {

    @Binds
    abstract fun bindCollectionFileReader(impl: ContentResolverCollectionFileReader): CollectionFileReader
}
