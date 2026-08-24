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
class InstallmentPlanMigrationInstrumentedTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WaslDatabase::class.java,
    )

    @Test
    fun versionFiveMigratesToSixWithoutChangingExistingDebtData() {
        val databaseName = "wasl-schema-v5-installments.db"
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(databaseName)

        migrationHelper.createDatabase(databaseName, 5).apply {
            execSQL(
                """
                INSERT INTO persons (
                    id, display_name, created_at, updated_at
                ) VALUES ('person-v5', 'قبل الأقساط', 1, 1)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO debts (
                    id, person_id, direction, original_amount_minor, currency_code,
                    opened_at, due_date_epoch_day, lifecycle_state, created_at, updated_at
                ) VALUES ('debt-v5', 'person-v5', 'RECEIVABLE', 120000, 'YER',
                    1, 20700, 'ACTIVE', 1, 1)
                """.trimIndent(),
            )
            close()
        }

        migrationHelper.runMigrationsAndValidate(
            databaseName,
            6,
            true,
            WaslDatabase.MIGRATION_5_6,
        ).use { migrated ->
            migrated.query(
                "SELECT original_amount_minor, due_date_epoch_day FROM debts WHERE id = 'debt-v5'",
            ).use {
                check(it.moveToFirst())
                assertEquals(120000L, it.getLong(0))
                assertEquals(20700L, it.getLong(1))
            }
            migrated.query("SELECT COUNT(*) FROM installment_plans").use {
                check(it.moveToFirst())
                assertEquals(0L, it.getLong(0))
            }
            migrated.query("SELECT COUNT(*) FROM installments").use {
                check(it.moveToFirst())
                assertEquals(0L, it.getLong(0))
            }
        }

        context.deleteDatabase(databaseName)
    }
}
