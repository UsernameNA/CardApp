package com.github.username.cardapp.ui.collection

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import javax.inject.Inject

class ContentResolverCollectionFileReader @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : CollectionFileReader {

    override suspend fun readText(uri: Uri): String = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw FileNotFoundException("No content at $uri")
        val bytes = stream.use { it.readNBytes(MAX_BYTES + 1) }
        if (bytes.size > MAX_BYTES) throw ImportFileTooLargeException(MAX_BYTES)
        bytes.toString(Charsets.UTF_8)
    }

    private companion object {
        // A collection list is a few KB; anything this big was picked by mistake.
        const val MAX_BYTES = 5 * 1024 * 1024
    }
}
