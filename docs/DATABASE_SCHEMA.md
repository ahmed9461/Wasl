# تصميم قاعدة البيانات

آخر تحديث: 2026-08-28

الحالة: **Room Schema v10 منفذة ومثبتة ببوابة Android CI كاملة على الرأس الحالي.**

- المحرك: Room 2.8.4 فوق SQLite، وتوليد الكود عبر KSP.
- Schema المرجعية: `app/schemas/com.wasl.app.data.local.WaslDatabase/10.json`.
- التاريخ الملتزم: `1.json → 10.json`.
- لا `fallbackToDestructiveMigration` في Production.
- Ledger هو مصدر الحقيقة المالي؛ المتابعة والمستندات والعمليات الجماعية لا تستبدله.

## قواعد عامة

- IDs نصية ثابتة.
- `Instant` يخزن epoch milliseconds في INTEGER.
- `LocalDate` يخزن epoch day في INTEGER.
- الأموال: `amount_minor` INTEGER + `currency_code` TEXT.
- لا Float/Double للأموال.
- لا Cascade delete لسجل مالي.
- كل Migration تأتي مع exported schema واختبار.
- PDF والمرفقات لا تخزن BLOB داخل Room؛ الملفات في internal storage وmetadata/hash في Room.
- Android Auto Backup ليس مسار النسخ المعتمد؛ Backup التطبيق منطقي ومشفر.

## الجداول الحالية — 14 جدولًا

### `persons`

هوية الشخص وبيانات العرض: `id`, `display_name`, بيانات اتصال/ملاحظات اختيارية، timestamps و`archived_at`.

الاسم ليس مفتاحًا ماليًا؛ العلاقات تستخدم `person_id`.

### `debts`

الحساب المالي الأساسي:

- `id` PK.
- `person_id` FK→`persons.id` RESTRICT.
- `direction`.
- `original_amount_minor`, `currency_code`.
- `opened_at`, `due_date_epoch_day` nullable.
- `description`, `notes`.
- lifecycle/timestamps.

الرصيد والحالة مشتقان من Ledger.

### `ledger_entries`

- `id` PK.
- `command_id` UNIQUE.
- `debt_id` FK→`debts.id` RESTRICT.
- `kind`: `PAYMENT` / `PAYMENT_REVERSAL`.
- المبلغ والعملة والأوقات.
- `reverses_entry_id` nullable FK→`ledger_entries.id` RESTRICT.
- `sequence_number`.

الثوابت: الدفع يطابق عملة الدين ولا يتجاوز الرصيد، والعكس يشير إلى Payment سابق ولا يمحو الأصل.

### `reminders`

جدولة DUE_DATE/GENERAL: subject، type، trigger/zone/repeat، status، platform metadata وtimestamps. لا تغير Ledger.

### `audit_events`

سجل تغييرات غير مالية: `command_id` UNIQUE، aggregate/event type، before/after snapshots، reason وtimestamps.

### `document_identities`

هوية مصدر المستند: الاسم، النشاط، الهاتف، footer، default flag وtimestamps.

### `issued_documents`

السجل العام للمستندات:

- `PAYMENT_RECEIPT`, `DEBT_RECEIPT`, `ACCOUNT_STATEMENT`.
- command/document number/year/sequence.
- debt FK، و`ledger_entry_id` nullable.
- identity FK.
- immutable snapshot fields + `snapshot_json`.
- PDF path/hash/page count/failure metadata.

`ledger_entry_id` يكون مطلوبًا لإيصال السداد وnullable لمستندات الدين/كشف الحساب.

### `payment_promises`

وعد متابعة مستقل عن Ledger، بحالات `PENDING / KEPT / MISSED / CANCELLED` وresolution metadata.

### `installment_plans`

خطط أقساط مع revisions وحالات `ACTIVE / SUPERSEDED`. revision جديدة تحفظ السابقة ولا تعدل التاريخ.

### `installments`

حصة القسط: plan/debt FKs، sequence، due date، amount/currency. paid/remaining مشتقان من Ledger.

### `payment_claims` — منذ v8

مطالبات «طالبني» المستقلة عن Ledger:

