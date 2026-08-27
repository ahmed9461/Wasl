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
class PaymentClaimMigrationInstrumentedTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WaslDatabase::class.java,
    )

    @Test
    fun versionSevenMigratesToEightWithoutChangingExistingDebtData() {
        val databaseName = "wasl-schema-v7-claims.db"
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(databaseName)

        migrationHelper.createDatabase(databaseName, 7).apply {
            execSQL(
                """
                INSERT INTO persons (
                    id, display_name, created_at, updated_at
                ) VALUES ('person-v7', 'قبل المطالبات', 1, 1)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO debts (
                    id, person_id, direction, original_amount_minor, currency_code,
                    opened_at, due_date_epoch_day, lifecycle_state, created_at, updated_at
                ) VALUES ('debt-v7', 'person-v7', 'PAYABLE', 55000, 'YER',
                    1, 20700, 'ACTIVE', 1, 1)
                """.trimIndent(),
            )
            close()
        }

        migrationHelper.runMigrationsAndValidate(
            databaseName,
            8,
            true,
            WaslDatabase.MIGRATION_7_8,
        ).use { migrated ->
            migrated.query(
                "SELECT direction, original_amount_minor, due_date_epoch_day FROM debts WHERE id = 'debt-v7'",
            ).use {
                check(it.moveToFirst())
                assertEquals("PAYABLE", it.getString(0))
                assertEquals(55000L, it.getLong(1))
                assertEquals(20700L, it.getLong(2))
            }
            migrated.query("SELECT COUNT(*) FROM payment_claims").use {
                check(it.moveToFirst())
                assertEquals(0L, it.getLong(0))
            }
            migrated.query(
                """
                SELECT COUNT(*) FROM sqlite_master
                WHERE type = 'index' AND name = 'index_payment_claims_create_command_id'
                """.trimIndent(),
            ).use {
                check(it.moveToFirst())
                assertEquals(1L, it.getLong(0))
            }
            migrated.query(
                """
                SELECT COUNT(*) FROM sqlite_master
                WHERE type = 'index' AND name = 'index_payment_claims_status_follow_up_date_epoch_day'
                """.trimIndent(),
            ).use {
                check(it.moveToFirst())
                assertEquals(1L, it.getLong(0))
            }
        }

        context.deleteDatabase(databaseName)
    }
}
