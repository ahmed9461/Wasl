package com.wasl.app.data

import com.wasl.domain.CurrencyCode
import com.wasl.domain.MoneyInputParser
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

private val repeatedWhitespace = Regex("\\s+")
private val currencyFirstPattern = Regex("^(YER|SAR|USD)\\s+(.+)$", RegexOption.IGNORE_CASE)
private val currencyLastPattern = Regex("^(.+?)\\s+(YER|SAR|USD)$", RegexOption.IGNORE_CASE)
private val searchDateFormatters = listOf(
    DateTimeFormatter.ofPattern("uuuu-MM-dd", Locale.US).withResolverStyle(ResolverStyle.STRICT),
    DateTimeFormatter.ofPattern("d/M/uuuu", Locale.US).withResolverStyle(ResolverStyle.STRICT),
    DateTimeFormatter.ofPattern("d-M-uuuu", Locale.US).withResolverStyle(ResolverStyle.STRICT),
    DateTimeFormatter.ofPattern("d.M.uuuu", Locale.US).withResolverStyle(ResolverStyle.STRICT),
)

internal data class LocalSearchCriteria(
    val normalizedQuery: String,
    val queryPattern: String,
    val amountMinorByCurrency: Map<CurrencyCode, Long>,
    val dateStartMillis: Long?,
    val dateEndMillis: Long?,
    val dateEpochDay: Long?,
) {
    fun amountMinor(currency: CurrencyCode): Long? = amountMinorByCurrency[currency]
}

internal object LocalSearchQuery {
    fun normalize(value: String): String = value
        .trim()
        .replace(repeatedWhitespace, " ")

    fun toSqlLikePattern(value: String): String? {
        val normalized = normalize(value)
        if (normalized.isEmpty()) return null

        val escaped = buildString(normalized.length) {
            normalized.forEach { character ->
                when (character) {
                    '\\' -> append("\\\\")
                    '%' -> append("\\%")
                    '_' -> append("\\_")
                    else -> append(character)
                }
            }
        }
        return "%$escaped%"
    }

    fun toAdvancedCriteria(value: String, zoneId: ZoneId): LocalSearchCriteria? {
        val normalized = normalize(value)
        val pattern = toSqlLikePattern(normalized) ?: return null
        val date = parseExactDate(normalized)
        val dateStart = date?.atStartOfDay(zoneId)?.toInstant()?.toEpochMilli()
        val dateEnd = date?.plusDays(1)?.atStartOfDay(zoneId)?.toInstant()?.toEpochMilli()
        return LocalSearchCriteria(
            normalizedQuery = normalized,
            queryPattern = pattern,
            amountMinorByCurrency = parseAmountCandidates(normalized),
            dateStartMillis = dateStart,
            dateEndMillis = dateEnd,
            dateEpochDay = date?.toEpochDay(),
        )
    }

    private fun parseAmountCandidates(value: String): Map<CurrencyCode, Long> {
        val currencyFirst = currencyFirstPattern.matchEntire(value)
        if (currencyFirst != null) {
            return parseExplicitCurrencyAmount(
                currencyCode = currencyFirst.groupValues[1],
                rawAmount = currencyFirst.groupValues[2],
            )
        }
        val currencyLast = currencyLastPattern.matchEntire(value)
        if (currencyLast != null) {
            return parseExplicitCurrencyAmount(
                currencyCode = currencyLast.groupValues[2],
                rawAmount = currencyLast.groupValues[1],
            )
        }

        return listOf(CurrencyCode.YER, CurrencyCode.SAR, CurrencyCode.USD)
            .mapNotNull { currency ->
                runCatching { MoneyInputParser.parse(value, currency) }
                    .getOrNull()
                    ?.let { money -> currency to money.minorUnits }
            }
            .toMap()
    }

    private fun parseExplicitCurrencyAmount(
        currencyCode: String,
        rawAmount: String,
    ): Map<CurrencyCode, Long> {
        val currency = CurrencyCode.of(currencyCode)
        val money = runCatching { MoneyInputParser.parse(rawAmount, currency) }.getOrNull()
            ?: return emptyMap()
        return mapOf(currency to money.minorUnits)
    }

    private fun parseExactDate(value: String): LocalDate? {
        val latinDigits = buildString(value.length) {
            value.forEach { character ->
                append(
                    when (character) {
                        in '\u0660'..'\u0669' -> '0' + (character - '\u0660')
                        in '\u06F0'..'\u06F9' -> '0' + (character - '\u06F0')
                        else -> character
                    },
                )
            }
        }
        return searchDateFormatters.firstNotNullOfOrNull { formatter ->
            runCatching { LocalDate.parse(latinDigits, formatter) }.getOrNull()
        }
    }
}
