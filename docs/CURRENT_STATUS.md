# وَصل — الحالة الحالية

آخر مراجعة: 2026-08-28

هذا الملف يلخص الحالة الحية. عند التعارض يكون مصدر الحقيقة بالترتيب: الكود على الرأس الحالي، Room exported schema، GitHub Actions للرأس نفسه، ثم هذا الملف وبقية الوثائق.

## المرشح الحالي

- المستودع: `ahmed9461/Wasl`.
- فرع الإنهاء المجمع: `agent/final-polish-doc-sync`.
- آخر رأس يحمل تغييرات المنتج قبل مزامنة الوثائق: `5794e9a74914c8af3a3ecf750664f2f6083eaf66`.
- الإصدار المرشح: `0.1.0`، `versionCode = 1`.
- Room Database: **Schema v11**.
- exported schemas ملتزمة: `1.json → 11.json`.
- لا `fallbackToDestructiveMigration` في Production.
- `main` لم يُعامل كنسخة إصدار قبل إتمام بوابة القبول للرأس المجمع.

## بوابات التحقق

### Document Templates / Schema v11

Android CI **#1017** — run `33203634720` — head `fdbb28b2aca59f7d0542eaa785d72502d695a431` نجح بالكامل قبل الدمج:

- Unit tests / Lint / Debug APK ✅
- توليد وفحص Room Schema v11 ✅
- Emulator instrumentation / migration / repository / backup tests ✅
- فحص Payment Receipt PDF ✅
- فحص Debt Receipt PDF ✅
- فحص Account Statement PDF ✅
- رفع instrumentation وPDF evidence ✅

تم بعد ذلك دمج هذه الدفعة في فرع الإنهاء بالرأس `5794e9a...`. بوابة GitHub Actions للرأس النهائي بعد مزامنة الوثائق هي الحكم النهائي قبل الدمج إلى `main`.

## الوظائف المكتملة

### المالية

- أشخاص وحسابات متعددة للشخص.
- RECEIVABLE / PAYABLE.
- YER / SAR / USD دون netting مضلل بين العملات.
- Money بminor units من `Long` فقط.
- Ledger append-only؛ التصحيح بـPayment Reversal وليس حذف التاريخ.
- دفعات جزئية ونهائية، idempotency وreplay.

### المتابعة

- Due date + audit.
- Today.
- WorkManager scheduling/recovery.
- Exact Alarm اختياري.
- General Reminders.
- Payment Promises.
- Installment Plans/Revisions.
- Payment Claims «طالبني».

### البحث والعرض

- Basic/Advanced Search.
- Person Timeline.
- Objective Statistics.
- Documents Hub.
- Account Details timeline.
- رسائل سداد جاهزة للنسخ/المشاركة فقط.
- RTL first-class وBidi isolation وadaptive/large-font hardening للشاشات الرئيسية.

### Natural Entry / Voice

- `Parser → Preview → explicit Confirmation → Save`.
- Voice Dictation يغذي نفس مسار Natural Entry.
- recognized / empty / cancelled / unavailable / launch failure مغطاة.
- لا كتابة مالية من الصوت أو الإشعار قبل التأكيد الصريح.

### Group Expense

- العملية الجماعية الأصلية سياق تاريخي وليست Ledger موازيًا.
- كل share تنشئ Debt عاديًا.
- 2+ مشاركين فريدين، unequal shares، عملة واتجاه موحدان، exact total.
- atomic create + replay/idempotency + conflict detection + rollback.
- Preview/Confirmation إلزاميان.

### المستندات وRoom v11

- `PAYMENT_RECEIPT`, `DEBT_RECEIPT`, `ACCOUNT_STATEMENT` من immutable snapshots.
- Document Templates محفوظة في `document_templates` منذ v11.
- أنماط القوالب الحالية: MINIMAL / BUSINESS / CLASSIC / COMPACT / MODERN.
- القالب المختار يثبت داخل snapshot للمستند؛ تعديل الإعدادات لاحقًا لا يعيد تفسير مستند قديم.
- `11.json` مولد من Room ومثبت في Git، وليس ملفًا مكتوبًا يدويًا.
- SHA-256 وpage count وفحوص سلامة قبل فتح/مشاركة PDF.

### الملفات والأمان

- Attachments/evidence vault داخل مساحة التطبيق مع metadata وSHA-256 ومسارات مقيدة.
- FileProvider غير exported للمشاركة الصريحة.
- Backup/Restore مشفر مع staging + schema/path/hash/FK/invariant validation + rollback.
- App Lock عبر BiometricPrompt / Device Credential.
- `FLAG_SECURE` وسياسة خصوصية للإشعارات الحساسة.
- التطبيق الحالي Local-first ولا يطلب صلاحية `INTERNET`.

## قاعدة البيانات الحالية

Schema **v11** يضيف `document_templates` فوق v10، ليصبح عدد جداول Room المنطقية **15**.

سلسلة migrations محفوظة صراحة حتى `10→11`، وكل ترقية جديدة يجب أن تأتي مع exported schema واختبارات migration وBackup/Restore update عند الحاجة.

## الإصدار

- `versionName = 0.1.0`.
- مسار Signed Release موجود في `.github/workflows/release.yml`.
- توقيع Release يقرأ فقط متغيرات/Secrets خارج Git:
  - `WASL_KEYSTORE_BASE64`
  - `WASL_KEYSTORE_PASSWORD`
  - `WASL_KEY_ALIAS`
  - `WASL_KEY_PASSWORD`
- Workflow الإصدار يبني APK موقعًا، يتحقق منه بـ`apksigner`، ويولد SHA-256.
- لا يوصف التطبيق بأنه **Published** قبل توفير مفتاح التوقيع الخارجي وتشغيل بوابة Signed Release بنجاح.

راجع `PRIVACY_POLICY.md` و`docs/RELEASE_CHECKLIST.md` قبل التوزيع العام.

## المتبقي خارج كود المنتج

إذا اجتازت بوابة Android CI للرأس المجمع النهائي، فلا توجد مرحلة وظيفية مفتوحة داخل الكود. يبقى النشر الخارجي فقط: توفير أسرار التوقيع الفعلية، تشغيل Signed Release، وإدخال بيانات المتجر/وسيلة التواصل الرسمية التي تتطلبها منصة التوزيع.

## ثوابت لا تكسر

- لا حذف Ledger history.
- لا Float/Double للأموال.
- لا خلط عملات في إجمالي واحد.
- Promise/Claim/Reminder/Installment ليست Ledger.
- Notification/Natural/Voice لا تنفذ commit ماليًا مباشرًا.
- PDF يعتمد snapshot ثابتًا.
- لا فتح READY PDF/Attachment عند فقد الملف أو فشل SHA-256.
- لا Restore يتجاوز schema/path/hash/FK/invariant validation.
- لا Migration بلا exported schema + tests.
- لا signing keys أو passwords داخل Git.
