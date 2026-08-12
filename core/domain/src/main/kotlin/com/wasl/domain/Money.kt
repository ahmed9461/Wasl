package com.wasl.domain

/**
 * An exact monetary amount represented in the currency's minor unit.
 *
 * Floating point values are intentionally excluded from the domain API.
 */
data class Money(
    val minorUnits: Long,
    val currency: CurrencyCode,
) {
    init {
        require(minorUnits >= 0L) { "Money cannot be negative." }
    }

    val isZero: Boolean
        get() = minorUnits == 0L

    fun plus(other: Money): Money {
        requireSameCurrency(other)
        return Money(
            minorUnits = Math.addExact(minorUnits, other.minorUnits),
            currency = currency,
        )
    }

    fun minus(other: Money): Money {
        requireSameCurrency(other)
        require(other.minorUnits <= minorUnits) { "Money subtraction cannot produce a negative value." }
        return Money(
            minorUnits = Math.subtractExact(minorUnits, other.minorUnits),
            currency = currency,
        )
    }

    private fun requireSameCurrency(other: Money) {
        require(currency == other.currency) {
            "Money operations require matching currencies."
        }
    }

    companion object {
        fun zero(currency: CurrencyCode): Money = Money(0L, currency)
    }
}
