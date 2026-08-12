package com.wasl.domain

/**
 * Current balances grouped by currency. Different currencies are never merged
 * into one misleading number.
 */
data class BalanceSummary(
    val receivableByCurrency: Map<CurrencyCode, Money>,
    val payableByCurrency: Map<CurrencyCode, Money>,
)

object BalanceSummaryCalculator {
    fun calculate(ledgers: Iterable<DebtLedger>): BalanceSummary {
        val receivable = mutableMapOf<CurrencyCode, Long>()
        val payable = mutableMapOf<CurrencyCode, Long>()

        ledgers.forEach { ledger ->
            val balance = ledger.balance
            val target = when (ledger.header.direction) {
                DebtDirection.RECEIVABLE -> receivable
                DebtDirection.PAYABLE -> payable
            }
            val current = target[balance.currency] ?: 0L
            target[balance.currency] = Math.addExact(current, balance.minorUnits)
        }

        return BalanceSummary(
            receivableByCurrency = receivable.toMoneyMap(),
            payableByCurrency = payable.toMoneyMap(),
        )
    }

    private fun Map<CurrencyCode, Long>.toMoneyMap(): Map<CurrencyCode, Money> =
        entries
            .sortedBy { it.key.value }
            .associate { (currency, minorUnits) ->
                currency to Money(minorUnits, currency)
            }
}
