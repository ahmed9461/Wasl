# خطة الاختبارات

آخر تحديث: 2026-08-26

## الهدف

إثبات صحة المال والاستمرارية والأمان والمستندات على تنفيذ Android فعلي، لا مجرد نجاح رسم الشاشة.

## طبقات الاختبار

### Unit — Domain/Application

- Money arithmetic وOverflow وCurrency mismatch.
- Debt state وDue state.
- Partial / Full payment وOverpayment.
- Reversal وDouble reversal وDuplicate IDs.
- Summary حسب العملة والاتجاه.
- Parsing مبالغ YER/SAR/USD والأرقام العربية دون `Double`.
- ViewModel idempotency وإعادة المحاولة بعد نتيجة غير مؤكدة.
- Reminder time/recovery policy.
- App Lock session state والمهلات والمصادقة والتعطيل.

### Database / Repository

- Entity constraints وForeign Keys.
- Transaction ذرية وعدم ترك نصف كتابة.
- Idempotency للـcommand IDs.
- Concurrent payments ومنع Overdraw.
- Replay يطابق Projection والحالة المغلقة.
- Restart وإعادة فتح قاعدة حقيقية.
- Migration من كل Schema مدعوم حتى v7.
- v6→v7 يحفظ إيصالات السداد ويجعل `issued_documents.ledger_entry_id` nullable دون تدمير البيانات.
- promises وinstallment revisions تبقى منفصلة عن Ledger.
- Document snapshots ثابتة ولا تنشئ Ledger entries مصطنعة.

### UI / Compose

- إنشاء دين وتسجيل دفعة وإعادة فتح البيانات.
- تعديل/إلغاء الاستحقاق وAudit.
- Today للاستحقاقات والوعود والأقساط.
- Search وفتح النتيجة والعودة لعبارة البحث.
- إنشاء دين لشخص موجود دون تكرار Person.
- Payment promises وحسمها دون تغيير الرصيد.
- Security Hub وApp Lock recovery.
- Dark Mode + Font Scale 2.0 على شاشة الأمان.
- فحص أن شاشة القفل لا تعرض زر فتح غير صالح عندما تكون مصادقة النظام غير متاحة.

ما زال مطلوبًا قبل Release عام:

- توسيع Font Scale على الشاشات المالية الرئيسية.
- TalkBack يدوي/آلي أوسع.
- Compact/Expanded window coverage أوسع.

### Platform

- Notification permission وحالة القنوات.
- WorkManager Unique Work وRecovery.
- Exact Alarm fallback وعدم فتح إعدادات النظام تلقائيًا.
- تغير الوقت/المنطقة الزمنية.
- FileProvider وSHA-256 قبل فتح/مشاركة PDF.
- Android system authentication عبر BiometricPrompt/Device Credential.
- PDF عربي فعلي متعدد الصفحات.

### Backup / Restore

- Round trip مشفر.
- Wrong passphrase لا يغير الحالة الحية.
- ملفات PDF `READY` تدخل النسخة وتعود ببصمتها.
- رفض المسارات غير الآمنة وSchema غير المدعوم والبصمات غير المطابقة.
- Restore عبر Stage + Room مؤقتة + Foreign key/invariant checks.
- بيانات أضيفت بعد النسخة تختفي بعد Restore الناجح.
- ملف PDF أفسد بعد النسخة يستعاد إلى النسخة الصحيحة.

### End-to-End MVP

`MvpAcceptanceInstrumentedTest.requiredMvpJourneySurvivesRestartReceiptBackupAndRestoreOffline` يغطي على Emulator حقيقي:

1. إنشاء دين `100,000 YER` مع موعد وتذكير.
2. إغلاق وإعادة فتح Room والتأكد من بقاء البيانات.
3. دفعتان `20,000 + 5,000` → رصيد `75,000`.
4. Restart ثانٍ والحفاظ على Ledger.
5. سداد نهائي `75,000` → `SETTLED`.
6. عكس السداد النهائي بسبب موثق → عودة الرصيد `75,000`.
7. تسجيل سداد نهائي بديل → `SETTLED`.
8. إصدار Payment Receipt حقيقي بحالة `READY` والتحقق من الملف وSHA-256.
9. إنشاء Backup مشفر Schema v7.
10. إضافة دين بعد النسخة وإفساد ملف PDF عمدًا.
11. Restore للنسخة.
12. التحقق من رجوع Ledger والحالة والتذكير والمستند والملف وSHA-256، واختفاء البيانات التي أنشئت بعد النسخة.

