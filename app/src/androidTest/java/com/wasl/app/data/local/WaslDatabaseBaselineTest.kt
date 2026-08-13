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
    fun versionOneMigratesToVersionTwoWithoutLosingDebtData() {
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
            2,
            true,
            WaslDatabase.MIGRATION_1_2,
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
        }

        val database = Room.databaseBuilder(context, WaslDatabase::class.java, databaseName)
            .addMigrations(*WaslDatabase.ALL_MIGRATIONS)
            .build()
        database.openHelper.writableDatabase
        database.close()

        context.deleteDatabase(databaseName)
    }
}
