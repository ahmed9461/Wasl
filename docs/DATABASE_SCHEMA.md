# تصميم قاعدة البيانات

آخر تحديث: 2026-08-26

الحالة: **Room Schema v7 منفذة ومتحقق منها في CI**

- المحرك: Room 2.8.4 فوق SQLite، وتوليد الكود عبر KSP 2.3.11.
- ملف Schema المرجعي: `app/schemas/com.wasl.app.data.local.WaslDatabase/7.json`.
- `identityHash`: `d2c9fe45f2707138bc1476639617e255`.
- المبدأ: Ledger هو مصدر الحقيقة المالي؛ Projections والحالات المشتقة لا تستبدله.
- لا `fallbackToDestructiveMigration` في Production.

## قواعد عامة

- IDs نصية ثابتة.
- `Instant` يخزن epoch milliseconds في INTEGER.
- `LocalDate` يخزن epoch day في INTEGER.
- الأموال تخزن `amount_minor` INTEGER و`currency_code` TEXT.
- Foreign Keys مفعلة حيث توجد علاقات صريحة.
- لا Cascade delete لسجل مالي.
- كل Migration تصدر Schema JSON وتملك اختبارًا.
- PDF لا يخزن BLOB؛ الملف في Internal storage وMetadata/Hash في Room.
- Android Auto Backup ليس مسار النسخ المعتمد؛ Backup التطبيق منطقي ومشفر.

## الجداول الحالية

### `persons`

الأعمدة:

- `id` TEXT PK NOT NULL.
- `display_name` TEXT NOT NULL.
- `phone`, `email`, `photo_uri`, `notes` TEXT nullable.
- `created_at`, `updated_at` INTEGER NOT NULL.
- `archived_at` INTEGER nullable.

Indexes:

- `display_name`.
- `archived_at`.

الاسم ليس مفتاحًا ماليًا؛ `person_id` هو الربط الحقيقي، لذلك يسمح بتشابه الأسماء.

### `debts`

الأعمدة:

- `id` TEXT PK.
- `person_id` TEXT FK→`persons.id` RESTRICT.
- `direction` TEXT.
- `original_amount_minor` INTEGER.
- `currency_code` TEXT.
- `opened_at` INTEGER.
- `due_date_epoch_day` INTEGER nullable.
- `description`, `notes` TEXT nullable.
- `lifecycle_state` TEXT.
- `created_at`, `updated_at` INTEGER.
- `closed_at` INTEGER nullable projection.

Indexes:

- (`person_id`, `opened_at`).
- (`lifecycle_state`, `due_date_epoch_day`).
- (`currency_code`, `direction`).

`closed_at` مشتق من Ledger ولا يمثل رصيدًا ماليًا مستقلًا.

### `ledger_entries`

الأعمدة:

- `id` TEXT PK.
- `command_id` TEXT NOT NULL UNIQUE.
- `debt_id` TEXT FK→`debts.id` RESTRICT.
- `kind` TEXT (`PAYMENT` / `PAYMENT_REVERSAL`).
- `amount_minor`, `currency_code`, `occurred_at` nullable بحسب نوع الحدث.
- `recorded_at` INTEGER NOT NULL.
- `reverses_entry_id` nullable FK→`ledger_entries.id` RESTRICT.
- `note`, `reason` nullable بحسب النوع.
- `sequence_number` INTEGER NOT NULL.

Indexes/قيود:

- `command_id` UNIQUE.
- (`debt_id`, `sequence_number`) UNIQUE.
- `reverses_entry_id` UNIQUE عندما يكون غير null.
- الدفع لا يتجاوز الرصيد، ويطابق عملة الدين.
- العكس يشير إلى Payment سابق ولا يمحو الحدث الأصلي.
- التشغيل العادي لا يوفر Update/Delete لحدث مالي.

### `reminders`

الأعمدة:

