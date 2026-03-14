package com.github.username.cardapp.data

import android.content.Context
import com.github.username.cardapp.data.local.AppDatabase
import com.github.username.cardapp.data.local.CardEntity
import com.github.username.cardapp.data.local.CardVariantEntity
import com.github.username.cardapp.data.local.CollectionCardRow
import com.github.username.cardapp.data.local.CollectionEntryEntity
import com.github.username.cardapp.data.local.SetEntity
import com.github.username.cardapp.data.model.CardJson
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CardRepository(private val context: Context, db: AppDatabase) {

    private val dao = db.cardDao()

    val cards = dao.getAllCards()
    val collection: kotlinx.coroutines.flow.Flow<List<CollectionCardRow>> = dao.getCollectionEntries()

    suspend fun needsCardSync(): Boolean = dao.getCardCount() == 0

    suspend fun addToCollection(entries: List<CollectionEntryEntity>) {
        dao.upsertCollectionEntries(entries)
    }

    suspend fun incrementInCollection(cardName: String) {
        dao.incrementCollectionEntry(cardName)
    }

    suspend fun removeOneFromCollection(cardName: String) {
        dao.removeOneFromCollection(cardName)
    }

    suspend fun syncCards() = withContext(Dispatchers.IO) {
        val json = context.assets.open("cards.json").bufferedReader().use { it.readText() }
        val type = object : TypeToken<List<CardJson>>() {}.type
        val cardList: List<CardJson> = Gson().fromJson(json, type)

        // Collect unique sets → sort by releasedAt → assign 1-based releaseOrder
        val setMap = linkedMapOf<String, String>() // name → releasedAt
        for (card in cardList) {
            for (set in card.sets) {
                val name = set.name ?: continue
                if (name !in setMap) setMap[name] = set.releasedAt.orEmpty()
            }
        }
        val setEntities = setMap.entries
            .sortedBy { it.value }
            .mapIndexed { index, (name, releasedAt) ->
                SetEntity(name = name, releasedAt = releasedAt, releaseOrder = index + 1)
            }
        val setOrder = setEntities.associate { it.name to it.releaseOrder }

        // One CardEntity per unique card name.
        // primarySlug = the standard-finish variant from the card's earliest set.
        val cardEntities = cardList.map { card ->
            val g = card.guardian
            val primarySlug = card.sets
                .sortedBy { setOrder[it.name] ?: Int.MAX_VALUE }
                .firstNotNullOfOrNull { set ->
                    set.variants.firstOrNull { it.finish.equals("Standard", ignoreCase = true) }?.slug
                        ?: set.variants.firstOrNull()?.slug
                }
                .orEmpty()

            CardEntity(
                name = card.name,
                primarySlug = primarySlug,
                elements = card.elements.orEmpty(),
                subTypes = card.subTypes.orEmpty(),
                cardType = g?.type.orEmpty(),
                rarity = g?.rarity.orEmpty(),
                cost = g?.cost ?: 0,
                attack = g?.attack ?: 0,
                defence = g?.defence ?: 0,
                life = g?.life,
                rulesText = g?.rulesText.orEmpty(),
                airThreshold = g?.thresholds?.air ?: 0,
                earthThreshold = g?.thresholds?.earth ?: 0,
                fireThreshold = g?.thresholds?.fire ?: 0,
                waterThreshold = g?.thresholds?.water ?: 0,
            )
        }

        // One CardVariantEntity per physical print
        val variantEntities = cardList.flatMap { card ->
            card.sets.flatMap { set ->
                set.variants.map { variant ->
                    CardVariantEntity(
                        slug = variant.slug,
                        cardName = card.name,
                        setName = set.name.orEmpty(),
                        finish = variant.finish.orEmpty(),
                        product = variant.product.orEmpty(),
                        artist = variant.artist.orEmpty(),
                        flavorText = variant.flavorText.orEmpty(),
                        typeText = variant.typeText.orEmpty(),
                    )
                }
            }
        }

        dao.insertAllSets(setEntities)
        dao.insertAllCards(cardEntities)
        dao.insertAllVariants(variantEntities)
    }
}
