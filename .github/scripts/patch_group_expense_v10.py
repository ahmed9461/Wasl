from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    source = path.read_text(encoding="utf-8")
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"{path}: {label}: expected 1 match, got {count}")
    path.write_text(source.replace(old, new, 1), encoding="utf-8")


repo = Path("app/src/main/java/com/wasl/app/data/WaslRepository.kt")
replace_once(
    repo,
    "import com.wasl.domain.DebtLedger\n",
    "import com.wasl.domain.DebtLedger\nimport com.wasl.domain.GroupExpenseId\n",
    "repository group id import",
)
replace_once(
    repo,
    "    fun observeAccount(debtId: DebtId): Flow<AccountOverview?>\n\n",
    "    fun observeAccount(debtId: DebtId): Flow<AccountOverview?>\n\n"
    "    fun observeGroupExpenses(): Flow<List<GroupExpenseRecord>>\n\n",
    "repository observe group expenses",
)
replace_once(
    repo,
    "    suspend fun createDebtForExistingPerson(\n        command: CreateDebtForExistingPersonCommand,\n    ): AccountOverview\n\n",
    "    suspend fun createDebtForExistingPerson(\n        command: CreateDebtForExistingPersonCommand,\n    ): AccountOverview\n\n"
    "    suspend fun createGroupExpense(command: CreateGroupExpenseCommand): GroupExpenseRecord\n\n",
    "repository create group expense",
)
replace_once(
    repo,
    "    suspend fun getAccount(debtId: DebtId): AccountOverview?\n\n",
    "    suspend fun getAccount(debtId: DebtId): AccountOverview?\n\n"
    "    suspend fun getGroupExpense(groupExpenseId: GroupExpenseId): GroupExpenseRecord?\n\n",
    "repository get group expense",
)

room = Path("app/src/main/java/com/wasl/app/data/local/RoomWaslRepository.kt")
replace_once(
    room,
    "import com.wasl.app.data.CreateDebtForExistingPersonCommand\n",
    "import com.wasl.app.data.CreateDebtForExistingPersonCommand\n"
    "import com.wasl.app.data.CreateGroupExpenseCommand\n"
    "import com.wasl.app.data.GroupExpenseRecord\n",
    "room group app imports",
)
replace_once(
    room,
    "import com.wasl.app.data.local.entity.DebtEntity\n",
    "import com.wasl.app.data.local.entity.DebtEntity\n"
    "import com.wasl.app.data.local.entity.GroupExpenseAggregate\n"
    "import com.wasl.app.data.local.entity.GroupExpenseEntity\n"
    "import com.wasl.app.data.local.entity.GroupExpenseShareEntity\n",
    "room group entity imports",
)
replace_once(
    room,
    "import com.wasl.domain.DebtState\n",
    "import com.wasl.domain.DebtState\n"
    "import com.wasl.domain.GroupExpense\n"
    "import com.wasl.domain.GroupExpenseId\n"
    "import com.wasl.domain.GroupExpenseShare\n"
    "import com.wasl.domain.GroupExpenseShareId\n",
    "room group domain imports",
)
replace_once(
    room,
    "    private val issuedDocumentDao = database.issuedDocumentDao()\n",
    "    private val issuedDocumentDao = database.issuedDocumentDao()\n"
    "    private val groupExpenseDao = database.groupExpenseDao()\n",
    "room group dao",
)
replace_once(
    room,
    "    override fun observeAccount(debtId: DebtId): Flow<AccountOverview?> =\n"
    "        debtDao.observeAggregateById(debtId.value).map { aggregate ->\n"
    "            aggregate?.let(::toAccountOverview)\n"
    "        }\n\n",
    "    override fun observeAccount(debtId: DebtId): Flow<AccountOverview?> =\n"
    "        debtDao.observeAggregateById(debtId.value).map { aggregate ->\n"
    "            aggregate?.let(::toAccountOverview)\n"
    "        }\n\n"
    "    override fun observeGroupExpenses(): Flow<List<GroupExpenseRecord>> =\n"
    "        groupExpenseDao.observeAggregates().map { aggregates ->\n"
    "            aggregates.map(::toGroupExpenseRecord)\n"
    "        }\n\n",
    "room observe groups",
)

