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
class PaymentPromiseMigrationInstrumentedTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WaslDatabase::class.java,
    )

    @Test
    fun versionFourMigratesToFiveWithoutChangingExistingDebtData() {
        val databaseName = "wasl-schema-v4-promises.db"
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(databaseName)

        migrationHelper.createDatabase(databaseName, 4).apply {
            execSQL(
                """
                INSERT INTO persons (
                    id, display_name, created_at, updated_at
                ) VALUES ('person-v4', 'قبل الوعود', 1, 1)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO debts (
                    id, person_id, direction, original_amount_minor, currency_code,
                    opened_at, due_date_epoch_day, lifecycle_state, created_at, updated_at
                ) VALUES ('debt-v4', 'person-v4', 'RECEIVABLE', 25000, 'YER',
                    1, 20690, 'ACTIVE', 1, 1)
                """.trimIndent(),
            )
            close()
        }

        migrationHelper.runMigrationsAndValidate(
            databaseName,
            5,
            true,
            WaslDatabase.MIGRATION_4_5,
        ).use { migrated ->
            migrated.query(
                "SELECT original_amount_minor, due_date_epoch_day FROM debts WHERE id = 'debt-v4'",
            ).use {
                check(it.moveToFirst())
                assertEquals(25000L, it.getLong(0))
                assertEquals(20690L, it.getLong(1))
            }
            migrated.query("SELECT COUNT(*) FROM payment_promises").use {
                check(it.moveToFirst())
                assertEquals(0L, it.getLong(0))
            }
            migrated.query(
                """
                SELECT COUNT(*) FROM sqlite_master
                WHERE type = 'index' AND name = 'index_issued_documents_ledger_entry_id'
                """.trimIndent(),
            ).use {
                check(it.moveToFirst())
                assertEquals(1L, it.getLong(0))
            }
        }

        context.deleteDatabase(databaseName)
    }
}
