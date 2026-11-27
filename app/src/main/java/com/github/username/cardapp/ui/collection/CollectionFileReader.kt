package com.github.username.cardapp.ui.collection

import android.net.Uri

/** Reads a user-picked collection file as text. */
fun interface CollectionFileReader {
    /**
     * @throws java.io.FileNotFoundException if the file is gone.
     * @throws ImportFileTooLargeException if the file is too big to import.
     * @throws java.io.IOException if it cannot be read.
     * @throws SecurityException if read access was revoked.
     */
    suspend fun readText(uri: Uri): String
}
