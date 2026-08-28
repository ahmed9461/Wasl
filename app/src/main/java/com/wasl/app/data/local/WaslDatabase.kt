package com.wasl.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import com.wasl.app.data.local.dao.AttachmentDao
import com.wasl.app.data.local.dao.AuditEventDao
import com.wasl.app.data.local.dao.DebtDao
import com.wasl.app.data.local.dao.DocumentIdentityDao
import com.wasl.app.data.local.dao.GroupExpenseDao
import com.wasl.app.data.local.dao.InstallmentPlanDao
import com.wasl.app.data.local.dao.IssuedDocumentDao
import com.wasl.app.data.local.dao.LedgerDao
import com.wasl.app.data.local.dao.PaymentClaimDao
import com.wasl.app.data.local.dao.PaymentPromiseDao
import com.wasl.app.data.local.dao.PersonDao
import com.wasl.app.data.local.dao.ReminderDao
import com.wasl.app.data.local.entity.AttachmentEntity
import com.wasl.app.data.local.entity.AuditEventEntity
import com.wasl.app.data.local.entity.DebtEntity
import com.wasl.app.data.local.entity.DocumentIdentityEntity
import com.wasl.app.data.local.entity.GroupExpenseEntity
import com.wasl.app.data.local.entity.GroupExpenseShareEntity
import com.wasl.app.data.local.entity.InstallmentEntity
import com.wasl.app.data.local.entity.InstallmentPlanEntity
import com.wasl.app.data.local.entity.IssuedDocumentEntity
import com.wasl.app.data.local.entity.LedgerEntryEntity
import com.wasl.app.data.local.entity.PaymentClaimEntity
import com.wasl.app.data.local.entity.PaymentIssuedDocumentView
import com.wasl.app.data.local.entity.PaymentPromiseEntity
import com.wasl.app.data.local.entity.PersonEntity
import com.wasl.app.data.local.entity.ReminderEntity

