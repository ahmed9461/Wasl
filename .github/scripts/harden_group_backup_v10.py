from pathlib import Path

path = Path("app/src/main/java/com/wasl/app/backup/BackupService.kt")
text = path.read_text(encoding="utf-8")

old = '''        require(
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
'''

new = '''        require(
            singleLong(
                db,
                """
                SELECT COUNT(*) FROM group_expenses
                WHERE trim(id) = ''
                   OR trim(command_id) = ''
                   OR direction NOT IN ('RECEIVABLE', 'PAYABLE')
                   OR currency_code NOT GLOB '[A-Z][A-Z][A-Z]'
                   OR trim(description) = ''
                   OR (notes IS NOT NULL AND trim(notes) = '')
                   OR created_at < occurred_at
                """.trimIndent(),
            ) == 0L,
        ) { "النسخة تحتوي عملية جماعية ببيانات أساسية غير متسقة." }
        require(
            singleLong(
                db,
                """
                SELECT COUNT(*) FROM group_expense_shares
                WHERE trim(id) = ''
                   OR trim(group_expense_id) = ''
                   OR trim(debt_id) = ''
                   OR trim(person_id) = ''
                   OR sequence_number <= 0
                """.trimIndent(),
            ) == 0L,
        ) { "النسخة تحتوي حصة جماعية ببيانات تعريف غير صالحة." }
'''

count = text.count(old)
if count != 1:
    raise SystemExit(f"Expected exactly one group metadata invariant block, found {count}")

path.write_text(text.replace(old, new, 1), encoding="utf-8")
