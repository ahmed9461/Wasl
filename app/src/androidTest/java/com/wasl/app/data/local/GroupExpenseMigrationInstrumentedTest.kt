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
class GroupExpenseMigrationInstrumentedTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WaslDatabase::class.java,
    )

    @Test
    fun versionNineMigratesToTenWithoutChangingExistingDebtData() {
        val databaseName = "wasl-schema-v9-group-expenses.db"
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(databaseName)

        migrationHelper.createDatabase(databaseName, 9).apply {
            execSQL(
                """
                INSERT INTO persons (
                    id, display_name, created_at, updated_at
                ) VALUES ('person-v9', 'قبل الجماعي', 1, 1)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO debts (
                    id, person_id, direction, original_amount_minor, currency_code,
                    opened_at, due_date_epoch_day, lifecycle_state, created_at, updated_at
                ) VALUES ('debt-v9', 'person-v9', 'RECEIVABLE', 42000, 'YER',
                    1, 20700, 'ACTIVE', 1, 1)
                """.trimIndent(),
            )
            close()
        }

        migrationHelper.runMigrationsAndValidate(
            databaseName,
            10,
            true,
            WaslDatabase.MIGRATION_9_10,
        ).use { migrated ->
            migrated.query(
                "SELECT original_amount_minor, currency_code FROM debts WHERE id = 'debt-v9'",
            ).use {
                check(it.moveToFirst())
                assertEquals(42_000L, it.getLong(0))
                assertEquals("YER", it.getString(1))
            }
            migrated.query("SELECT COUNT(*) FROM group_expenses").use {
                check(it.moveToFirst())
                assertEquals(0L, it.getLong(0))
            }
            migrated.query("SELECT COUNT(*) FROM group_expense_shares").use {
                check(it.moveToFirst())
                assertEquals(0L, it.getLong(0))
            }
            assertIndex(migrated, "index_group_expenses_command_id")
            assertIndex(migrated, "index_group_expense_shares_group_expense_id_sequence_number")
            assertIndex(migrated, "index_group_expense_shares_group_expense_id_person_id")
            assertIndex(migrated, "index_group_expense_shares_debt_id")
        }

        context.deleteDatabase(databaseName)
    }

    private fun assertIndex(
        database: androidx.sqlite.db.SupportSQLiteDatabase,
        name: String,
    ) {
        database.query(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = ?",
            arrayOf(name),
        ).use {
            check(it.moveToFirst())
            assertEquals(1L, it.getLong(0), "Missing index $name")
        }
    }
}