create_group = r'''    override suspend fun createGroupExpense(
        command: CreateGroupExpenseCommand,
    ): GroupExpenseRecord = database.withTransaction {
        val normalizedExpense = command.expense.copy(
            description = command.expense.description.trim(),
            notes = command.expense.notes?.trim(),
        )
        val normalizedCommand = command.copy(expense = normalizedExpense)

        groupExpenseDao.findAggregateByCommandId(command.commandId)?.let { existing ->
            validateGroupExpenseReplay(normalizedCommand, existing)
            return@withTransaction toGroupExpenseRecord(existing)
        }
        if (groupExpenseDao.findAggregateById(normalizedExpense.id.value) != null) {
            throw CommandConflictException(
                "Group expense ID ${normalizedExpense.id.value} is already used by another command.",
            )
        }

        normalizedExpense.shares.forEach { share ->
            val person = personDao.findById(share.personId.value)
                ?: throw RecordNotFoundException("Person ${share.personId.value} was not found.")
            if (person.archivedAt != null) {
                throw RecordNotFoundException("Person ${share.personId.value} is archived.")
            }
            if (debtDao.findAggregateById(share.debtId.value) != null) {
                throw CommandConflictException(
                    "Debt ID ${share.debtId.value} is already used by another command.",
                )
            }
            if (groupExpenseDao.findShareById(share.id.value) != null) {
                throw CommandConflictException(
                    "Group expense share ID ${share.id.value} is already used.",
                )
            }
        }

        groupExpenseDao.insertGroupExpense(
            GroupExpenseEntity(
                id = normalizedExpense.id.value,
                commandId = command.commandId,
                direction = normalizedExpense.direction.name,
                totalAmountMinor = normalizedExpense.totalAmount.minorUnits,
                currencyCode = normalizedExpense.totalAmount.currency.value,
                occurredAt = normalizedExpense.occurredAt.toEpochMilli(),
                description = normalizedExpense.description,
                notes = normalizedExpense.notes,
                createdAt = command.createdAt.toEpochMilli(),
            ),
        )
        normalizedExpense.shares.forEachIndexed { index, share ->
            insertDebtWithReminder(
                DebtCreation(
                    personId = share.personId,
                    debtId = share.debtId,
                    direction = normalizedExpense.direction,
                    originalAmount = share.amount,
                    openedAt = normalizedExpense.occurredAt,
                    createdAt = command.createdAt,
                    dueDate = null,
                    description = normalizedExpense.description,
                    debtNotes = null,
                    dueReminder = null,
                    strongAlarm = null,
                ),
            )
            groupExpenseDao.insertShare(
                GroupExpenseShareEntity(
                    id = share.id.value,
                    groupExpenseId = normalizedExpense.id.value,
                    debtId = share.debtId.value,
                    personId = share.personId.value,
                    amountMinor = share.amount.minorUnits,
                    sequenceNumber = index + 1,
                ),
            )
        }

        toGroupExpenseRecord(
            requireNotNull(groupExpenseDao.findAggregateById(normalizedExpense.id.value)) {
                "Created group expense could not be read back."
            },
        )
    }

'''
replace_once(
    room,
    "    override suspend fun getAccount(debtId: DebtId): AccountOverview? =\n"
    "        debtDao.findAggregateById(debtId.value)?.let(::toAccountOverview)\n\n",
    create_group +
    "    override suspend fun getAccount(debtId: DebtId): AccountOverview? =\n"
    "        debtDao.findAggregateById(debtId.value)?.let(::toAccountOverview)\n\n"
    "    override suspend fun getGroupExpense(groupExpenseId: GroupExpenseId): GroupExpenseRecord? =\n"
    "        groupExpenseDao.findAggregateById(groupExpenseId.value)?.let(::toGroupExpenseRecord)\n\n",
    "room create/get groups",
)

