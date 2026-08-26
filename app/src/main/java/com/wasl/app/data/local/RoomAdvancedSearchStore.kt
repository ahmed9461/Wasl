package com.wasl.app.data.local

import androidx.sqlite.db.SimpleSQLiteQuery
import com.wasl.app.data.AdvancedSearchResult
import com.wasl.app.data.AdvancedSearchResultType
import com.wasl.app.data.AdvancedSearchStore
import com.wasl.app.data.DocumentStatus
import com.wasl.app.data.DocumentType
import com.wasl.app.data.LocalSearchCriteria
import com.wasl.app.data.LocalSearchQuery
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtId
import com.wasl.domain.Money
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RoomAdvancedSearchStore(
    private val database: WaslDatabase,
) : AdvancedSearchStore {
    override fun observeAdvancedSearch(
        query: String,
        zoneId: ZoneId,
        limit: Int,
    ): Flow<List<AdvancedSearchResult>> {
        require(limit > 0) { "Search limit must be positive." }
        val criteria = LocalSearchQuery.toAdvancedCriteria(query, zoneId)
            ?: return flowOf(emptyList())

        return database.invalidationTracker.createFlow(
            "debts",
            "persons",
            "ledger_entries",
            "issued_documents",
            emitInitialState = true,
        ).map {
            withContext(Dispatchers.IO) {
                executeSearch(criteria = criteria, zoneId = zoneId, limit = limit)
            }
        }
    }

    private fun executeSearch(
        criteria: LocalSearchCriteria,
        zoneId: ZoneId,
        limit: Int,
    ): List<AdvancedSearchResult> {
        val query = buildSearchQuery(criteria, limit)
        val results = mutableListOf<AdvancedSearchResult>()
        database.openHelper.readableDatabase.query(query).use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow("result_id")
            val typeIndex = cursor.getColumnIndexOrThrow("result_type")
            val debtIdIndex = cursor.getColumnIndexOrThrow("debt_id")
            val personNameIndex = cursor.getColumnIndexOrThrow("person_name")
            val descriptionIndex = cursor.getColumnIndexOrThrow("description")
            val amountIndex = cursor.getColumnIndexOrThrow("amount_minor")
            val currencyIndex = cursor.getColumnIndexOrThrow("currency_code")
            val eventAtIndex = cursor.getColumnIndexOrThrow("event_at")
            val documentNumberIndex = cursor.getColumnIndexOrThrow("document_number")
            val documentTypeIndex = cursor.getColumnIndexOrThrow("document_type")
            val documentStatusIndex = cursor.getColumnIndexOrThrow("document_status")

            while (cursor.moveToNext()) {
                val amount = Money(
                    minorUnits = cursor.getLong(amountIndex),
                    currency = CurrencyCode.of(cursor.getString(currencyIndex)),
                )
                val type = AdvancedSearchResultType.valueOf(cursor.getString(typeIndex))
                results += AdvancedSearchResult(
                    id = cursor.getString(idIndex),
                    type = type,
                    debtId = DebtId(cursor.getString(debtIdIndex)),
                    personName = cursor.getString(personNameIndex),
                    description = cursor.nullableString(descriptionIndex),
                    amount = amount,
                    date = Instant.ofEpochMilli(cursor.getLong(eventAtIndex))
                        .atZone(zoneId)
                        .toLocalDate(),
                    documentNumber = cursor.nullableString(documentNumberIndex),
                    documentType = cursor.nullableString(documentTypeIndex)?.let(DocumentType::valueOf),
                    documentStatus = cursor.nullableString(documentStatusIndex)
                        ?.let(DocumentStatus::valueOf),
                )
            }
        }
        return results
    }

    private fun buildSearchQuery(
        criteria: LocalSearchCriteria,
        limit: Int,
    ): SimpleSQLiteQuery {
        val selects = mutableListOf<String>()
        val args = mutableListOf<Any>()

        val debtConditions = mutableListOf<String>()
        addAmountConditions(
            conditions = debtConditions,
            args = args,
            amountExpression = "d.original_amount_minor",
            currencyExpression = "d.currency_code",
            criteria = criteria,
        )
        if (criteria.dateStartMillis != null && criteria.dateEndMillis != null) {
            debtConditions += "(d.opened_at >= ? AND d.opened_at < ?)"
            args += criteria.dateStartMillis
            args += criteria.dateEndMillis
        }
        if (criteria.dateEpochDay != null) {
            debtConditions += "d.due_date_epoch_day = ?"
            args += criteria.dateEpochDay
        }
        if (debtConditions.isNotEmpty()) {
            selects += """
                SELECT
                    d.id AS result_id,
                    'DEBT' AS result_type,
                    d.id AS debt_id,
                    p.display_name AS person_name,
                    d.description AS description,
                    d.original_amount_minor AS amount_minor,
                    d.currency_code AS currency_code,
                    d.opened_at AS event_at,
                    NULL AS document_number,
                    NULL AS document_type,
                    NULL AS document_status
                FROM debts d
                INNER JOIN persons p ON p.id = d.person_id
                WHERE d.lifecycle_state = 'ACTIVE'
                  AND (${debtConditions.joinToString(" OR ")})
            """.trimIndent()
        }

        val ledgerConditions = mutableListOf<String>()
        ledgerConditions += """(
            COALESCE(l.note, '') LIKE ? ESCAPE '\' COLLATE NOCASE
            OR COALESCE(l.reason, '') LIKE ? ESCAPE '\' COLLATE NOCASE
        )""".trimIndent()
        args += criteria.queryPattern
        args += criteria.queryPattern
        addAmountConditions(
            conditions = ledgerConditions,
            args = args,
            amountExpression = "COALESCE(l.amount_minor, reversed.amount_minor)",
            currencyExpression = "COALESCE(l.currency_code, reversed.currency_code)",
            criteria = criteria,
        )
        if (criteria.dateStartMillis != null && criteria.dateEndMillis != null) {
            ledgerConditions += "(COALESCE(l.occurred_at, l.recorded_at) >= ? AND COALESCE(l.occurred_at, l.recorded_at) < ?)"
            args += criteria.dateStartMillis
            args += criteria.dateEndMillis
        }
        selects += """
            SELECT
                l.id AS result_id,
                CASE WHEN l.kind = 'PAYMENT' THEN 'PAYMENT' ELSE 'PAYMENT_REVERSAL' END AS result_type,
                l.debt_id AS debt_id,
                p.display_name AS person_name,
                CASE WHEN l.kind = 'PAYMENT' THEN l.note ELSE l.reason END AS description,
                COALESCE(l.amount_minor, reversed.amount_minor) AS amount_minor,
                COALESCE(l.currency_code, reversed.currency_code) AS currency_code,
                COALESCE(l.occurred_at, l.recorded_at) AS event_at,
                NULL AS document_number,
                NULL AS document_type,
                NULL AS document_status
            FROM ledger_entries l
            INNER JOIN debts d ON d.id = l.debt_id
            INNER JOIN persons p ON p.id = d.person_id
            LEFT JOIN ledger_entries reversed ON reversed.id = l.reverses_entry_id
            WHERE d.lifecycle_state = 'ACTIVE'
              AND l.kind IN ('PAYMENT', 'PAYMENT_REVERSAL')
              AND (${ledgerConditions.joinToString(" OR ")})
        """.trimIndent()

        val documentConditions = mutableListOf<String>()
        documentConditions += "doc.document_number LIKE ? ESCAPE '\' COLLATE NOCASE"
        args += criteria.queryPattern
        addAmountConditions(
            conditions = documentConditions,
            args = args,
            amountExpression = "doc.amount_minor",
            currencyExpression = "doc.currency_code",
            criteria = criteria,
        )
        if (criteria.dateStartMillis != null && criteria.dateEndMillis != null) {
            documentConditions += "(doc.issued_at >= ? AND doc.issued_at < ?)"
            args += criteria.dateStartMillis
            args += criteria.dateEndMillis
        }
        selects += """
            SELECT
                doc.id AS result_id,
                'DOCUMENT' AS result_type,
                doc.debt_id AS debt_id,
                doc.person_name_snapshot AS person_name,
                NULL AS description,
                doc.amount_minor AS amount_minor,
                doc.currency_code AS currency_code,
                doc.issued_at AS event_at,
                doc.document_number AS document_number,
                doc.document_type AS document_type,
                doc.status AS document_status
            FROM issued_documents doc
            INNER JOIN debts d ON d.id = doc.debt_id
            WHERE d.lifecycle_state = 'ACTIVE'
              AND (${documentConditions.joinToString(" OR ")})
        """.trimIndent()

        val sql = buildString {
            append("SELECT * FROM (")
            append(selects.joinToString(" UNION ALL "))
            append(") ORDER BY event_at DESC, result_id DESC LIMIT ?")
        }
        args += limit
        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }

    private fun addAmountConditions(
        conditions: MutableList<String>,
        args: MutableList<Any>,
        amountExpression: String,
        currencyExpression: String,
        criteria: LocalSearchCriteria,
    ) {
        listOf(CurrencyCode.YER, CurrencyCode.SAR, CurrencyCode.USD).forEach { currency ->
            criteria.amountMinor(currency)?.let { amountMinor ->
                conditions += "($currencyExpression = '${currency.value}' AND $amountExpression = ?)"
                args += amountMinor
            }
        }
    }

    private fun android.database.Cursor.nullableString(index: Int): String? =
        if (isNull(index)) null else getString(index)
}
