# سجل التغييرات

جميع التغييرات المهمة في وَصل تسجل هنا. آخر مزامنة: 2026-08-27.

## Unreleased

### Added

#### الأساس المالي والواجهة

- Android أصلي بـKotlin وJetpack Compose وMaterial 3 وNavigation 3 مع RTL وLight/Dark.
- `core:domain` مستقل للأموال والديون، و`Money` بوحدات Minor Units من نوع `Long`.
- Ledger append-only مع دفعات جزئية/نهائية وPayment reversal موثق.
- أشخاص وحسابات متعددة للشخص نفسه دون تكرار Person.
- Today، البحث المحلي والمتقدم، Account Details وNavigation بالـIDs.

#### الاستحقاقات والتذكيرات

- Due date قابل للتعديل/الإلغاء مع Audit.
- WorkManager scheduling + recovery.
- Exact Alarm اختياري للمنبه القوي مع fallback.
- General Reminder مستقل عن `due_date`: مرة واحدة/يومي/أسبوعي/شهري.
- REM-006: فتح الحساب / دفع جزء / تم السداد / ذكرني لاحقًا من الإشعارات.
- `ReminderSnooze` بUnique delayed Work دون تغيير Ledger أو `due_date`.

#### الوعود والأقساط

- Payment Promises مستقلة عن Ledger.
- Installment Plans مع `ACTIVE / SUPERSEDED` وRevision history.
- تقدم الأقساط مشتق من العمليات المالية الحقيقية.

#### المستندات

- `PAYMENT_RECEIPT`.
- `DEBT_RECEIPT`.
- `ACCOUNT_STATEMENT` متعدد الصفحات.
- Immutable snapshots، ترقيم سنوي، metadata، SHA-256 وpage count.
- فتح/مشاركة عبر FileProvider بعد فحص السلامة.
- `issued_documents` كسجل عام و`payment_issued_documents` View لإيصالات السداد.

#### Backup / Security

- Backup/Restore تطبيقي يدوي مشفر بكلمة مرور.
- AES-256-GCM + PBKDF2-HMAC-SHA256 + gzip payload.
- Restore staging + temporary Room validation + FK/invariants + rollback.
- App Lock عبر BiometricPrompt / Device Credential.
- `FLAG_SECURE` وسياسات خصوصية الإشعارات.

#### Post-MVP — Schema v8/v9

- **Schema v8:** إضافة `payment_claims` لميزة «طالبني» مع DAO/Store/ViewModel/UI وToday وBackup/Restore validation.
- Claims تدعم `TODAY / TOMORROW / SALARY / CUSTOM` وحالات `ACTIVE / RESOLVED / CANCELLED` دون أي Ledger mutation.
- **Schema v9:** إضافة `attachments` وخزنة ملفات داخلية مع `ledger_entry_id` اختياري وSHA-256 و`relative_path` فريد.
- Attachment file access، UI vault، واختبارات Backup/Restore وفقد/سلامة الملف.
- Person Timeline يجمع حسابات الشخص دون خلط العملات ويعرض Timeline موحدًا.
- Payment message templates/section للنسخ والمشاركة بفعل صريح.
- Objective Statistics وواجهة إحصاءات دون تصنيف الأشخاص.
- Natural Entry: `Parser + Draft + ConfirmationService + UI` مع Preview إلزامي قبل الحفظ.
- **Voice Dictation أساسي** داخل Natural Entry باستخدام Android `RecognizerIntent`; recognized text يمر عبر نفس Parser/Preview/Confirmation.
- Advanced Search وAdaptive Search واختبارات accessibility/large-font أولية.

### Changed