- `id` TEXT PK.
- `subject_type`, `subject_id`, `reminder_type`, `schedule_type` TEXT.
- `trigger_at` INTEGER.
- `zone_id` TEXT.
- `repeat_rule` TEXT nullable.
- `status` TEXT.
- `platform_request_code` INTEGER nullable.
- `last_failure_code` TEXT nullable.
- `delivered_at` INTEGER nullable.
- `created_at`, `updated_at` INTEGER.

Indexes:

- (`subject_type`, `subject_id`, `reminder_type`) UNIQUE.
- (`status`, `trigger_at`).
- `subject_id`.
- `platform_request_code` UNIQUE عندما يكون غير null.

تستخدم WorkManager للمتابعة وExact Alarm فقط للمنبه القوي، ولا يغير أي منهما Ledger.

### `audit_events`

الأعمدة:

- `id` TEXT PK.
- `command_id` TEXT UNIQUE.
- `aggregate_type`, `aggregate_id`, `event_type` TEXT.
- `occurred_at` INTEGER.
- `actor` TEXT.
- `before_snapshot`, `after_snapshot`, `reason` TEXT nullable.

Index:

- (`aggregate_id`, `aggregate_type`, `occurred_at`).

Audit يسجل تغييرات غير مالية مثل جدول الاستحقاق ولا يحل محل Ledger.

### `document_identities`

الأعمدة:

- `id` TEXT PK.
- `display_name` TEXT.
- `activity_name`, `phone`, `footer_text` TEXT nullable.
- `is_default` INTEGER.
- `created_at`, `updated_at` INTEGER.

Index: `is_default`.

### `issued_documents`

السجل العام للمستندات المالية.

الأعمدة:

- `id` TEXT PK.
- `command_id` TEXT UNIQUE.
- `document_type` TEXT.
- `status` TEXT.
- `document_number` TEXT UNIQUE.
- `issue_year`, `sequence_number` INTEGER.
- `debt_id` TEXT FK→`debts.id` RESTRICT.
- `ledger_entry_id` TEXT **nullable** FK→`ledger_entries.id` RESTRICT.
- `identity_id` TEXT FK→`document_identities.id` RESTRICT.
- `person_id`, `person_name_snapshot` TEXT.
- `amount_minor` INTEGER.
- `currency_code` TEXT.
- `issued_at` INTEGER.
- `snapshot_version` INTEGER.
- `snapshot_json` TEXT.
- `pdf_relative_path` TEXT.
- `pdf_sha256` TEXT nullable حتى يصبح المستند READY.
- `page_count` INTEGER nullable.
- `failure_code` TEXT nullable.
- `created_at`, `updated_at` INTEGER.

Indexes:

- `command_id` UNIQUE.
- `document_number` UNIQUE.
- (`document_type`, `ledger_entry_id`) UNIQUE.
- (`issue_year`, `sequence_number`) UNIQUE.
- (`debt_id`, `issued_at`).
- `ledger_entry_id`.
- `identity_id`.
- `person_id`.

دلالة `ledger_entry_id`:

- `PAYMENT_RECEIPT`: مطلوب ويجب أن يطابق Payment داخل Snapshot.
- `DEBT_RECEIPT`: null.
- `ACCOUNT_STATEMENT`: null.

كل مستند يحفظ Snapshot ثابتًا عند الإصدار؛ لا يعاد تفسير الوثيقة من الحالة الحية.

### `payment_promises`

الأعمدة:

- `id` TEXT PK.
- `create_command_id` TEXT UNIQUE.
- `debt_id` TEXT FK→`debts.id` RESTRICT.
- `promised_date_epoch_day` INTEGER.
- `status` TEXT.
- `note` TEXT nullable.
- `created_at` INTEGER.
- `resolution_command_id` TEXT nullable UNIQUE.
- `resolved_at` INTEGER nullable.
- `resolution_note` TEXT nullable.
- `updated_at` INTEGER.

Indexes:

