package com.github.username.cardapp.data

/** Slugs of the card images bundled in the APK (file names without `.webp`). */
fun interface BundledImageIndex {
    fun slugs(): Set<String>
}
