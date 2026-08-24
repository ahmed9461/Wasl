package com.wasl.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WaslDatabaseBaselineTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WaslDatabase::class.java,
    )

    @Test
    fun versionOneMigratesToVersionSixWithoutLosingDebtData() {
        val databaseName = "wasl-schema-v1.db"
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(databaseName)

        migrationHelper.createDatabase(databaseName, 1).apply {
            execSQL(
                """
                INSERT INTO persons (
                    id, display_name, created_at, updated_at
                ) VALUES ('person-v1', 'سجل قديم', 1, 1)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO debts (
                    id, person_id, direction, original_amount_minor, currency_code,
                    opened_at, due_date_epoch_day, lifecycle_state, created_at, updated_at
                ) VALUES ('debt-v1', 'person-v1', 'RECEIVABLE', 5000, 'YER',
                    1, 20680, 'ACTIVE', 1, 1)
                """.trimIndent(),
            )
            close()
        }

        migrationHelper.runMigrationsAndValidate(
            databaseName,
            6,
            true,
            WaslDatabase.MIGRATION_1_2,
            WaslDatabase.MIGRATION_2_3,
            WaslDatabase.MIGRATION_3_4,
            WaslDatabase.MIGRATION_4_5,
            WaslDatabase.MIGRATION_5_6,
        ).use { migrated ->
            migrated.query("SELECT original_amount_minor, due_date_epoch_day FROM debts").use {
                check(it.moveToFirst())
                assertEquals(5000L, it.getLong(0))
                assertEquals(20680L, it.getLong(1))
            }
            assertEmpty(migrated, "reminders")
            assertEmpty(migrated, "audit_events")
            assertEmpty(migrated, "document_identities")
            assertEmpty(migrated, "issued_documents")
            assertEmpty(migrated, "payment_promises")
            assertEmpty(migrated, "installment_plans")
            assertEmpty(migrated, "installments")
        }

        val database = Room.databaseBuilder(context, WaslDatabase::class.java, databaseName)
            .addMigrations(*WaslDatabase.ALL_MIGRATIONS)
            .build()
        database.openHelper.writableDatabase
        database.close()

        context.deleteDatabase(databaseName)
    }

    @Test
    fun versionTwoMigratesToVersionSixWithoutLosingReminderData() {
        val databaseName = "wasl-schema-v2.db"
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(databaseName)

        migrationHelper.createDatabase(databaseName, 2).apply {
            execSQL(
                """
                INSERT INTO persons (
                    id, display_name, created_at, updated_at
                ) VALUES ('person-v2', 'سجل بتذكير', 1, 1)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO debts (
                    id, person_id, direction, original_amount_minor, currency_code,
                    opened_at, due_date_epoch_day, lifecycle_state, created_at, updated_at
                ) VALUES ('debt-v2', 'person-v2', 'RECEIVABLE', 9000, 'YER',
                    1, 20681, 'ACTIVE', 1, 1)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO reminders (
                    id, subject_type, subject_id, reminder_type, schedule_type,
                    trigger_at, zone_id, status, created_at, updated_at
                ) VALUES ('reminder-v2', 'DEBT', 'debt-v2', 'DUE_DATE', 'WORK',
                    1000, 'UTC', 'SCHEDULED', 1, 1)
                """.trimIndent(),
            )
            close()
        }

        migrationHelper.runMigrationsAndValidate(
            databaseName,
            6,
            true,
            WaslDatabase.MIGRATION_2_3,
            WaslDatabase.MIGRATION_3_4,
            WaslDatabase.MIGRATION_4_5,
            WaslDatabase.MIGRATION_5_6,
        ).use { migrated ->
            migrated.query("SELECT id, status FROM reminders").use {
                check(it.moveToFirst())
                assertEquals("reminder-v2", it.getString(0))
                assertEquals("SCHEDULED", it.getString(1))
            }
            assertEmpty(migrated, "audit_events")
            assertEmpty(migrated, "payment_promises")
            assertEmpty(migrated, "installment_plans")
            assertEmpty(migrated, "installments")
        }

        context.deleteDatabase(databaseName)
    }

    @Test
    fun versionThreeMigratesToVersionSixAndKeepsDocumentStorage() {
        val databaseName = "wasl-schema-v3.db"
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(databaseName)

        migrationHelper.createDatabase(databaseName, 3).apply {
            execSQL(
                """
                INSERT INTO persons (
                    id, display_name, created_at, updated_at
                ) VALUES ('person-v3', 'سجل قبل المستندات', 1, 1)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO debts (
                    id, person_id, direction, original_amount_minor, currency_code,
                    opened_at, lifecycle_state, created_at, updated_at
                ) VALUES ('debt-v3', 'person-v3', 'RECEIVABLE', 12000, 'YER',
                    1, 'ACTIVE', 1, 1)
                """.trimIndent(),
            )
            close()
        }

        migrationHelper.runMigrationsAndValidate(
            databaseName,
            6,
            true,
            WaslDatabase.MIGRATION_3_4,
            WaslDatabase.MIGRATION_4_5,
            WaslDatabase.MIGRATION_5_6,
        ).use { migrated ->
            migrated.query("SELECT original_amount_minor FROM debts WHERE id = 'debt-v3'").use {
                check(it.moveToFirst())
                assertEquals(12000L, it.getLong(0))
            }
            assertEmpty(migrated, "document_identities")
            assertEmpty(migrated, "issued_documents")
            assertEmpty(migrated, "payment_promises")
            assertEmpty(migrated, "installment_plans")
            assertEmpty(migrated, "installments")
        }

        context.deleteDatabase(databaseName)
    }

    private fun assertEmpty(database: androidx.sqlite.db.SupportSQLiteDatabase, table: String) {
        database.query("SELECT COUNT(*) FROM $table").use {
            check(it.moveToFirst())
            assertEquals(0L, it.getLong(0))
        }
    }
}