@Database(
    entities = [
        PersonEntity::class,
        DebtEntity::class,
        LedgerEntryEntity::class,
        ReminderEntity::class,
        AuditEventEntity::class,
        DocumentIdentityEntity::class,
        IssuedDocumentEntity::class,
        PaymentPromiseEntity::class,
        InstallmentPlanEntity::class,
        InstallmentEntity::class,
        PaymentClaimEntity::class,
        AttachmentEntity::class,
        GroupExpenseEntity::class,
        GroupExpenseShareEntity::class,
    ],
    views = [PaymentIssuedDocumentView::class],
    version = 10,
    exportSchema = true,
)
abstract class WaslDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun debtDao(): DebtDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun reminderDao(): ReminderDao
    abstract fun auditEventDao(): AuditEventDao
    abstract fun documentIdentityDao(): DocumentIdentityDao
    abstract fun issuedDocumentDao(): IssuedDocumentDao
    abstract fun paymentPromiseDao(): PaymentPromiseDao
    abstract fun installmentPlanDao(): InstallmentPlanDao
    abstract fun paymentClaimDao(): PaymentClaimDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun groupExpenseDao(): GroupExpenseDao

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
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_reminders_subject_type_subject_id_reminder_type` ON `reminders` (`subject_type`, `subject_id`, `reminder_type`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_status_trigger_at` ON `reminders` (`status`, `trigger_at`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_subject_id` ON `reminders` (`subject_id`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_reminders_platform_request_code` ON `reminders` (`platform_request_code`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `audit_events` (
                        `id` TEXT NOT NULL,
                        `command_id` TEXT NOT NULL,
                        `aggregate_type` TEXT NOT NULL,
                        `aggregate_id` TEXT NOT NULL,
                        `event_type` TEXT NOT NULL,
                        `occurred_at` INTEGER NOT NULL,
                        `actor` TEXT NOT NULL,
                        `before_snapshot` TEXT,
                        `after_snapshot` TEXT,
                        `reason` TEXT,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_audit_events_command_id` ON `audit_events` (`command_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_events_aggregate_id_aggregate_type_occurred_at` ON `audit_events` (`aggregate_id`, `aggregate_type`, `occurred_at`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `document_identities` (
                        `id` TEXT NOT NULL,
                        `display_name` TEXT NOT NULL,
                        `activity_name` TEXT,
                        `phone` TEXT,
                        `footer_text` TEXT,
                        `is_default` INTEGER NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_document_identities_is_default` ON `document_identities` (`is_default`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `issued_documents` (
                        `id` TEXT NOT NULL,
                        `command_id` TEXT NOT NULL,
                        `document_type` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `document_number` TEXT NOT NULL,
                        `issue_year` INTEGER NOT NULL,
                        `sequence_number` INTEGER NOT NULL,
                        `debt_id` TEXT NOT NULL,
                        `ledger_entry_id` TEXT NOT NULL,
                        `identity_id` TEXT NOT NULL,
                        `person_id` TEXT NOT NULL,
                        `person_name_snapshot` TEXT NOT NULL,
                        `amount_minor` INTEGER NOT NULL,
                        `currency_code` TEXT NOT NULL,
                        `issued_at` INTEGER NOT NULL,
                        `snapshot_version` INTEGER NOT NULL,
                        `snapshot_json` TEXT NOT NULL,
                        `pdf_relative_path` TEXT NOT NULL,
                        `pdf_sha256` TEXT,
                        `page_count` INTEGER,
                        `failure_code` TEXT,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`debt_id`) REFERENCES `debts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(`ledger_entry_id`) REFERENCES `ledger_entries`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(`identity_id`) REFERENCES `document_identities`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent(),
                )
                createIssuedDocumentIndexes(db, includeLedgerIndex = false)
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `payment_promises` (
                        `id` TEXT NOT NULL,
                        `create_command_id` TEXT NOT NULL,
                        `debt_id` TEXT NOT NULL,
                        `promised_date_epoch_day` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `note` TEXT,
                        `created_at` INTEGER NOT NULL,
                        `resolution_command_id` TEXT,
                        `resolved_at` INTEGER,
                        `resolution_note` TEXT,
                        `updated_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`debt_id`) REFERENCES `debts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_payment_promises_create_command_id` ON `payment_promises` (`create_command_id`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_payment_promises_resolution_command_id` ON `payment_promises` (`resolution_command_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_payment_promises_debt_id_promised_date_epoch_day` ON `payment_promises` (`debt_id`, `promised_date_epoch_day`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_payment_promises_debt_id_status` ON `payment_promises` (`debt_id`, `status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_payment_promises_status_promised_date_epoch_day` ON `payment_promises` (`status`, `promised_date_epoch_day`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_issued_documents_ledger_entry_id` ON `issued_documents` (`ledger_entry_id`)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `installment_plans` (
                        `id` TEXT NOT NULL,
                        `command_id` TEXT NOT NULL,
                        `debt_id` TEXT NOT NULL,
                        `revision_number` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `supersedes_plan_id` TEXT,
                        `superseded_at` INTEGER,
                        `superseded_after_sequence` INTEGER,
                        `reason` TEXT,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`debt_id`) REFERENCES `debts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_installment_plans_command_id` ON `installment_plans` (`command_id`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_installment_plans_debt_id_revision_number` ON `installment_plans` (`debt_id`, `revision_number`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_installment_plans_debt_id_status` ON `installment_plans` (`debt_id`, `status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_installment_plans_supersedes_plan_id` ON `installment_plans` (`supersedes_plan_id`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `installments` (
                        `id` TEXT NOT NULL,
                        `plan_id` TEXT NOT NULL,
                        `debt_id` TEXT NOT NULL,
                        `sequence_number` INTEGER NOT NULL,
                        `due_date_epoch_day` INTEGER NOT NULL,
                        `amount_minor` INTEGER NOT NULL,
                        `currency_code` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`plan_id`) REFERENCES `installment_plans`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(`debt_id`) REFERENCES `debts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_installments_plan_id_sequence_number` ON `installments` (`plan_id`, `sequence_number`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_installments_plan_id_due_date_epoch_day` ON `installments` (`plan_id`, `due_date_epoch_day`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_installments_debt_id_due_date_epoch_day` ON `installments` (`debt_id`, `due_date_epoch_day`)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("DROP VIEW IF EXISTS `payment_issued_documents`")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `issued_documents_new` (
                        `id` TEXT NOT NULL,
                        `command_id` TEXT NOT NULL,
                        `document_type` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `document_number` TEXT NOT NULL,
                        `issue_year` INTEGER NOT NULL,
                        `sequence_number` INTEGER NOT NULL,
                        `debt_id` TEXT NOT NULL,
                        `ledger_entry_id` TEXT,
                        `identity_id` TEXT NOT NULL,
                        `person_id` TEXT NOT NULL,
                        `person_name_snapshot` TEXT NOT NULL,
                        `amount_minor` INTEGER NOT NULL,
                        `currency_code` TEXT NOT NULL,
                        `issued_at` INTEGER NOT NULL,
                        `snapshot_version` INTEGER NOT NULL,
                        `snapshot_json` TEXT NOT NULL,
                        `pdf_relative_path` TEXT NOT NULL,
                        `pdf_sha256` TEXT,
                        `page_count` INTEGER,
                        `failure_code` TEXT,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`debt_id`) REFERENCES `debts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(`ledger_entry_id`) REFERENCES `ledger_entries`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(`identity_id`) REFERENCES `document_identities`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO `issued_documents_new` (
                        `id`, `command_id`, `document_type`, `status`, `document_number`,
                        `issue_year`, `sequence_number`, `debt_id`, `ledger_entry_id`, `identity_id`,
                        `person_id`, `person_name_snapshot`, `amount_minor`, `currency_code`,
                        `issued_at`, `snapshot_version`, `snapshot_json`, `pdf_relative_path`,
                        `pdf_sha256`, `page_count`, `failure_code`, `created_at`, `updated_at`
                    )
                    SELECT
                        `id`, `command_id`, `document_type`, `status`, `document_number`,
                        `issue_year`, `sequence_number`, `debt_id`, `ledger_entry_id`, `identity_id`,
                        `person_id`, `person_name_snapshot`, `amount_minor`, `currency_code`,
                        `issued_at`, `snapshot_version`, `snapshot_json`, `pdf_relative_path`,
                        `pdf_sha256`, `page_count`, `failure_code`, `created_at`, `updated_at`
                    FROM `issued_documents`
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE `issued_documents`")
                db.execSQL("ALTER TABLE `issued_documents_new` RENAME TO `issued_documents`")
                createIssuedDocumentIndexes(db, includeLedgerIndex = true)
                db.execSQL(
                    "CREATE VIEW IF NOT EXISTS `payment_issued_documents` AS SELECT * FROM issued_documents WHERE document_type = 'PAYMENT_RECEIPT'",
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `payment_claims` (
                        `id` TEXT NOT NULL,
                        `create_command_id` TEXT NOT NULL,
                        `debt_id` TEXT NOT NULL,
                        `claimed_at` INTEGER NOT NULL,
                        `follow_up_kind` TEXT NOT NULL,
                        `follow_up_date_epoch_day` INTEGER,
                        `note` TEXT,
                        `status` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `resolution_command_id` TEXT,
                        `resolved_at` INTEGER,
                        `resolution_note` TEXT,
                        `updated_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`debt_id`) REFERENCES `debts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_payment_claims_create_command_id` ON `payment_claims` (`create_command_id`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_payment_claims_resolution_command_id` ON `payment_claims` (`resolution_command_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_payment_claims_debt_id_claimed_at` ON `payment_claims` (`debt_id`, `claimed_at`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_payment_claims_debt_id_status` ON `payment_claims` (`debt_id`, `status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_payment_claims_status_follow_up_date_epoch_day` ON `payment_claims` (`status`, `follow_up_date_epoch_day`)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `attachments` (
                        `id` TEXT NOT NULL,
                        `debt_id` TEXT NOT NULL,
                        `ledger_entry_id` TEXT,
                        `display_name` TEXT NOT NULL,
                        `mime_type` TEXT NOT NULL,
                        `size_bytes` INTEGER NOT NULL,
                        `relative_path` TEXT NOT NULL,
                        `sha256` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `note` TEXT,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`debt_id`) REFERENCES `debts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(`ledger_entry_id`) REFERENCES `ledger_entries`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_debt_id_created_at` ON `attachments` (`debt_id`, `created_at`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_ledger_entry_id` ON `attachments` (`ledger_entry_id`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_attachments_relative_path` ON `attachments` (`relative_path`)")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `group_expenses` (
                        `id` TEXT NOT NULL,
                        `command_id` TEXT NOT NULL,
                        `direction` TEXT NOT NULL,
                        `total_amount_minor` INTEGER NOT NULL,
                        `currency_code` TEXT NOT NULL,
                        `occurred_at` INTEGER NOT NULL,
                        `description` TEXT NOT NULL,
                        `notes` TEXT,
                        `created_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_group_expenses_command_id` ON `group_expenses` (`command_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_expenses_occurred_at` ON `group_expenses` (`occurred_at`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `group_expense_shares` (
                        `id` TEXT NOT NULL,
                        `group_expense_id` TEXT NOT NULL,
                        `debt_id` TEXT NOT NULL,
                        `person_id` TEXT NOT NULL,
                        `amount_minor` INTEGER NOT NULL,
                        `sequence_number` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`group_expense_id`) REFERENCES `group_expenses`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(`debt_id`) REFERENCES `debts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(`person_id`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_group_expense_shares_group_expense_id_sequence_number` ON `group_expense_shares` (`group_expense_id`, `sequence_number`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_group_expense_shares_group_expense_id_person_id` ON `group_expense_shares` (`group_expense_id`, `person_id`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_group_expense_shares_debt_id` ON `group_expense_shares` (`debt_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_expense_shares_person_id` ON `group_expense_shares` (`person_id`)")
            }
        }

        val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
        )

        private fun createIssuedDocumentIndexes(
            db: androidx.sqlite.db.SupportSQLiteDatabase,
            includeLedgerIndex: Boolean,
        ) {
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_issued_documents_command_id` ON `issued_documents` (`command_id`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_issued_documents_document_number` ON `issued_documents` (`document_number`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_issued_documents_document_type_ledger_entry_id` ON `issued_documents` (`document_type`, `ledger_entry_id`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_issued_documents_issue_year_sequence_number` ON `issued_documents` (`issue_year`, `sequence_number`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_issued_documents_debt_id_issued_at` ON `issued_documents` (`debt_id`, `issued_at`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_issued_documents_identity_id` ON `issued_documents` (`identity_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_issued_documents_person_id` ON `issued_documents` (`person_id`)")
            if (includeLedgerIndex) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_issued_documents_ledger_entry_id` ON `issued_documents` (`ledger_entry_id`)")
            }
        }

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
