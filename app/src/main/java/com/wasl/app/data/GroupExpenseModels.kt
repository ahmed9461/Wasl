package com.wasl.app.data

import com.wasl.domain.GroupExpense
import java.time.Instant

data class CreateGroupExpenseCommand(
    val commandId: String,
    val expense: GroupExpense,
    val createdAt: Instant,
) {
    init {
        require(commandId.isNotBlank()) { "Group expense command ID cannot be blank." }
        require(!createdAt.isBefore(expense.occurredAt)) {
            "Group expense cannot be created before it occurred."
        }
    }
}

data class GroupExpenseRecord(
    val commandId: String,
    val expense: GroupExpense,
    val createdAt: Instant,
) {
    init {
        require(commandId.isNotBlank()) { "Group expense command ID cannot be blank." }
        require(!createdAt.isBefore(expense.occurredAt)) {
            "Persisted group expense creation time is invalid."
        }
    }
}
