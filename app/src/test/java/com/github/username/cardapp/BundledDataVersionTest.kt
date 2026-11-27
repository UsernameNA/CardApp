package com.github.username.cardapp

import com.github.username.cardapp.data.BundledDataVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.security.MessageDigest

class BundledDataVersionTest {

    private val base = BundledDataVersion.of("[cards]", "{prices}", setOf("a", "b"))

    @Test
    fun sameInputsGiveTheSameVersion() {
        assertEquals(base, BundledDataVersion.of("[cards]", "{prices}", linkedSetOf("b", "a")))
    }

    @Test
    fun changingAnyInputChangesTheVersion() {
        assertNotEquals(base, BundledDataVersion.of("[cards2]", "{prices}", setOf("a", "b")))
        assertNotEquals(base, BundledDataVersion.of("[cards]", "{prices2}", setOf("a", "b")))
        assertNotEquals(base, BundledDataVersion.of("[cards]", "{prices}", setOf("a", "b", "c")))
    }

    @Test
    fun changingTheImportLogicVersionChangesTheVersion() {
        val bumped = BundledDataVersion.IMPORT_LOGIC_VERSION + 1
        assertNotEquals(base, BundledDataVersion.of("[cards]", "{prices}", setOf("a", "b"), bumped))
    }

    @Test
    fun sameAssetsUnderThePreviousReleaseFingerprintAreReimported() {
        // v1.6.0 hashed only the assets and image slugs, so an upgrade that ships identical
        // assets must still differ from it, or the new import logic never runs.
        assertNotEquals(v160Fingerprint("[cards]", "{prices}", setOf("a", "b")), base)
    }

    @Test
    fun inputBoundariesAreNotAmbiguous() {
        assertNotEquals(
            BundledDataVersion.of("ab", "c", emptySet()),
            BundledDataVersion.of("a", "bc", emptySet()),
        )
    }

    private fun v160Fingerprint(cardsJson: String, pricesJson: String, imageSlugs: Set<String>): String {
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
