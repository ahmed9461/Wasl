package com.wasl.app.data

import com.wasl.domain.DebtId
import com.wasl.domain.Money
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

enum class AdvancedSearchResultType {
    DEBT,
    PAYMENT,
    PAYMENT_REVERSAL,
    DOCUMENT,
}

data class AdvancedSearchResult(
    val id: String,
    val type: AdvancedSearchResultType,
    val debtId: DebtId,
    val personName: String,
    val description: String? = null,
    val amount: Money,
    val date: LocalDate,
    val documentNumber: String? = null,
    val documentType: DocumentType? = null,
    val documentStatus: DocumentStatus? = null,
) {
    init {
        require(id.isNotBlank()) { "Search result ID cannot be blank." }
        require(personName.isNotBlank()) { "Search result person name cannot be blank." }
        if (type == AdvancedSearchResultType.DOCUMENT) {
            require(!documentNumber.isNullOrBlank()) {
                "Document search result requires a document number."
            }
            require(documentType != null && documentStatus != null) {
                "Document search result requires document metadata."
            }
        } else {
            require(documentNumber == null && documentType == null && documentStatus == null) {
                "Non-document search result cannot contain document metadata."
            }
        }
    }
}

interface AdvancedSearchStore {
    fun observeAdvancedSearch(
        query: String,
        zoneId: ZoneId,
        limit: Int,
    ): Flow<List<AdvancedSearchResult>>
}

object UnavailableAdvancedSearchStore : AdvancedSearchStore {
    override fun observeAdvancedSearch(
        query: String,
        zoneId: ZoneId,
        limit: Int,
    ): Flow<List<AdvancedSearchResult>> = flowOf(emptyList())
}