- `create_command_id` UNIQUE.
- `resolution_command_id` UNIQUE عندما يكون غير null.
- (`debt_id`, `promised_date_epoch_day`).
- (`debt_id`, `status`).
- (`status`, `promised_date_epoch_day`).

Promise لا تنشئ Payment ولا تغير الرصيد.

### `installment_plans`

الأعمدة:

- `id` TEXT PK.
- `command_id` TEXT UNIQUE.
- `debt_id` TEXT FK→`debts.id` RESTRICT.
- `revision_number` INTEGER.
- `status` TEXT.
- `created_at` INTEGER.
- `supersedes_plan_id` TEXT nullable.
- `superseded_at`, `superseded_after_sequence` INTEGER nullable.
- `reason` TEXT nullable.

Indexes:

- `command_id` UNIQUE.
- (`debt_id`, `revision_number`) UNIQUE.
- (`debt_id`, `status`).
- `supersedes_plan_id`.

Revision جديدة لا تعدل السابقة؛ تحولها إلى `SUPERSEDED` وتبقي التاريخ.

### `installments`

الأعمدة:

- `id` TEXT PK.
- `plan_id` TEXT FK→`installment_plans.id` RESTRICT.
- `debt_id` TEXT FK→`debts.id` RESTRICT.
- `sequence_number` INTEGER.
- `due_date_epoch_day` INTEGER.
- `amount_minor` INTEGER.
- `currency_code` TEXT.
- `created_at` INTEGER.

Indexes:

- (`plan_id`, `sequence_number`) UNIQUE.
- (`plan_id`, `due_date_epoch_day`).
- (`debt_id`, `due_date_epoch_day`).

القسط لا يملك رصيدًا ماليًا موازيًا؛ paid/remaining مشتقان من Ledger عند العرض.

## الـView الحالية

### `payment_issued_documents`

```sql
SELECT *
FROM issued_documents
WHERE document_type = 'PAYMENT_RECEIPT'
```

الغرض: إبقاء استعلامات إيصالات السداد صريحة بعد تعميم `issued_documents` على أنواع مستندات الحساب.

## سلسلة Migrations

- **v1→v2**: إضافة `reminders`.
- **v2→v3**: إضافة `audit_events`.
- **v3→v4**: إضافة `document_identities` و`issued_documents` لإيصال السداد.
- **v4→v5**: إضافة `payment_promises` وفهارسها.
- **v5→v6**: إضافة `installment_plans` و`installments` ودعم Revisions.
- **v6→v7**: إعادة بناء `issued_documents` مع `ledger_entry_id` nullable، نسخ البيانات السابقة، إعادة إنشاء الفهارس و`payment_issued_documents`.

Migration v6→v7 تختبر على قاعدة v6 فعلية وتثبت:

- بقاء Payment Receipt القديمة.
- `ledger_entry_id` لم يعد NOT NULL.
- إمكانية إدخال مستند حساب دون Ledger source.
- View الخاصة بالسداد لا تعرض الأنواع الأخرى.

## Backup contract

Backup v7 المنطقي يشمل الجداول العشرة أعلاه وملفات PDF المرتبطة بسجلات `issued_documents` ذات الحالة `READY`.

قبل Restore:

- Schema يجب أن تكون مدعومة.
- شكل الجداول والصفوف يجب أن يكون متوقعًا.
- المسارات يجب أن تبقى داخل مجلد المستندات.
- SHA-256 يجب أن يطابق.
- البيانات تختبر في Room مؤقتة.
- Foreign Keys والثوابت المالية تفحص قبل الاستبدال.

أي جدول جديد يدخل مصدر الحقيقة أو أي ملف مهم جديد يجب أن يحدث Backup contract واختبارات Round-trip في نفس المرحلة.

## بوابة التحقق

CI #382 على head `be7f67d`:

- Room Schema v7 generated/current check ✅
- Baseline migrations + v6→v7 ✅
- Android instrumentation **63/63** ✅
- Backup/Restore ✅
- PDF records/files/checksums ✅
