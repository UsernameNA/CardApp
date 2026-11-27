package com.github.username.cardapp.ui.collection

import android.net.Uri
import android.util.Log
import com.github.username.cardapp.data.CardRepository
import com.github.username.cardapp.data.CollectionImportParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject

/** Reads a picked collection file and adds the cards it lists, reporting failures as messages. */
class CollectionImporter @Inject constructor(
    private val fileReader: CollectionFileReader,
    private val repository: CardRepository,
) {

    suspend fun import(uri: Uri): ImportOutcome =
        try {
            val text = fileReader.readText(uri)
            if (text.isBlank()) {
                ImportOutcome.Failed("The file is empty.")
            } else {
                val names = repository.getAllCardNames()
                val result = withContext(Dispatchers.Default) { CollectionImportParser.parse(text, names) }
                if (result.entries.isNotEmpty()) repository.addToCollection(result.entries)
                ImportOutcome.Imported(result)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Collection import failed", e)
            ImportOutcome.Failed(messageFor(e))
        }

    private fun messageFor(e: Exception): String = when (e) {
        is ImportFileTooLargeException -> "The file is too large to be a collection list."
        is FileNotFoundException -> "The file could not be found."
        is SecurityException -> "Permission to read the file was denied."
        is IOException -> "The file could not be read."
        else -> "The cards could not be added to your collection."
    }

    private companion object {
        const val TAG = "CollectionImporter"
    }
}
