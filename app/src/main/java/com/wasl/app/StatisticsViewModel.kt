package com.wasl.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wasl.app.data.PaymentPromiseRecord
import com.wasl.app.data.PaymentPromiseStore
import com.wasl.app.data.WaslRepository
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

data class StatisticsUiState(
    val isLoading: Boolean = true,
    val statistics: ObjectiveStatistics? = null,
    val errorMessage: String? = null,
)

class StatisticsViewModel(
    private val repository: WaslRepository,
    private val promiseStore: PaymentPromiseStore,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()
    private var observationJob: Job? = null

    init {
        observe()
    }

    fun retry() = observe()

    private fun observe() {
        observationJob?.cancel()
        _uiState.value = StatisticsUiState(isLoading = true)
        observationJob = viewModelScope.launch {
            repository.observeAccounts()
                .catch { error ->
                    if (error is CancellationException) throw error
                    _uiState.value = StatisticsUiState(
                        isLoading = false,
                        errorMessage = "تعذر قراءة الإحصاءات من البيانات المحلية.",
                    )
                }
                .collectLatest { accounts ->
                    if (accounts.isEmpty()) {
                        _uiState.value = StatisticsUiState(
                            isLoading = false,
                            statistics = ObjectiveStatisticsBuilder.build(
                                emptyList(),
                                emptyList(),
                                zoneId,
                            ),
                        )
                        return@collectLatest
                    }
                    val promiseFlows = accounts.map { account ->
                        promiseStore.observePaymentPromises(account.ledger.header.id)
                    }
                    val allPromises = if (promiseFlows.isEmpty()) {
                        flowOf(emptyList<PaymentPromiseRecord>())
                    } else {
                        combine(promiseFlows) { lists -> lists.flatMap { it } }
                    }
                    allPromises.collect { promises ->
                        _uiState.value = StatisticsUiState(
                            isLoading = false,
                            statistics = ObjectiveStatisticsBuilder.build(accounts, promises, zoneId),
                        )
                    }
                }
        }
    }

    class Factory(
        private val repository: WaslRepository,
        private val promiseStore: PaymentPromiseStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(StatisticsViewModel::class.java))
            return StatisticsViewModel(repository, promiseStore) as T
        }
    }
}
