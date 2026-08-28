package com.wasl.domain

import java.util.Locale

/**
 * ISO 4217-style currency code.
 *
 * A value object is used instead of an enum so adding a supported currency later
 * does not require changing the financial aggregate model.
 */
@JvmInline
value class CurrencyCode private constructor(val value: String) {
    override fun toString(): String = value

    companion object {
        private val CURRENCY_PATTERN = Regex("[A-Z]{3}")

        val YER: CurrencyCode = of("YER")
        val SAR: CurrencyCode = of("SAR")
        val USD: CurrencyCode = of("USD")

        fun of(raw: String): CurrencyCode {
            val normalized = raw.trim().uppercase(Locale.ROOT)
            require(CURRENCY_PATTERN.matches(normalized)) {
                "Currency code must contain exactly three Latin letters."
            }
            return CurrencyCode(normalized)
        }
    }
}
