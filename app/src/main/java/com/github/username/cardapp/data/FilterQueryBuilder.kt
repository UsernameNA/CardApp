package com.github.username.cardapp.data

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.github.username.cardapp.ui.common.CardFilterState
import com.github.username.cardapp.ui.common.SortDir

object FilterQueryBuilder {

    fun build(filter: CardFilterState, collection: Boolean = false): SupportSQLiteQuery {
        val args = mutableListOf<Any>()
        val where = mutableListOf<String>()

        val select = if (collection) {
            "SELECT cards.*, collection.quantity, prices.marketPrice, prices.lowPrice FROM cards " +
                "INNER JOIN collection ON collection.cardName = cards.name " +
                "LEFT JOIN prices ON prices.cardName = cards.name"
        } else {
            "SELECT cards.*, prices.marketPrice, prices.lowPrice FROM cards " +
                "LEFT JOIN prices ON prices.cardName = cards.name"
        }

        // Text search
        if (filter.query.isNotBlank()) {
            val pattern = "%${filter.query}%"
            where += "(cards.name LIKE ? OR cards.cardType LIKE ? OR cards.subTypes LIKE ? OR cards.rulesText LIKE ?)"
            args += pattern; args += pattern; args += pattern; args += pattern
        }

        // Set filter (match any)
        if (filter.sets.isNotEmpty()) {
            val setClauses = filter.sets.map {
                args += "%,$it,%"
                "cards.setNames LIKE ?"
            }
            where += "(${setClauses.joinToString(" OR ")})"
        }

        // Type filter
        if (filter.types.isNotEmpty()) {
            val placeholders = filter.types.map { "?" }.joinToString(", ")
            where += "cards.cardType IN ($placeholders)"
            args.addAll(filter.types)
        }

        // Rarity filter
        if (filter.rarities.isNotEmpty()) {
            val placeholders = filter.rarities.map { "?" }.joinToString(", ")
            where += "cards.rarity IN ($placeholders)"
            args.addAll(filter.rarities)
        }

        // Element filter
        if (filter.elements.isNotEmpty()) {
            val wantNone = "None" in filter.elements
            val elementKeys = filter.elements - "None"
            val clauses = mutableListOf<String>()

            if (wantNone) {
                clauses += "(cards.airThreshold = 0 AND cards.earthThreshold = 0 AND cards.fireThreshold = 0 AND cards.waterThreshold = 0)"
            }

            for (element in elementKeys) {
                val col = when (element) {
                    "Fire" -> "cards.fireThreshold"
                    "Water" -> "cards.waterThreshold"
                    "Earth" -> "cards.earthThreshold"
                    "Air" -> "cards.airThreshold"
                    else -> continue
                }
                clauses += "$col > 0"
            }

            if (clauses.isNotEmpty()) {
                val joiner = if (filter.elementMatchAll && !wantNone) " AND " else " OR "
                where += "(${clauses.joinToString(joiner)})"
            }
        }

        // Build WHERE clause
        val whereClause = if (where.isNotEmpty()) " WHERE ${where.joinToString(" AND ")}" else ""

        // Build ORDER BY clause
        val orderParts = mutableListOf<String>()
        for (field in filter.sort.priority) {
            val dir = when (field) {
                "name" -> filter.sort.name
                "cost" -> filter.sort.cost
                "rarity" -> filter.sort.rarity
                "price" -> filter.sort.price
                else -> SortDir.Off
            }
            if (dir == SortDir.Off) continue
            val dirStr = if (dir == SortDir.Asc) "ASC" else "DESC"
            when (field) {
                "name" -> orderParts += "cards.name COLLATE NOCASE $dirStr"
                "cost" -> orderParts += "cards.cost $dirStr"
                "rarity" -> orderParts += "CASE cards.rarity WHEN 'Unique' THEN 4 WHEN 'Elite' THEN 3 WHEN 'Exceptional' THEN 2 WHEN 'Ordinary' THEN 1 ELSE 0 END $dirStr"
                "price" -> {
                    orderParts += "CASE WHEN prices.marketPrice IS NULL THEN 1 ELSE 0 END ASC"
                    orderParts += "prices.marketPrice $dirStr"
                }
            }
        }
        // Always add name as tiebreaker if not already sorting by name
        if (filter.sort.name == SortDir.Off) {
            orderParts += "cards.name COLLATE NOCASE ASC"
        }

        val orderClause = if (orderParts.isNotEmpty()) " ORDER BY ${orderParts.joinToString(", ")}" else ""

        return SimpleSQLiteQuery("$select$whereClause$orderClause", args.toTypedArray())
    }
}