helpers = r'''    private fun toGroupExpenseRecord(aggregate: GroupExpenseAggregate): GroupExpenseRecord {
        val entity = aggregate.groupExpense
        val orderedShares = aggregate.shares.sortedBy { it.sequenceNumber }
        orderedShares.forEachIndexed { index, share ->
            check(share.sequenceNumber == index + 1) {
                "Group expense share sequence contains a gap or duplicate."
            }
        }
        return GroupExpenseRecord(
            commandId = entity.commandId,
            expense = GroupExpense(
                id = GroupExpenseId(entity.id),
                direction = DebtDirection.valueOf(entity.direction),
                totalAmount = Money(
                    minorUnits = entity.totalAmountMinor,
                    currency = CurrencyCode.of(entity.currencyCode),
                ),
                occurredAt = Instant.ofEpochMilli(entity.occurredAt),
                description = entity.description,
                notes = entity.notes,
                shares = orderedShares.map { share ->
                    GroupExpenseShare(
                        id = GroupExpenseShareId(share.id),
                        debtId = DebtId(share.debtId),
                        personId = PersonId(share.personId),
                        amount = Money(
                            minorUnits = share.amountMinor,
                            currency = CurrencyCode.of(entity.currencyCode),
                        ),
                    )
                },
            ),
            createdAt = Instant.ofEpochMilli(entity.createdAt),
        )
    }

    private suspend fun validateGroupExpenseReplay(
        command: CreateGroupExpenseCommand,
        aggregate: GroupExpenseAggregate,
    ) {
        val persisted = toGroupExpenseRecord(aggregate)
        val expected = GroupExpenseRecord(
            commandId = command.commandId,
            expense = command.expense,
            createdAt = command.createdAt,
        )
        if (persisted != expected) {
            throw CommandConflictException(
                "Group expense command ID was reused with different data.",
            )
        }
        command.expense.shares.forEach { share ->
            val debtAggregate = debtDao.findAggregateById(share.debtId.value)
                ?: throw CommandConflictException("Group expense child debt is missing.")
            val account = toAccountOverview(debtAggregate)
            val expectedDebt = DebtCreation(
                personId = share.personId,
                debtId = share.debtId,
                direction = command.expense.direction,
                originalAmount = share.amount,
                openedAt = command.expense.occurredAt,
                createdAt = command.createdAt,
                dueDate = null,
                description = command.expense.description,
                debtNotes = null,
                dueReminder = null,
                strongAlarm = null,
            )
            if (!debtCreationMatches(expectedDebt, debtAggregate, account)) {
                throw CommandConflictException(
                    "Group expense child debt does not match the original command.",
                )
            }
        }
    }

'''
replace_once(
    room,
    "    private suspend fun requireAccount(debtId: DebtId): AccountOverview =\n",
    helpers + "    private suspend fun requireAccount(debtId: DebtId): AccountOverview =\n",
    "room group helpers",
)

database = Path("app/src/main/java/com/wasl/app/data/local/WaslDatabase.kt")
replace_once(
    database,
    "import com.wasl.app.data.local.dao.DocumentIdentityDao\n",
    "import com.wasl.app.data.local.dao.DocumentIdentityDao\nimport com.wasl.app.data.local.dao.GroupExpenseDao\n",
    "database group dao import",
)
replace_once(
    database,
    "import com.wasl.app.data.local.entity.DocumentIdentityEntity\n",
    "import com.wasl.app.data.local.entity.DocumentIdentityEntity\n"
    "import com.wasl.app.data.local.entity.GroupExpenseEntity\n"
    "import com.wasl.app.data.local.entity.GroupExpenseShareEntity\n",
    "database group entity imports",
)
replace_once(
    database,
    "        AttachmentEntity::class,\n",
    "        AttachmentEntity::class,\n"
    "        GroupExpenseEntity::class,\n"
    "        GroupExpenseShareEntity::class,\n",
    "database group entities",
)
replace_once(database, "    version = 9,\n", "    version = 10,\n", "database version")
replace_once(
    database,
    "    abstract fun attachmentDao(): AttachmentDao\n",
    "    abstract fun attachmentDao(): AttachmentDao\n"
    "    abstract fun groupExpenseDao(): GroupExpenseDao\n",
    "database group dao accessor",
)

