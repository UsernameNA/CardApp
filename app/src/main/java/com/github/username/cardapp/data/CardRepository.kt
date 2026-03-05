package com.github.username.cardapp.data

import android.content.Context
import com.github.username.cardapp.data.local.AppDatabase
import com.github.username.cardapp.data.local.CardEntity
import com.github.username.cardapp.data.model.CardJson
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CardRepository(private val context: Context, db: AppDatabase) {

    private val dao = db.cardDao()

    val cards = dao.getUniqueCards()

    suspend fun needsCardSync(): Boolean = dao.getCardCount() == 0

    suspend fun syncCards() = withContext(Dispatchers.IO) {
        val json = context.assets.open("cards.json").bufferedReader().use { it.readText() }
        val type = object : TypeToken<List<CardJson>>() {}.type
        val cardList: List<CardJson> = Gson().fromJson(json, type)

        val entities = cardList.flatMap { card ->
            card.sets.flatMap { set ->
                set.variants.map { variant ->
                    val g = card.guardian
                    CardEntity(
                        slug = variant.slug,
                        name = card.name,
                        rarity = g?.rarity.orEmpty(),
                        cardType = g?.type.orEmpty(),
                        rulesText = g?.rulesText.orEmpty(),
                        cost = g?.cost ?: 0,
                        attack = g?.attack ?: 0,
                        defence = g?.defence ?: 0,
                        elements = card.elements.orEmpty(),
                        subTypes = card.subTypes.orEmpty(),
                        setName = set.name.orEmpty(),
                        finish = variant.finish.orEmpty(),
                        artist = variant.artist.orEmpty(),
                        flavorText = variant.flavorText.orEmpty(),
                        typeText = variant.typeText.orEmpty(),
                        airThreshold = g?.thresholds?.air ?: 0,
                        earthThreshold = g?.thresholds?.earth ?: 0,
                        fireThreshold = g?.thresholds?.fire ?: 0,
                        waterThreshold = g?.thresholds?.water ?: 0,
                    )
                }
            }
        }
        dao.insertAll(entities)
    }
}
