package com.example.cardapp.data

import android.content.Context
import com.example.cardapp.data.local.AppDatabase
import com.example.cardapp.data.local.CardEntity
import com.example.cardapp.data.remote.model.ApiCard
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class CardRepository(private val context: Context, private val db: AppDatabase) {

    private val dao = db.cardDao()

    val cards = dao.getUniqueCards()

    suspend fun needsCardSync(): Boolean = dao.getCardCount() == 0

    suspend fun syncCards() {
        val json = context.assets.open("cards.json").bufferedReader().use { it.readText() }
        val type = object : TypeToken<List<ApiCard>>() {}.type
        val apiCards: List<ApiCard> = Gson().fromJson(json, type)

        val entities = apiCards.flatMap { card ->
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
