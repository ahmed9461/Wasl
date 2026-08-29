package com.wasl.app.data.local

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentIdentityBannerMigrationInstrumentedTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WaslDatabase::class.java,
    )

    @Test
    fun versionElevenMigratesToTwelvePreservingIdentityAndAddingNullableBannerColumns() {
        val databaseName = "wasl-schema-v11-document-banner.db"
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(databaseName)

        migrationHelper.createDatabase(databaseName, 11).use { database ->
            database.execSQL(
                """
                INSERT INTO document_identities (
                    id, display_name, activity_name, phone, footer_text,
                    is_default, created_at, updated_at
                ) VALUES (
                    'identity-1', 'AL NOOR TRADING', 'Trading', '+967700000000', 'Footer',
                    1, 1000, 2000
                )
                """.trimIndent(),
            )
        }

        migrationHelper.runMigrationsAndValidate(
            databaseName,
            12,
            true,
            WaslDatabase.MIGRATION_11_12,
        ).use { migrated ->
            migrated.query(
                """
                SELECT display_name, activity_name, phone, footer_text,
                       is_default, created_at, updated_at,
                       banner_relative_path, banner_sha256
                FROM document_identities
                WHERE id = 'identity-1'
                """.trimIndent(),
            ).use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("AL NOOR TRADING", cursor.getString(0))
                assertEquals("Trading", cursor.getString(1))
                assertEquals("+967700000000", cursor.getString(2))
                assertEquals("Footer", cursor.getString(3))
                assertEquals(1L, cursor.getLong(4))
                assertEquals(1000L, cursor.getLong(5))
                assertEquals(2000L, cursor.getLong(6))
                assertNull(cursor.getString(7))
                assertNull(cursor.getString(8))
                assertEquals(false, cursor.moveToNext())
            }
        }

        context.deleteDatabase(databaseName)
    }
}