migration = r'''        val MIGRATION_9_10 = object : Migration(9, 10) {
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

'''
replace_once(
    database,
    "        val ALL_MIGRATIONS: Array<Migration> = arrayOf(\n",
    migration + "        val ALL_MIGRATIONS: Array<Migration> = arrayOf(\n",
    "database migration 9 10",
)
replace_once(
    database,
    "            MIGRATION_8_9,\n        )\n",
    "            MIGRATION_8_9,\n            MIGRATION_9_10,\n        )\n",
    "database all migrations",
)

backup = Path("app/src/main/java/com/wasl/app/backup/BackupService.kt")
replace_once(backup, "        const val SCHEMA_VERSION = 9\n", "        const val SCHEMA_VERSION = 10\n", "backup schema version")
replace_once(
    backup,
    "            \"debts\",\n            \"ledger_entries\",\n",
    "            \"debts\",\n            \"group_expenses\",\n            \"group_expense_shares\",\n            \"ledger_entries\",\n",
    "backup group tables",
)
replace_once(
    backup,
    "            \"debts\" to \"SELECT * FROM debts ORDER BY opened_at, id\",\n"
    "            \"ledger_entries\" to \"SELECT * FROM ledger_entries ORDER BY debt_id, sequence_number, id\",\n",
    "            \"debts\" to \"SELECT * FROM debts ORDER BY opened_at, id\",\n"
    "            \"group_expenses\" to \"SELECT * FROM group_expenses ORDER BY occurred_at, id\",\n"
    "            \"group_expense_shares\" to \"SELECT * FROM group_expense_shares ORDER BY group_expense_id, sequence_number, id\",\n"
    "            \"ledger_entries\" to \"SELECT * FROM ledger_entries ORDER BY debt_id, sequence_number, id\",\n",
    "backup group queries",
)

invariants = r'''        require(singleLong(db, "SELECT COUNT(*) FROM group_expenses WHERE total_amount_minor <= 0") == 0L) {
            "النسخة تحتوي إجمالي عملية جماعية غير صالح."
        }
        require(singleLong(db, "SELECT COUNT(*) FROM group_expense_shares WHERE amount_minor <= 0") == 0L) {
            "النسخة تحتوي حصة جماعية غير صالحة."
        }
        require(
            singleLong(
                db,
                """
                SELECT COUNT(*) FROM group_expenses
                WHERE direction NOT IN ('RECEIVABLE', 'PAYABLE')
                   OR trim(description) = ''
                   OR created_at < occurred_at
                """.trimIndent(),
            ) == 0L,
        ) { "النسخة تحتوي عملية جماعية ببيانات أساسية غير متسقة." }
        require(
            singleLong(
                db,
                """
                SELECT COUNT(*) FROM (
                    SELECT g.id
                    FROM group_expenses g
                    LEFT JOIN group_expense_shares s ON s.group_expense_id = g.id
                    GROUP BY g.id, g.total_amount_minor
                    HAVING COUNT(s.id) < 2 OR COALESCE(SUM(s.amount_minor), 0) != g.total_amount_minor
                )
                """.trimIndent(),
            ) == 0L,
        ) { "النسخة تحتوي عملية جماعية لا يطابق مجموع حصصها الإجمالي." }
        require(
            singleLong(
                db,
                """
                SELECT COUNT(*) FROM (
                    SELECT group_expense_id
                    FROM group_expense_shares
                    GROUP BY group_expense_id
                    HAVING MIN(sequence_number) != 1
                       OR MAX(sequence_number) != COUNT(*)
                       OR COUNT(DISTINCT sequence_number) != COUNT(*)
                )
                """.trimIndent(),
            ) == 0L,
        ) { "النسخة تحتوي تسلسل حصص جماعية غير متصل." }
        require(
            singleLong(
                db,
                """
                SELECT COUNT(*) FROM group_expense_shares s
                JOIN group_expenses g ON g.id = s.group_expense_id
                JOIN debts d ON d.id = s.debt_id
                WHERE d.person_id != s.person_id
                   OR d.direction != g.direction
                   OR d.currency_code != g.currency_code
                   OR d.original_amount_minor != s.amount_minor
                   OR d.opened_at != g.occurred_at
                   OR d.created_at != g.created_at
                   OR d.description != g.description
                """.trimIndent(),
            ) == 0L,
        ) { "النسخة تحتوي حصة جماعية لا تطابق الدين المرتبط بها." }
'''
replace_once(
    backup,
    "        require(singleLong(db, \"SELECT COUNT(*) FROM installments WHERE amount_minor <= 0\") == 0L) {\n",
    invariants + "        require(singleLong(db, \"SELECT COUNT(*) FROM installments WHERE amount_minor <= 0\") == 0L) {\n",
    "backup group invariants",
)

