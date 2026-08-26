package com.wasl.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wasl.app.data.AccountOverview
import com.wasl.app.data.AdvancedSearchResult
import com.wasl.app.data.AdvancedSearchStore
import com.wasl.app.data.LocalSearchQuery
import com.wasl.app.data.UnavailableAdvancedSearchStore
import com.wasl.app.data.WaslRepository
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal const val SEARCH_RESULT_LIMIT = 50

data class SearchUiState(
    val query: String = "",
    val normalizedQuery: String = "",
    val isLoading: Boolean = false,
    val loadError: String? = null,
    val results: List<AccountOverview> = emptyList(),
    val advancedResults: List<AdvancedSearchResult> = emptyList(),
    val hasMoreResults: Boolean = false,
    val hasMoreAdvancedResults: Boolean = false,
) {
    val isQueryBlank: Boolean
        get() = normalizedQuery.isEmpty()

    val hasAnyResults: Boolean
        get() = results.isNotEmpty() || advancedResults.isNotEmpty()
}

class SearchViewModel(
    private val repository: WaslRepository,
    private val advancedSearchStore: AdvancedSearchStore =
        repository as? AdvancedSearchStore ?: UnavailableAdvancedSearchStore,
    private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() },
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var observationJob: Job? = null

    fun updateQuery(value: String) {
        val normalized = LocalSearchQuery.normalize(value)
        if (normalized == _uiState.value.normalizedQuery) {
            _uiState.update { it.copy(query = value) }
            return
        }

        observationJob?.cancel()
        if (normalized.isEmpty()) {
            _uiState.value = SearchUiState(query = value)
            return
        }

        observeQuery(rawQuery = value, normalizedQuery = normalized)
    }

    fun clearQuery() {
        updateQuery("")
    }

    fun retryLoad() {
        val state = _uiState.value
        if (!state.isQueryBlank) {
            observeQuery(
                rawQuery = state.query,
                normalizedQuery = state.normalizedQuery,
            )
        }
    }

    private fun observeQuery(rawQuery: String, normalizedQuery: String) {
        observationJob?.cancel()
        _uiState.value = SearchUiState(
            query = rawQuery,
            normalizedQuery = normalizedQuery,
            isLoading = true,
        )
        val zoneId = zoneIdProvider()
        observationJob = viewModelScope.launch {
            combine(
                repository.observeSearchAccounts(
                    query = normalizedQuery,
                    limit = SEARCH_RESULT_LIMIT + 1,
                ),
                advancedSearchStore.observeAdvancedSearch(
                    query = normalizedQuery,
                    zoneId = zoneId,
                    limit = SEARCH_RESULT_LIMIT + 1,
                ),
            ) { accounts, advancedResults ->
                accounts to advancedResults
            }
                .catch { error ->
                    if (error is CancellationException) throw error
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadError = "تعذر البحث في البيانات المحفوظة.",
                        )
                    }
                }
                .collect { (accounts, advancedResults) ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadError = null,
                            results = accounts.take(SEARCH_RESULT_LIMIT),
                            advancedResults = advancedResults.take(SEARCH_RESULT_LIMIT),
                            hasMoreResults = accounts.size > SEARCH_RESULT_LIMIT,
                            hasMoreAdvancedResults = advancedResults.size > SEARCH_RESULT_LIMIT,
                        )
                    }
                }
        }
    }

    class Factory(
        private val repository: WaslRepository,
        private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() },
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SearchViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return SearchViewModel(
                repository = repository,
                zoneIdProvider = zoneIdProvider,
            ) as T
        }
    }
}
