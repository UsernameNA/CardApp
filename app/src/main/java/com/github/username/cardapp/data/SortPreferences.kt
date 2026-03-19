package com.github.username.cardapp.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.github.username.cardapp.ui.common.SortDir
import com.github.username.cardapp.ui.common.SortState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "user_prefs")

private val KEY_SORT_NAME = stringPreferencesKey("sort_name")
private val KEY_SORT_COST = stringPreferencesKey("sort_cost")
private val KEY_SORT_RARITY = stringPreferencesKey("sort_rarity")
private val KEY_SORT_PRICE = stringPreferencesKey("sort_price")
private val KEY_SORT_PRIORITY = stringPreferencesKey("sort_priority")

@Singleton
class SortPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    val sortState: Flow<SortState> = context.dataStore.data.map { prefs ->
        SortState(
            name = prefs[KEY_SORT_NAME].toSortDir(default = SortDir.Asc),
            cost = prefs[KEY_SORT_COST].toSortDir(),
            rarity = prefs[KEY_SORT_RARITY].toSortDir(),
            price = prefs[KEY_SORT_PRICE].toSortDir(),
            priority = prefs[KEY_SORT_PRIORITY]
                ?.split(",")
                ?.filter { it.isNotEmpty() }
                ?: listOf("name"),
        )
    }

    suspend fun save(sort: SortState) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SORT_NAME] = sort.name.name
            prefs[KEY_SORT_COST] = sort.cost.name
            prefs[KEY_SORT_RARITY] = sort.rarity.name
            prefs[KEY_SORT_PRICE] = sort.price.name
            prefs[KEY_SORT_PRIORITY] = sort.priority.joinToString(",")
        }
    }
}

private fun String?.toSortDir(default: SortDir = SortDir.Off): SortDir =
    when (this) {
        "Asc" -> SortDir.Asc
        "Desc" -> SortDir.Desc
        "Off" -> SortDir.Off
        else -> default
    }