ci = Path(".github/workflows/ci.yml")
replace_once(
    ci,
    "          path: app/schemas/com.wasl.app.data.local.WaslDatabase/9.json\n",
    "          path: app/schemas/com.wasl.app.data.local.WaslDatabase/10.json\n",
    "ci schema upload v10",
)
replace_once(
    ci,
    "      - name: Verify generated Room schema v9\n"
    "        run: |\n"
    "          SCHEMA=\"app/schemas/com.wasl.app.data.local.WaslDatabase/9.json\"\n"
    "          test \"$(jq -r '.database.version' \"$SCHEMA\")\" = \"9\"\n"
    "          jq -e '.database.entities[] | select(.tableName == \"payment_claims\")' \"$SCHEMA\" >/dev/null\n"
    "          jq -e '.database.entities[] | select(.tableName == \"attachments\")' \"$SCHEMA\" >/dev/null\n"
    "          jq -e '.database.entities[] | select(.tableName == \"attachments\") | .indices[] | select(.name == \"index_attachments_relative_path\" and .unique == true)' \"$SCHEMA\" >/dev/null\n",
    "      - name: Verify generated Room schema v10\n"
    "        run: |\n"
    "          SCHEMA=\"app/schemas/com.wasl.app.data.local.WaslDatabase/10.json\"\n"
    "          test \"$(jq -r '.database.version' \"$SCHEMA\")\" = \"10\"\n"
    "          jq -e '.database.entities[] | select(.tableName == \"payment_claims\")' \"$SCHEMA\" >/dev/null\n"
    "          jq -e '.database.entities[] | select(.tableName == \"attachments\")' \"$SCHEMA\" >/dev/null\n"
    "          jq -e '.database.entities[] | select(.tableName == \"group_expenses\")' \"$SCHEMA\" >/dev/null\n"
    "          jq -e '.database.entities[] | select(.tableName == \"group_expense_shares\")' \"$SCHEMA\" >/dev/null\n"
    "          jq -e '.database.entities[] | select(.tableName == \"attachments\") | .indices[] | select(.name == \"index_attachments_relative_path\" and .unique == true)' \"$SCHEMA\" >/dev/null\n"
    "          jq -e '.database.entities[] | select(.tableName == \"group_expenses\") | .indices[] | select(.name == \"index_group_expenses_command_id\" and .unique == true)' \"$SCHEMA\" >/dev/null\n"
    "          jq -e '.database.entities[] | select(.tableName == \"group_expense_shares\") | .indices[] | select(.name == \"index_group_expense_shares_debt_id\" and .unique == true)' \"$SCHEMA\" >/dev/null\n",
    "ci schema verify v10",
)

for relative in [
    "app/src/androidTest/java/com/wasl/app/backup/BackupRestoreInstrumentedTest.kt",
    "app/src/androidTest/java/com/wasl/app/backup/AttachmentBackupRestoreInstrumentedTest.kt",
    "app/src/androidTest/java/com/wasl/app/backup/AccountDocumentBackupInstrumentedTest.kt",
]:
    path = Path(relative)
    source = path.read_text(encoding="utf-8")
    if "schemaVersion" in source:
        source = source.replace("assertEquals(9, backup.schemaVersion)", "assertEquals(10, backup.schemaVersion)")
        source = source.replace("assertEquals(9, restored.schemaVersion)", "assertEquals(10, restored.schemaVersion)")
    path.write_text(source, encoding="utf-8")
