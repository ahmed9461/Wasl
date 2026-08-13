package com.wasl.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import com.wasl.app.data.local.dao.DebtDao
import com.wasl.app.data.local.dao.LedgerDao
import com.wasl.app.data.local.dao.PersonDao
import com.wasl.app.data.local.dao.ReminderDao
import com.wasl.app.data.local.entity.DebtEntity
import com.wasl.app.data.local.entity.LedgerEntryEntity
import com.wasl.app.data.local.entity.PersonEntity
import com.wasl.app.data.local.entity.ReminderEntity

@Database(
    entities = [
        PersonEntity::class,
        DebtEntity::class,
        LedgerEntryEntity::class,
        ReminderEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class WaslDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao

    abstract fun debtDao(): DebtDao

    abstract fun ledgerDao(): LedgerDao

    abstract fun reminderDao(): ReminderDao

    companion object {
        const val DATABASE_NAME = "wasl.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `reminders` (
                        `id` TEXT NOT NULL,
                        `subject_type` TEXT NOT NULL,
                        `subject_id` TEXT NOT NULL,
                        `reminder_type` TEXT NOT NULL,
                        `schedule_type` TEXT NOT NULL,
                        `trigger_at` INTEGER NOT NULL,
                        `zone_id` TEXT NOT NULL,
                        `repeat_rule` TEXT,
                        `status` TEXT NOT NULL,
                        `platform_request_code` INTEGER,
                        `last_failure_code` TEXT,
                        `delivered_at` INTEGER,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_reminders_subject_type_subject_id_reminder_type` ON `reminders` (`subject_type`, `subject_id`, `reminder_type`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_reminders_status_trigger_at` ON `reminders` (`status`, `trigger_at`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_reminders_subject_id` ON `reminders` (`subject_id`)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_reminders_platform_request_code` ON `reminders` (`platform_request_code`)",
                )
            }
        }

        val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

        fun create(context: Context): WaslDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                WaslDatabase::class.java,
                DATABASE_NAME,
            )
                .addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}
