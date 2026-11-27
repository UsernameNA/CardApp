package com.github.username.cardapp.data

/** A sortable card attribute; [key] is the value persisted in the saved sort priority. */
enum class SortField(val key: String) {
    Name("name"),
    Cost("cost"),
    Rarity("rarity"),
    Price("price");

    companion object {
        fun fromKey(key: String): SortField? = entries.firstOrNull { it.key == key }
    }
}
