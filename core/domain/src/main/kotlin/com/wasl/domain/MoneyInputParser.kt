package com.wasl.domain

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

    private const val MAX_INPUT_LENGTH = 64
    private val plainDigits = Regex("[0-9]+")
    private val commaGroupedInteger = Regex("[+-]?[0-9]{1,3}(,[0-9]{3})+")
    private val groupingSeparators = setOf(',', '\u066C', ' ', '_')

    private fun normalize(raw: String, fractionDigits: Int): String {
        // Bound the work before constructing BigDecimal; exponent notation and
        // arbitrary pasted text are not part of a user-entered money amount.
        require(raw.length <= MAX_INPUT_LENGTH) { "Amount is outside the supported range." }
        val latinDigits = buildString(raw.length) {
            raw.trim().forEach { character ->
                append(
                    when (character) {
                        in '\u0660'..'\u0669' -> '0' + (character - '\u0660')
                        in '\u06F0'..'\u06F9' -> '0' + (character - '\u06F0')
                        '\u066B' -> '.'
                        '\u00A0', '\u202F' -> ' '
                        else -> character
                    },
                )
            }
        }
        require(latinDigits.isNotEmpty()) { "Amount is required." }

        val decimalNormalized = when {
            ',' !in latinDigits || '.' in latinDigits -> latinDigits
            commaGroupedInteger.matches(latinDigits) -> latinDigits.replace(",", "")
            latinDigits.count { it == ',' } == 1 && fractionDigits > 0 &&
                latinDigits.substringAfter(',').let {
                    it.length in 1..fractionDigits && plainDigits.matches(it)
                } -> latinDigits.replace(',', '.')
            else -> throw IllegalArgumentException("Amount separators are ambiguous.")
        }
        require(decimalNormalized.count { it == '.' } <= 1) { "Amount must be a valid number." }
        val parts = decimalNormalized.split('.', limit = 2)
        val integerPart = parts[0]
        val sign = integerPart.firstOrNull()?.takeIf { it == '+' || it == '-' }
        val unsignedInteger = if (sign != null) integerPart.drop(1) else integerPart
        val separators = unsignedInteger.filter { it in groupingSeparators }.toSet()
        require(separators.size <= 1) { "Amount separators are ambiguous." }

        val integerDigits = if (separators.isEmpty()) {
            require(unsignedInteger.isEmpty() || plainDigits.matches(unsignedInteger)) {
                "Amount must be a valid number."
            }
            unsignedInteger
        } else {
            val groups = unsignedInteger.split(separators.single())
            require(groups.first().length in 1..3 && groups.all { plainDigits.matches(it) } &&
                groups.drop(1).all { it.length == 3 }) {
                "Amount grouping is invalid."
            }
            groups.joinToString("")
        }
        val fraction = parts.getOrNull(1)
        require(fraction == null || plainDigits.matches(fraction)) { "Amount must be a valid number." }
        require(integerDigits.isNotEmpty() || fraction != null) { "Amount is required." }
        return buildString {
            if (sign != null) append(sign)
            append(integerDigits.ifEmpty { "0" })
            if (fraction != null) { append('.'); append(fraction) }
        }
    }
}
