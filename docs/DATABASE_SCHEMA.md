# تصميم قاعدة البيانات

آخر تحديث: 2026-08-27

الحالة: **Room Schema v9 منفذة في الكود؛ آخر `verify` أكد توليد v9، وتنتظر البوابة الكاملة Android instrumentation بعد إصلاح imports الاختبارات.**

- المحرك: Room 2.8.4 فوق SQLite، وتوليد الكود عبر KSP.
- ملف Schema المرجعي الحالي: `app/schemas/com.wasl.app.data.local.WaslDatabase/9.json`.
- المبدأ: Ledger هو مصدر الحقيقة المالي؛ Projections والمتابعة والمستندات لا تستبدله.
- لا `fallbackToDestructiveMigration` في Production.

## قواعد عامة

- IDs نصية ثابتة.
- `Instant` يخزن epoch milliseconds في INTEGER.
- `LocalDate` يخزن epoch day في INTEGER.
- الأموال تخزن `amount_minor` INTEGER و`currency_code` TEXT.
- Foreign Keys مفعلة حيث توجد علاقات صريحة.
- لا Cascade delete لسجل مالي.
- كل Migration تصدر Schema JSON وتملك اختبارًا.
- PDF والمرفقات لا تخزن BLOB داخل Room؛ الملفات في Internal storage وMetadata/Hash في Room.
- Android Auto Backup ليس مسار النسخ المعتمد؛ Backup التطبيق منطقي ومشفر.

## الجداول الحالية

### `persons`

- `id` TEXT PK NOT NULL.
- `display_name` TEXT NOT NULL.
- `phone`, `email`, `photo_uri`, `notes` nullable.
- `created_at`, `updated_at` INTEGER NOT NULL.
- `archived_at` nullable.

الاسم ليس مفتاحًا ماليًا؛ الربط الحقيقي بـ`person_id`.

### `debts`

- `id` TEXT PK.
- `person_id` FK→`persons.id` RESTRICT.
- `direction`.
- `original_amount_minor`, `currency_code`.
- `opened_at`.
- `due_date_epoch_day` nullable.
- `description`, `notes` nullable.
- `lifecycle_state`.
- `created_at`, `updated_at`, `closed_at`.

`closed_at` Projection مشتقة من Ledger ولا تمثل رصيدًا موازيًا.

### `ledger_entries`

- `id` TEXT PK.
- `command_id` UNIQUE.
- `debt_id` FK→`debts.id` RESTRICT.
- `kind`: `PAYMENT` / `PAYMENT_REVERSAL`.
- المبلغ/العملة/الأوقات حسب نوع الحدث.
- `reverses_entry_id` nullable FK→`ledger_entries.id` RESTRICT.
- `sequence_number` ضمن تسلسل الحساب.

الثوابت:

- الدفع لا يتجاوز الرصيد ويطابق عملة الدين.
- العكس يشير إلى Payment سابق ولا يمحو الأصل.
- التشغيل الطبيعي لا يوفر Update/Delete لحدث مالي.

### `reminders`

- `id` PK.
- `subject_type`, `subject_id`, `reminder_type`, `schedule_type`.
- `trigger_at`, `zone_id`, `repeat_rule`.
- `status`, `platform_request_code`, `last_failure_code`, `delivered_at`.
- `created_at`, `updated_at`.

يخدم DUE_DATE وGENERAL scheduling. لا يغير Ledger.

### `audit_events`

- `id` PK.
- `command_id` UNIQUE.
- `aggregate_type`, `aggregate_id`, `event_type`.
- `occurred_at`, `actor`.
- `before_snapshot`, `after_snapshot`, `reason` nullable.

Audit لتغييرات غير مالية مثل جدول الاستحقاق؛ لا يحل محل Ledger.

### `document_identities`

- هوية مصدر المستند: الاسم، النشاط، الهاتف، footer، الافتراضي، timestamps.

### `issued_documents`

السجل العام للمستندات المالية:

- `id`, `command_id`, `document_type`, `status`, `document_number`.
- `issue_year`, `sequence_number`.
- `debt_id` FK.
- `ledger_entry_id` nullable FK.
- `identity_id` FK.
- Snapshot للشخص والمبلغ والعملة ووقت الإصدار.
- `snapshot_version`, `snapshot_json`.
- `pdf_relative_path`, `pdf_sha256`, `page_count`, `failure_code`.

دلالة `ledger_entry_id`:

- `PAYMENT_RECEIPT`: مرتبط بعملية Payment.
- `DEBT_RECEIPT`: null.
- `ACCOUNT_STATEMENT`: null.

كل مستند يصدر من Snapshot ثابت ولا يعاد تفسيره من الحالة الحية لاحقًا.

### `payment_promises`

- `id`, `create_command_id`, `debt_id`.
- `promised_date_epoch_day`.
- `status`: `PENDING / KEPT / MISSED / CANCELLED`.
- ملاحظات وحقول resolution وtimestamps.

Promise لا تنشئ Payment ولا تغير الرصيد.

### `installment_plans`

- `id`, `command_id`, `debt_id`.
- `revision_number`.
- `status`: `ACTIVE / SUPERSEDED`.
- `supersedes_plan_id`, `superseded_at`, `superseded_after_sequence`, `reason`.

