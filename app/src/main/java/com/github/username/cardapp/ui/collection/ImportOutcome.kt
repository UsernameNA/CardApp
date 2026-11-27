package com.github.username.cardapp.ui.collection

import com.github.username.cardapp.data.CollectionImportResult

sealed interface ImportOutcome {
    data class Imported(val result: CollectionImportResult) : ImportOutcome
    data class Failed(val message: String) : ImportOutcome
}
