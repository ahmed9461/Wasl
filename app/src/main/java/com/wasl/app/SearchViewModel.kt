package com.wasl.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wasl.app.data.AccountOverview
import com.wasl.app.data.LocalSearchQuery
import com.wasl.app.data.WaslRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal const val SEARCH_RESULT_LIMIT = 50

data class SearchUiState(
    val query: String = "",
    val normalizedQuery: String = "",
    val isLoading: Boolean = false,
    val loadError: String? = null,
    val results: List<AccountOverview> = emptyList(),
    val hasMoreResults: Boolean = false,
) {
    val isQueryBlank: Boolean
        get() = normalizedQuery.isEmpty()
}

class SearchViewModel(
    private val repository: WaslRepository,
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
        observationJob = viewModelScope.launch {
            repository.observeSearchAccounts(
                query = normalizedQuery,
                limit = SEARCH_RESULT_LIMIT + 1,
            )
                .catch { error ->
                    if (error is CancellationException) throw error
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadError = "تعذر البحث في البيانات المحفوظة.",
                        )
                    }
                }
                .collect { accounts ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadError = null,
                            results = accounts.take(SEARCH_RESULT_LIMIT),
                            hasMoreResults = accounts.size > SEARCH_RESULT_LIMIT,
                        )
                    }
                }
        }
    }

    class Factory(
        private val repository: WaslRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SearchViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return SearchViewModel(repository) as T
        }
    }
}
