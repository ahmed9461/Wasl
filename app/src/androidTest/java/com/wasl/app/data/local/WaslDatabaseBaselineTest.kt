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
    fun versionOneMigratesToVersionThreeWithoutLosingDebtData() {
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
            3,
            true,
            WaslDatabase.MIGRATION_1_2,
            WaslDatabase.MIGRATION_2_3,
        ).use { migrated ->
            migrated.query("SELECT original_amount_minor, due_date_epoch_day FROM debts").use {
                check(it.moveToFirst())
                assertEquals(5000L, it.getLong(0))
                assertEquals(20680L, it.getLong(1))
            }
            migrated.query("SELECT COUNT(*) FROM reminders").use {
                check(it.moveToFirst())
                assertEquals(0L, it.getLong(0))
            }
            migrated.query("SELECT COUNT(*) FROM audit_events").use {
                check(it.moveToFirst())
                assertEquals(0L, it.getLong(0))
            }
        }

        val database = Room.databaseBuilder(context, WaslDatabase::class.java, databaseName)
            .addMigrations(*WaslDatabase.ALL_MIGRATIONS)
            .build()
        database.openHelper.writableDatabase
        database.close()

        context.deleteDatabase(databaseName)
    }

    @Test
    fun versionTwoMigratesToVersionThreeWithoutLosingReminderData() {
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
            3,
            true,
            WaslDatabase.MIGRATION_2_3,
        ).use { migrated ->
            migrated.query("SELECT id, status FROM reminders").use {
                check(it.moveToFirst())
                assertEquals("reminder-v2", it.getString(0))
                assertEquals("SCHEDULED", it.getString(1))
            }
            migrated.query("SELECT COUNT(*) FROM audit_events").use {
                check(it.moveToFirst())
                assertEquals(0L, it.getLong(0))
            }
        }

        context.deleteDatabase(databaseName)
    }
}
