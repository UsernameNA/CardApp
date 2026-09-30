package com.github.username.cardapp.data

import java.security.MessageDigest

/**
 * Fingerprint of everything an import derives from: the catalogue, the price list, and
 * which card images are bundled (they decide each card's display image). An app update
 * that changes any of them changes the fingerprint and triggers a re-import.
 */
object BundledDataVersion {

    fun of(cardsJson: String, pricesJson: String, imageSlugs: Set<String>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(cardsJson.toByteArray())
        digest.update(0)
        digest.update(pricesJson.toByteArray())
        digest.update(0)
        for (slug in imageSlugs.sorted()) {
            digest.update(slug.toByteArray())
            digest.update(0)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
