package com.wasl.app

import com.wasl.app.data.AccountOverview
import com.wasl.app.data.PaymentPromiseRecord
import com.wasl.app.data.PaymentPromiseStatus
import java.time.Duration
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class ObjectiveStatistics(
    val totalAccounts: Int,
    val settledAccounts: Int,
    val openAccounts: Int,
    val averageSettlementDays: Double?,
    val settledAccountsWithDueDate: Int,
    val lateSettledAccounts: Int,
    val averageLateDays: Double?,
    val keptPromises: Int,
    val missedPromises: Int,
    val pendingPromises: Int,
    val cancelledPromises: Int,
)

internal object ObjectiveStatisticsBuilder {
    fun build(
        accounts: List<AccountOverview>,
        promises: List<PaymentPromiseRecord>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): ObjectiveStatistics {
        val settled = accounts.filter { account ->
            account.closedAt != null && account.ledger.balance.isZero
        }
        val settlementDays = settled.mapNotNull { account ->
            val closedAt = account.closedAt ?: return@mapNotNull null
            Duration.between(account.ledger.header.openedAt, closedAt)
                .takeUnless { it.isNegative }
                ?.toMillis()
                ?.toDouble()
                ?.div(Duration.ofDays(1).toMillis().toDouble())
        }
        val settledWithDueDate = settled.mapNotNull { account ->
            val dueDate = account.ledger.header.dueDate ?: return@mapNotNull null
            val closedAt = account.closedAt ?: return@mapNotNull null
            dueDate to closedAt.atZone(zoneId).toLocalDate()
        }
        val lateDays = settledWithDueDate.mapNotNull { (dueDate, closedDate) ->
            ChronoUnit.DAYS.between(dueDate, closedDate)
                .takeIf { it > 0 }
                ?.toDouble()
        }

        return ObjectiveStatistics(
            totalAccounts = accounts.size,
            settledAccounts = settled.size,
            openAccounts = accounts.count { it.closedAt == null && !it.ledger.balance.isZero },
            averageSettlementDays = settlementDays.averageOrNull(),
            settledAccountsWithDueDate = settledWithDueDate.size,
            lateSettledAccounts = lateDays.size,
            averageLateDays = lateDays.averageOrNull(),
            keptPromises = promises.count { it.status == PaymentPromiseStatus.KEPT },
            missedPromises = promises.count { it.status == PaymentPromiseStatus.MISSED },
            pendingPromises = promises.count { it.status == PaymentPromiseStatus.PENDING },
            cancelledPromises = promises.count { it.status == PaymentPromiseStatus.CANCELLED },
        )
    }

    private fun List<Double>.averageOrNull(): Double? =
        if (isEmpty()) null else average()
}
