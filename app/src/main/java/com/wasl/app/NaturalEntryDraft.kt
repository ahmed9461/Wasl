package com.wasl.app

import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import java.time.LocalDate

enum class NaturalEntryKind {
    DEBT,
    PAYMENT,
    PROMISE,
    UNKNOWN,
}

enum class NaturalDraftField {
    PERSON,
    DIRECTION,
    AMOUNT,
    CURRENCY,
}

data class NaturalEntryDraft(
    val sourceText: String,
    val kind: NaturalEntryKind,
    val personName: String? = null,
    val direction: DebtDirection? = null,
    val amountMinorUnits: Long? = null,
    val currency: CurrencyCode? = null,
    val entryDate: LocalDate? = null,
    val promisedDate: LocalDate? = null,
    val missingRequiredFields: Set<NaturalDraftField> = emptySet(),
    val warnings: List<String> = emptyList(),
) {
    val canPreviewAsDebt: Boolean
        get() = kind == NaturalEntryKind.DEBT && missingRequiredFields.isEmpty()

    /**
     * Parsing never authorizes persistence. The UI must still show a preview and
     * require an explicit user confirmation before creating any financial record.
     */
    val requiresExplicitConfirmation: Boolean
        get() = true
}