- Room Schema الحالية أصبحت **v9** مع سلسلة Migrations صريحة v1→v9 دون destructive migration.
- Backup contract أصبح Schema v9 ويشمل 12 جدولًا وملفات PDF والمرفقات.
- `issued_documents.ledger_entry_id` أصبح nullable منذ v7 لدعم Debt Receipt/Account Statement دون Ledger entry مصطنع.
- Today يجمع الاستحقاقات والوعود والأقساط والمطالبات مع بقاء كل نموذج مستقلًا عن Ledger.
- إجراءات الإشعار المالية تفتح مسار الدفع القياسي داخل التطبيق ولا تنفذ auto-submit.
- App Lock يعتمد مصادقة Android نفسها بدل PIN خاص بوَصل.
- تم تحديث `README.md`, `PROJECT_CONTEXT.md`, `HANDOFF.md` وملفات `docs/` لتطابق Schema v9 والحالة الفعلية بدل أوصاف v3/v7 القديمة.
- وثائق Natural/Voice Entry أصبحت تميز بين الإملاء الصوتي الأساسي الموجود وبين hardening/tests المتبقية.

### Fixed

- إصلاحات سابقة شملت تزامن General Reminder UI، Exact Alarm UX، Payment/Reverse idempotency، Room schema exports وPDF/Backup regressions.
- في 2026-08-27 كشف Android CI #851 أن `compileDebugAndroidTestKotlin` يتوقف بسبب import قديم غير موجود: `androidx.compose.ui.test.onNode`.
- أزيل import فقط من أربعة اختبارات مع إبقاء `composeRule.onNode(...)` والـAssertions كما هي:
  - `DueDateUiInstrumentedTest.kt`
  - `PersonTimelineUiInstrumentedTest.kt`
  - `StatisticsScreenUiInstrumentedTest.kt`
  - `TodayUiInstrumentedTest.kt`

### Security

- Ledger لا يكتب من Notification callback.
- Natural/Voice input لا يحفظ ماليًا دون Preview/Confirmation.
- PendingIntents الحساسة immutable حيث يلزم.
- READY PDF/Attachment لا يفتح إذا فقد الملف أو فشل SHA-256.
- Backup/Restore يرفض Schema/path/hash/FK/invariant غير صالح قبل تغيير الحالة الحية.
- App Lock لا يخزن PIN أو كلمة مرور الجهاز أو قالب بصمة.
- Keystores والأسرار غير ملتزمة في Git.
- Android Auto Backup/Device Transfer ليسا مسار النسخ المدعوم؛ النسخ المعتمد هو Backup التطبيق المشفر.

## Verification history

### آخر بوابة كاملة مستقرة قبل ميزات v8/v9

**Android CI #485** — Run `32998478006` — head `53faec3cd7007c6a9e318b3fa69a2f955bb2ed4d`:

- Unit tests ✅
- Lint ✅
- Debug APK ✅
- Room Schema v7 ✅
- Android Emulator instrumentation: **70/70** ✅
- REM-006 regressions ✅
- General Reminder regressions ✅
- Payment/Debt/Statement PDF evidence ✅
- App Lock/Font Scale regression ✅
- MVP end-to-end acceptance ✅

Artifacts من #485:

- `Wasl-debug` — `9617704751` — SHA-256 `973a3985dd2c94d29a743488948172b444ef4e341b01005f8f9911d53468d539`.
- `Wasl-room-schema` — `9617705367` — SHA-256 `5fec4cc04f3720ba6bdb4e33499e0ca76e1d3809a68b524948706c79735d9797`.
- `Wasl-payment-receipt-evidence` — `9617967942` — SHA-256 `3e3f0cf2fbb908f68ae4def535c7325d49d7fa31282c21695216d76c8b6c036c`.
- `Wasl-account-document-evidence` — `9617968386` — SHA-256 `557a464d264d97d048d37d6fc52c6aad8cc02dd7e310e4b5d5f439d3fd24717e`.
- `Wasl-room-instrumentation-results` — `9617968909` — SHA-256 `346fb994021ed012d97598aa12b2020cc77e093b59e14aad33ebc3ce0fe9c2cb`.

### Schema v9 pre-fix gate

**Android CI #851** — head `94ce0adf3ff64431a261042ebb62e815b42f13f1`:

- Unit tests ✅
- Lint ✅
- Debug APK ✅
- Room Schema v9 generated/current check ✅
- `payment_claims` / `attachments` schema checks ✅
- Android instrumentation لم تبدأ بسبب compile error في imports الاختبارات الأربعة أعلاه ❌

المرحلتان Claims وAttachments لا توسمان Verified/Complete حتى ينجح full Android CI على الرأس المصحح.
