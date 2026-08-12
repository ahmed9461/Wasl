package com.wasl.domain

@JvmInline
value class PersonId(val value: String) {
    init {
        require(value.isNotBlank()) { "Person ID cannot be blank." }
    }
}

@JvmInline
value class DebtId(val value: String) {
    init {
        require(value.isNotBlank()) { "Debt ID cannot be blank." }
    }
}

@JvmInline
value class LedgerEntryId(val value: String) {
    init {
        require(value.isNotBlank()) { "Ledger entry ID cannot be blank." }
    }
}