- create/resolution command IDs.
- debt FK.
- `TODAY / TOMORROW / SALARY / CUSTOM`.
- `ACTIVE / RESOLVED / CANCELLED`.
- custom follow-up date عند الحاجة.

لا تغير balance أو Ledger أو due date.

### `attachments` — منذ v9

خزنة الإثباتات:

- debt FK.
- ledger entry FK اختياري من نفس الدين.
- display name / mime / size.
- `relative_path` UNIQUE.
- `sha256`, created_at, note.

المسار مقيد بخزنة التطبيق وSHA-256 إلزامي.

### `group_expenses` — منذ v10

يحفظ العملية الجماعية الأصلية كسياق تاريخي، وليس Ledger موازيًا:

- `id` PK.
- `command_id` UNIQUE.
- `direction`.
- `total_amount_minor`, `currency_code`.
- `occurred_at`.
- `description`, `notes`.
- `created_at`.

الثوابت: total موجب، وصف صالح، created_at لا يسبق occurred_at، وعملة/اتجاه العملية موحدان لكل shares.

### `group_expense_shares` — منذ v10

كل حصة تربط العملية الجماعية بدين وَصل عادي:

- `id` PK.
- `group_expense_id` FK→`group_expenses.id` RESTRICT.
- `debt_id` FK→`debts.id` RESTRICT وUNIQUE.
- `person_id` FK→`persons.id` RESTRICT.
- `amount_minor` موجب.
- `sequence_number`.

فهارس/ثوابت أساسية:

- UNIQUE(`group_expense_id`, `sequence_number`).
- UNIQUE(`group_expense_id`, `person_id`).
- UNIQUE(`debt_id`).
- كل عملية فيها 2+ shares.
- الأشخاص والديون والحصص فريدة.
- مجموع shares يساوي `group_expenses.total_amount_minor` بدقة.
- دين الحصة يطابق الشخص/الاتجاه/العملة/المبلغ/وقت العملية/الوصف المتوقع.

## الـView الحالية

### `payment_issued_documents`

```sql
SELECT *
FROM issued_documents
WHERE document_type = 'PAYMENT_RECEIPT'
```

## سلسلة Migrations

- v1→v2: `reminders`.
- v2→v3: `audit_events`.
- v3→v4: document identities + issued documents.
- v4→v5: payment promises.
- v5→v6: installment plans + installments.
- v6→v7: تعميم `issued_documents` وجعل `ledger_entry_id` nullable وإعادة إنشاء View.
- v7→v8: `payment_claims`.
- v8→v9: `attachments` وفهارسها.
- **v9→v10:** `group_expenses` + `group_expense_shares` وفهارس/FKs الخاصة بها.

`WaslDatabase.ALL_MIGRATIONS` يسجل السلسلة صراحة.

## Backup contract v10

Backup المنطقي المشفر يشمل 14 جدولًا:

1. `persons`
2. `debts`
3. `ledger_entries`
4. `group_expenses`
5. `group_expense_shares`
6. `reminders`
7. `audit_events`
8. `document_identities`
9. `issued_documents`
10. `payment_promises`
11. `payment_claims`
12. `installment_plans`
13. `installments`
14. `attachments`

ويشمل كذلك ملفات PDF للسجلات الجاهزة وملفات المرفقات.

قبل Restore:

- Schema يجب أن تكون v10 المدعومة.
- شكل الجداول والصفوف متوقع.
- المسارات تبقى داخل خزائن التطبيق.
- SHA-256 يطابق.
- البيانات تختبر داخل Room مؤقتة.
- Foreign Keys والثوابت المالية تفحص.
- Claims/Attachments/Group Expense invariants تفحص.
- Group shares تطابق الديون العادية المرتبطة بها.
- الاستبدال النهائي يملك rollback عند الفشل.

## آخر بوابة تحقق

**Android CI #967 — run `33137676461` — head `e09efee71cea4b1734afe50a025c2a3218ec2dd5`.**

- Unit/Lint/Debug ✅
- Room v10 generated/current verification ✅
- Emulator instrumentation **123/123** ✅
- Migration/Repository/Backup regressions، بما فيها Group Expense v10 ✅
- PDF evidence للأنواع الثلاثة ✅

بذلك v10 هي Schema الحالية الموثقة، وليست مرحلة pending.
