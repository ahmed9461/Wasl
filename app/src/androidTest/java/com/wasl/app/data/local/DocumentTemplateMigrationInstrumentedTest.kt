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
class DocumentTemplateMigrationInstrumentedTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WaslDatabase::class.java,
    )

    @Test
    fun versionTenMigratesToElevenWithFiveBuiltInTemplatesAndOneDefault() {
        val databaseName = "wasl-schema-v10-document-templates.db"
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(databaseName)
        migrationHelper.createDatabase(databaseName, 10).close()

        migrationHelper.runMigrationsAndValidate(
            databaseName,
            11,
            true,
            WaslDatabase.MIGRATION_10_11,
        ).use { migrated ->
            migrated.query("SELECT COUNT(*) FROM document_templates").use {
                check(it.moveToFirst())
                assertEquals(5L, it.getLong(0))
            }
            migrated.query("SELECT id, style FROM document_templates WHERE is_default = 1").use {
                check(it.moveToFirst())
                assertEquals("builtin-business", it.getString(0))
                assertEquals("BUSINESS", it.getString(1))
                assertEquals(false, it.moveToNext())
            }
            assertIndex(migrated, "index_document_templates_is_default")
            assertIndex(migrated, "index_document_templates_style")
        }
        context.deleteDatabase(databaseName)
    }

    private fun assertIndex(database: androidx.sqlite.db.SupportSQLiteDatabase, name: String) {
        database.query(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = ?",
            arrayOf(name),
        ).use {
            check(it.moveToFirst())
            assertEquals(1L, it.getLong(0), "Missing index $name")
        }
    }
}
