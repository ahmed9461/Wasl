package com.wasl.app.data

import com.wasl.domain.DebtId
import java.time.Instant
import java.time.LocalDate

enum class PaymentPromiseStatus {
    PENDING,
    KEPT,
    MISSED,
    CANCELLED,
}

data class PaymentPromiseRecord(
    val id: String,
    val debtId: DebtId,
    val promisedDate: LocalDate,
    val status: PaymentPromiseStatus,
    val note: String? = null,
    val createdAt: Instant,
    val resolvedAt: Instant? = null,
    val resolutionNote: String? = null,
    val updatedAt: Instant,
) {
    init {
        require(id.isNotBlank()) { "Payment promise ID cannot be blank." }
        require(status == PaymentPromiseStatus.PENDING || resolvedAt != null) {
            "A resolved payment promise requires a resolution timestamp."
        }
        require(status != PaymentPromiseStatus.PENDING || resolvedAt == null) {
            "A pending payment promise cannot have a resolution timestamp."
        }
    }

    fun isOverdue(onDate: LocalDate): Boolean =
        status == PaymentPromiseStatus.PENDING && promisedDate.isBefore(onDate)
}

data class CreatePaymentPromiseCommand(
    val commandId: String,
    val promiseId: String,
    val debtId: DebtId,
    val promisedDate: LocalDate,
    val note: String? = null,
    val createdAt: Instant,
) {
    init {
        require(commandId.isNotBlank()) { "Payment promise command ID cannot be blank." }
        require(promiseId.isNotBlank()) { "Payment promise ID cannot be blank." }
        require(note == null || note.isNotBlank()) { "Promise note must be null or non-blank." }
    }
}

data class ResolvePaymentPromiseCommand(
    val commandId: String,
    val promiseId: String,
    val debtId: DebtId,
    val status: PaymentPromiseStatus,
    val resolvedAt: Instant,
    val note: String? = null,
) {
    init {
        require(commandId.isNotBlank()) { "Payment promise resolution command ID cannot be blank." }
        require(promiseId.isNotBlank()) { "Payment promise ID cannot be blank." }
        require(status != PaymentPromiseStatus.PENDING) {
            "A resolution command requires a terminal promise status."
        }
        require(note == null || note.isNotBlank()) { "Resolution note must be null or non-blank." }
    }
}
