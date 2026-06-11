package com.github.username.cardapp.data

/**
 * Reads a bundled asset file as text. Abstracts Android's AssetManager so that
 * repository logic can be unit-tested on the JVM.
 */
fun interface AssetTextReader {
    fun read(fileName: String): String
}
