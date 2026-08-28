# تصميم قاعدة البيانات

آخر تحديث: 2026-08-28

الحالة: **Room Schema v11 منفذة، exported schema ملتزمة، ومهاجرة 10→11 مغطاة بالاختبارات.**

- Room 2.8.4 فوق SQLite، وتوليد الكود عبر KSP.
- Schema المرجعية: `app/schemas/com.wasl.app.data.local.WaslDatabase/11.json`.
- التاريخ الملتزم: `1.json → 11.json`.
- لا `fallbackToDestructiveMigration` في Production.
- Ledger هو مصدر الحقيقة المالي.

## قواعد عامة

- IDs نصية ثابتة.
- `Instant` = epoch milliseconds في INTEGER.
- `LocalDate` = epoch day في INTEGER.
- الأموال = `amount_minor` INTEGER + `currency_code` TEXT؛ لا Float/Double.
- لا Cascade delete لسجل مالي.
- كل Migration معها exported schema واختبار.
- PDF والمرفقات تحفظ كملفات داخلية، وmetadata/hash فقط في Room.
- Backup التطبيق منطقي ومشفر؛ Android Auto Backup ليس المسار المالي المعتمد.

## الجداول الحالية — 15 جدولًا

1. `persons`
2. `debts`
3. `ledger_entries`
4. `reminders`
5. `audit_events`
6. `document_identities`
7. `issued_documents`
8. `payment_promises`
9. `installment_plans`
10. `installments`
11. `payment_claims`
12. `attachments`
13. `group_expenses`
14. `group_expense_shares`
15. `document_templates`

## `document_templates` — منذ v11

يحفظ قوالب عرض المستندات بصورة مستقلة عن Ledger. القالب المختار عند إصدار مستند يثبت داخل immutable snapshot حتى لا يؤدي تعديل القوالب لاحقًا إلى تغيير معنى أو مظهر المستند التاريخي عند إعادة تفسير بياناته.

الأنماط المدعومة في طبقة القوالب الحالية:

- `MINIMAL`
- `BUSINESS`
- `CLASSIC`
- `COMPACT`
- `MODERN`

كما تشمل إعدادات عرض مثل الهاتف/footer/balance/notes بحسب تعريف القالب.

## المستندات

`issued_documents` يدعم:

- `PAYMENT_RECEIPT`
- `DEBT_RECEIPT`
- `ACCOUNT_STATEMENT`

كل مستند يحتفظ بـimmutable snapshot، رقم مستند، metadata، PDF path، SHA-256، page count وحالة الفشل إن وجدت. إيصال السداد فقط يرتبط إلزاميًا بـLedger entry؛ مستندات الدين وكشف الحساب لا تحتاج Ledger entry.

## المالية والمتابعة

- `debts` يحفظ أصل الحساب؛ الرصيد مشتق من Ledger.
- `ledger_entries` append-only ويحتوي PAYMENT / PAYMENT_REVERSAL مع `command_id` فريد.
- `reminders`, `payment_promises`, `payment_claims`, `installment_plans` ليست Ledger ولا تغير الرصيد مباشرة.
- `attachments` تربط ملفات الإثبات بالدين وبـledger entry اختياري من نفس الدين، مع `relative_path` فريد وSHA-256.

## Group Expense

`group_expenses` يحفظ العملية الأصلية كسياق تاريخي، و`group_expense_shares` يربط كل حصة بدين وَصل عادي. الثوابت الأساسية:

- 2+ shares.
- أشخاص وحصص وديون فريدة.
- عملة واتجاه موحدان.
- مجموع shares يساوي total بدقة.
- كل Debt حصة يطابق الشخص/الاتجاه/العملة/المبلغ والسياق المتوقع.
- create/replay/conflict/rollback داخل transaction واحدة.

## الـView

`payment_issued_documents` يرشح `issued_documents` إلى `PAYMENT_RECEIPT`.

## سلسلة Migrations

- v1→v2: reminders.
- v2→v3: audit events.
- v3→v4: document identities + issued documents.
- v4→v5: payment promises.
- v5→v6: installment plans + installments.
- v6→v7: تعميم issued documents وجعل `ledger_entry_id` nullable.
- v7→v8: payment claims.
- v8→v9: attachments.
- v9→v10: group expenses + shares.
- **v10→v11: document templates.**

`WaslDatabase.ALL_MIGRATIONS` يسجل السلسلة صراحة.

## Backup contract

Backup/Restore يشمل البيانات المالية والمتابعة والمستندات والقوالب والمرفقات والعمليات الجماعية، إضافة إلى ملفات PDF والمرفقات نفسها حيث ينطبق العقد.

قبل Restore:

- schema/version مدعومة.
- JSON/rows بالشكل المتوقع.
- المسارات داخل الخزائن المعتمدة.
- hashes صحيحة.
- Foreign Keys والثوابت المالية/الجماعية سليمة.
- البيانات تختبر في staging/Room مؤقتة قبل الاستبدال.
- rollback متاح عند الفشل.

## التحقق

Android CI #1017 — run `33203634720` على رأس Document Templates v11 نجح بالكامل قبل الدمج:

- Unit/Lint/Debug ✅
- Room v11 generated/verified ✅
- Migration/Repository/Backup instrumentation ✅
- PDF evidence للأنواع الثلاثة ✅

الرأس المجمع النهائي يجب أن يجتاز GitHub Actions نفسها قبل الانتقال إلى `main`.
