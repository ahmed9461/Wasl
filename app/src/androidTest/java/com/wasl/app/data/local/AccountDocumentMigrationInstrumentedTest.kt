package com.wasl.app.data.local

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountDocumentMigrationInstrumentedTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WaslDatabase::class.java,
    )

    @Test
    fun versionSixMigratesToSevenPreservingPaymentReceiptAndAllowingLedgerlessDocuments() {
        val databaseName = "wasl-schema-v6-account-documents.db"
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(databaseName)

        migrationHelper.createDatabase(databaseName, 6).apply {
            execSQL(
                """
                INSERT INTO persons (
                    id, display_name, created_at, updated_at
                ) VALUES ('person-v6', 'عميل الإصدار السادس', 1, 1)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO debts (
                    id, person_id, direction, original_amount_minor, currency_code,
                    opened_at, lifecycle_state, created_at, updated_at
                ) VALUES ('debt-v6', 'person-v6', 'RECEIVABLE', 100000, 'YER',
                    1, 'ACTIVE', 1, 1)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO ledger_entries (
                    id, command_id, debt_id, kind, amount_minor, currency_code,
                    occurred_at, recorded_at, sequence_number
                ) VALUES ('payment-v6', 'payment-command-v6', 'debt-v6', 'PAYMENT',
                    25000, 'YER', 2, 2, 1)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO document_identities (
                    id, display_name, is_default, created_at, updated_at
                ) VALUES ('identity-v6', 'متجر وصل', 1, 1, 1)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO issued_documents (
                    id, command_id, document_type, status, document_number,
                    issue_year, sequence_number, debt_id, ledger_entry_id, identity_id,
                    person_id, person_name_snapshot, amount_minor, currency_code,
                    issued_at, snapshot_version, snapshot_json, pdf_relative_path,
                    created_at, updated_at
                ) VALUES (
                    'document-v6', 'document-command-v6', 'PAYMENT_RECEIPT', 'PENDING_PDF',
                    'PAY-2026-00001', 2026, 1, 'debt-v6', 'payment-v6', 'identity-v6',
                    'person-v6', 'عميل الإصدار السادس', 25000, 'YER', 3, 1, '{}',
                    'documents/PAY-2026-00001.pdf', 3, 3
                )
                """.trimIndent(),
            )
            close()
        }

        migrationHelper.runMigrationsAndValidate(
            databaseName,
            7,
            true,
            WaslDatabase.MIGRATION_6_7,
        ).use { migrated ->
            migrated.query(
                """
                SELECT document_number, ledger_entry_id, amount_minor
                FROM issued_documents WHERE id = 'document-v6'
                """.trimIndent(),
            ).use {
                check(it.moveToFirst())
                assertEquals("PAY-2026-00001", it.getString(0))
                assertEquals("payment-v6", it.getString(1))
                assertEquals(25_000L, it.getLong(2))
            }

            migrated.query("PRAGMA table_info(`issued_documents`)").use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                val notNullIndex = cursor.getColumnIndexOrThrow("notnull")
                var ledgerEntryNotNull: Int? = null
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == "ledger_entry_id") {
                        ledgerEntryNotNull = cursor.getInt(notNullIndex)
                    }
                }
                assertEquals(0, ledgerEntryNotNull)
            }

            migrated.execSQL(
                """
                INSERT INTO issued_documents (
                    id, command_id, document_type, status, document_number,
                    issue_year, sequence_number, debt_id, ledger_entry_id, identity_id,
                    person_id, person_name_snapshot, amount_minor, currency_code,
                    issued_at, snapshot_version, snapshot_json, pdf_relative_path,
                    created_at, updated_at
                ) VALUES (
                    'debt-document-v7', 'debt-document-command-v7', 'DEBT_RECEIPT', 'PENDING_PDF',
                    'DEBT-2026-00002', 2026, 2, 'debt-v6', NULL, 'identity-v6',
                    'person-v6', 'عميل الإصدار السادس', 75000, 'YER', 4, 1, '{}',
                    'documents/DEBT-2026-00002.pdf', 4, 4
                )
                """.trimIndent(),
            )

            migrated.query("SELECT COUNT(*) FROM issued_documents WHERE ledger_entry_id IS NULL").use {
                check(it.moveToFirst())
                assertEquals(1L, it.getLong(0))
            }
            migrated.query("SELECT document_number FROM payment_issued_documents").use {
                check(it.moveToFirst())
                assertEquals("PAY-2026-00001", it.getString(0))
                assertEquals(false, it.moveToNext())
            }
        }

        context.deleteDatabase(databaseName)
    }
}
