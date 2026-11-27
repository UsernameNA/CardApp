package com.github.username.cardapp.data

import java.security.MessageDigest

/**
 * Fingerprint of everything an import derives from: the import logic, the catalogue, the
 * price list, and which card images are bundled (they decide each card's display image).
 * An app update that changes any of them changes the fingerprint and triggers a re-import.
 */
object BundledDataVersion {

    /**
     * Bump when CatalogueBuilder, PriceSelector, TextFold or any other import logic changes
     * what gets written for the same assets — otherwise upgraded installs keep rows built
     * by the old logic.
     */
    const val IMPORT_LOGIC_VERSION = 2

    fun of(
        cardsJson: String,
        pricesJson: String,
        imageSlugs: Set<String>,
        importLogicVersion: Int = IMPORT_LOGIC_VERSION,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("logic:$importLogicVersion".toByteArray())
        digest.update(0)
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
