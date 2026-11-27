package com.github.username.cardapp.ui.collection

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.username.cardapp.data.CardFilterState
import com.github.username.cardapp.data.CardRepository
import com.github.username.cardapp.data.SortPreferences
import com.github.username.cardapp.data.local.CollectionCardWithPrice
import com.github.username.cardapp.data.local.CollectionEntryEntity
import com.github.username.cardapp.ui.common.CardFilterController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CollectionViewModel @Inject constructor(
    private val repository: CardRepository,
    sortPreferences: SortPreferences,
    private val importer: CollectionImporter,
) : ViewModel() {

    val filter = CardFilterController(
        scope = viewModelScope,
        loadSavedSort = { sortPreferences.sortState.first() },
        saveSort = sortPreferences::save,
        sets = repository.sets,
        query = repository::filteredCollection,
    )

    val entries: StateFlow<List<CollectionCardWithPrice>> = filter.results

    val totalCardCount: StateFlow<Int> = repository.totalCollectionQuantity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _importOutcome = MutableStateFlow<ImportOutcome?>(null)
    val importOutcome: StateFlow<ImportOutcome?> = _importOutcome.asStateFlow()

    init {
        repository.ensureDataLoaded()
    }

    fun increment(cardName: String) {
        viewModelScope.launch { repository.incrementInCollection(cardName) }
    }

    fun decrement(cardName: String) {
        viewModelScope.launch { repository.removeOneFromCollection(cardName) }
    }

    fun importCollection(uri: Uri) {
        viewModelScope.launch { _importOutcome.value = importer.import(uri) }
    }

    fun dismissImportOutcome() {
        _importOutcome.value = null
    }

    /** The whole collection, whatever the current filter, in the import format. */
    suspend fun exportText(): String =
        CollectionExportFormat.format(
            repository.filteredCollection(CardFilterState()).first()
                .map { CollectionEntryEntity(cardName = it.card.name, quantity = it.quantity) },
        )
}
