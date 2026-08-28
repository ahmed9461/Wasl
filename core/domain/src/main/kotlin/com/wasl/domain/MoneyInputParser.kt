package com.wasl.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Parses user-entered amounts without passing through floating-point numbers.
 *
 * The MVP supports YER with no fractional unit and SAR/USD with two fractional
 * digits. Arabic-Indic digits and the Arabic decimal/grouping separators are
 * normalized before exact conversion to minor units.
 */
object MoneyInputParser {
    fun parse(raw: String, currency: CurrencyCode): Money {
        val fractionDigits = fractionDigits(currency)
        val normalized = normalize(raw, fractionDigits)
        require(normalized.isNotEmpty()) { "Amount is required." }

        val majorAmount = normalized.toBigDecimalOrNull()
            ?: throw IllegalArgumentException("Amount must be a valid number.")
        require(majorAmount.signum() > 0) { "Amount must be greater than zero." }

        val scaled = try {
            majorAmount.setScale(fractionDigits, RoundingMode.UNNECESSARY)
        } catch (_: ArithmeticException) {
            throw IllegalArgumentException(
                "Amount has more fractional digits than the currency supports.",
            )
        }

        val minorUnits = try {
            scaled.movePointRight(fractionDigits).longValueExact()
        } catch (_: ArithmeticException) {
            throw IllegalArgumentException("Amount is outside the supported range.")
        }
        return Money(minorUnits = minorUnits, currency = currency)
    }

    fun fractionDigits(currency: CurrencyCode): Int = when (currency) {
        CurrencyCode.YER -> 0
        CurrencyCode.SAR, CurrencyCode.USD -> 2
        else -> throw IllegalArgumentException(
            "Currency ${currency.value} is not enabled in the MVP.",
        )
    }

    private fun normalize(raw: String, fractionDigits: Int): String {
        val latinDigits = buildString(raw.length) {
            raw.trim().forEach { character ->
                append(
                    when (character) {
                        in '\u0660'..'\u0669' -> '0' + (character - '\u0660')
                        in '\u06F0'..'\u06F9' -> '0' + (character - '\u06F0')
                        '\u066B' -> '.'
                        '\u066C', ' ', '_' -> return@forEach
                        else -> character
                    },
                )
            }
        }

        if (',' !in latinDigits) return latinDigits
        if ('.' in latinDigits) return latinDigits.replace(",", "")

        val groupedInteger = Regex("[+-]?\\d{1,3}(,\\d{3})+")
        if (groupedInteger.matches(latinDigits)) return latinDigits.replace(",", "")

        val commaCount = latinDigits.count { it == ',' }
        val digitsAfterComma = latinDigits.substringAfterLast(',').length
        return if (commaCount == 1 && fractionDigits > 0 && digitsAfterComma <= fractionDigits) {
            latinDigits.replace(',', '.')
        } else {
            throw IllegalArgumentException("Amount separators are ambiguous.")
        }
    }
}