## اختبارات رئيسية منفذة

| الاختبار | ما يثبته |
|---|---|
| `MoneyTest` | الحساب الصحيح ومنع خلط العملات وOverflow |
| `DebtLedgerTest` | الأصل والجزئي والنهائي والعكس وترتيب الأحداث والاستحقاق |
| `MoneyInputParserTest` | Parsing دقيق للأرقام العربية والعملات دون Floating Point |
| `RoomWaslRepositoryInstrumentedTest` | Restart، الذرية، Idempotency، التزامن، Foreign Keys، Today/Search، due schedule/audit |
| `WaslDatabaseBaselineTest` | Baseline migrations حتى Schema الحالية |
| `AccountDocumentMigrationInstrumentedTest` | v6→v7 وحفظ payment docs والسماح بالمستندات دون ledger source |
| `PaymentReceiptRepositoryInstrumentedTest` | immutable payment snapshots والترقيم والـidempotency |
| `AccountDocumentStoreInstrumentedTest` | Debt receipt/statement snapshots دون Ledger وهمي |
| `PaymentReceiptPdfInstrumentedTest` | PDF عربي RTL/LTR ومبالغ كبيرة ومتعدد الصفحات |
| `AccountDocumentPdfInstrumentedTest` | Debt Receipt وAccount Statement متعدد الصفحات |
| `BackupRestoreInstrumentedTest` | Backup/Restore وكلمة مرور خاطئة دون mutation |
| `AccountDocumentBackupInstrumentedTest` | استعادة Document record + PDF + SHA-256 |
| `SecurityUiInstrumentedTest` | App Lock UI/Recovery وDark + Font Scale 2.0 |
| `AppLockViewModelTest` | Session timeout والمصادقة والعودة والتعطيل |
| `MvpAcceptanceInstrumentedTest` | رحلة MVP المالية/المستند/backup/restore من طرف إلى طرف |

## فحص PDF داخل CI

بعد نجاح Android instrumentation تستخرج CI ملفات حقيقية من مساحة التطبيق ثم تستخدم:

- `pdfinfo` للتحقق من عدد الصفحات.
- `pdftotext` للتحقق من مراجع LTR/English المحددة.
- `pdftoppm` لتحويل الصفحات إلى PNG كدليل بصري آلي.

عينات التحقق الحالية:

- Payment receipt: `PAY-2026-00042`, `AL NOOR TRADING`, `123,456.78 USD`.
- Debt receipt: `DEBT-2026-00043`, `AL NOOR TRADING`.
- Account statement: `STAT-2026-00044`, `REF-35`، ومتعدد الصفحات.

## CI الحالي

كل Push وPull Request يشغل:

- `core:domain:test`.
- `app:testDebugUnitTest`.
- `app:lintDebug`.
- `app:assembleDebug`.
- تصدير Room Schema ومقارنتها بـ`7.json` الملتزم.
- `app:connectedDebugAndroidTest` على Emulator API 35 بعد نجاح Job البناء.
- فحص PDF الفعلي ورفع Artifacts.

آخر بوابة كاملة لكود المرحلة:

- Android CI #382 — Run `32903618216` — head `be7f67d`.
- Android instrumentation: **63/63**.
- failures: 0.
- errors: 0.
- skipped: 0.
- Unit/Lint/APK/Schema/PDF evidence: نجاح كامل.

## بوابات الدمج

- لا اختبارات حمراء.
- لا Lint errors.
- Debug APK يبنى.
- Room Schema المولد يطابق `7.json`.
- Android instrumentation ينجح كاملًا.
- PDF evidence ينجح للأنواع الثلاثة.
- أي Migration جديدة لها اختبار من النسخة السابقة.
- Backup contract يحدث مع أي جدول/ملف يدخل النسخة.
- `SPEC.md` و`HANDOFF.md` والقرارات ذات الصلة محدثة.
- Git diff مراجع.
- لا Release signing secrets داخل المستودع.
- لا دمج إلى `main` دون طلب صريح من صاحب المشروع.