Revision جديدة تحفظ السابقة بدل تعديل التاريخ.

### `installments`

- `id`, `plan_id`, `debt_id`, `sequence_number`.
- `due_date_epoch_day`, `amount_minor`, `currency_code`, `created_at`.

القسط لا يملك رصيدًا ماليًا موازيًا؛ paid/remaining مشتقان من Ledger.

### `payment_claims` — منذ v8

مطالبات «طالبني» مستقلة عن Ledger:

- `id` TEXT PK.
- `create_command_id` TEXT UNIQUE.
- `debt_id` TEXT FK→`debts.id` RESTRICT.
- `claimed_at` INTEGER.
- `follow_up_kind`: `TODAY / TOMORROW / SALARY / CUSTOM`.
- `follow_up_date_epoch_day` nullable.
- `note` nullable.
- `status`: `ACTIVE / RESOLVED / CANCELLED`.
- `created_at`, `updated_at`.
- `resolution_command_id` nullable UNIQUE.
- `resolved_at`, `resolution_note` nullable.

Indexes تشمل create/resolution command IDs، debt+claimed_at، debt+status، status+follow-up date.

ثوابت الاستعادة والتنفيذ:

- Claim يسمح فقط لدين `PAYABLE`.
- `CUSTOM` يتطلب تاريخ متابعة.
- `SALARY` لا يخمن تاريخًا.
- الحالة النهائية تتطلب resolution metadata.
- Claim لا يغير `originalAmount`, balance, Ledger أو `due_date`.

### `attachments` — منذ v9

خزنة ملفات الإثباتات المحلية:

- `id` TEXT PK.
- `debt_id` TEXT FK→`debts.id` RESTRICT.
- `ledger_entry_id` TEXT nullable FK→`ledger_entries.id` RESTRICT.
- `display_name` TEXT.
- `mime_type` TEXT.
- `size_bytes` INTEGER.
- `relative_path` TEXT UNIQUE.
- `sha256` TEXT.
- `created_at` INTEGER.
- `note` nullable.

Indexes:

- (`debt_id`, `created_at`).
- `ledger_entry_id`.
- `relative_path` UNIQUE.

ثوابت السلامة:

- المسار نسبي ومقيد بخزنة التطبيق.
- SHA-256 إلزامي.
- عند ربط المرفق بحركة يجب أن تكون الحركة من نفس الدين.
- فقد الملف أو اختلاف البصمة لا يعامل كمرفق سليم.
- لا تخزين URI مؤقت كمصدر دائم بديل عن نسخ الملف إلى الخزنة.

## الـView الحالية

### `payment_issued_documents`

```sql
SELECT *
FROM issued_documents
WHERE document_type = 'PAYMENT_RECEIPT'
```

تجعل استعلامات إيصالات السداد صريحة بعد تعميم `issued_documents`.

## سلسلة Migrations

- **v1→v2**: `reminders`.
- **v2→v3**: `audit_events`.
- **v3→v4**: `document_identities` + `issued_documents`.
- **v4→v5**: `payment_promises`.
- **v5→v6**: `installment_plans` + `installments`.
- **v6→v7**: إعادة بناء `issued_documents` وجعل `ledger_entry_id` nullable وإعادة إنشاء View.
- **v7→v8**: إضافة `payment_claims` وفهارسها.
- **v8→v9**: إضافة `attachments` وفهارسها، ومنها `relative_path` UNIQUE.

`WaslDatabase.ALL_MIGRATIONS` يسجل السلسلة كلها صراحة.

## Backup contract v9

Backup المنطقي المشفر يشمل 12 جدولًا:

1. `persons`
2. `debts`
3. `ledger_entries`
4. `reminders`
5. `audit_events`
6. `document_identities`
7. `issued_documents`
8. `payment_promises`
9. `payment_claims`
10. `installment_plans`
11. `installments`
12. `attachments`

ويشمل كذلك:

- ملفات PDF للسجلات `READY`.
- ملفات المرفقات.

قبل Restore:

- Schema يجب أن تكون v9 المدعومة.
- شكل الجداول والصفوف متوقع.
- المسارات تبقى داخل خزائن التطبيق.
- SHA-256 يطابق.
- البيانات تختبر داخل Room مؤقتة.
- Foreign Keys والثوابت المالية تفحص.
- Claim validation يفحص الاتجاه والحالات والتواريخ.
- Attachment validation يفحص أن الحركة الاختيارية من نفس الدين.
- الاستبدال النهائي يملك Rollback عند الفشل.

## بوابة التحقق الحالية

Android CI #851 على الرأس السابق `94ce0adf...`:

- Unit tests ✅
- Lint ✅
- Debug APK ✅
- Room Schema v9 generated/current check ✅
- وجود `payment_claims` و`attachments` وفهرس `attachments.relative_path` الفريد ✅
- Android instrumentation لم تبدأ لأن compilation لاختبارات Android توقف بسبب أربعة imports قديمة لـ`onNode`.

لا تسجل v9 كـ«بوابة كاملة ناجحة» حتى تمر Android instrumentation وBackup/Restore وPDF evidence على الرأس المصحح.
