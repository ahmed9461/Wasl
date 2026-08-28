# سجل التغييرات

جميع التغييرات المهمة في وَصل تسجل هنا. آخر مزامنة: 2026-08-28.

## Unreleased

### Added

#### الأساس المالي والمتابعة

- Android أصلي بـKotlin وJetpack Compose وMaterial 3 وNavigation 3 مع RTL وLight/Dark.
- `core:domain` مستقل، و`Money` بMinor Units من `Long`.
- Ledger append-only مع Payment وPayment Reversal وIdempotency.
- أشخاص وحسابات متعددة، RECEIVABLE/PAYABLE، وYER/SAR/USD دون خلط العملات.
- Due dates + Audit + Today.
- WorkManager scheduling/recovery، Exact Alarm اختياري، General Reminders.
- Payment Promises، Installment Plans/Revisions، Payment Claims.

#### البحث والمستندات والأمان

- Basic/Advanced Search، Person Timeline، Objective Statistics.
- `PAYMENT_RECEIPT`, `DEBT_RECEIPT`, `ACCOUNT_STATEMENT` من immutable snapshots.
- document numbering، metadata، SHA-256، page count وFileProvider safety checks.
- Backup/Restore مشفر: AES-256-GCM + PBKDF2-HMAC-SHA256 + staging/FK/path/hash/invariant validation + rollback.
- App Lock عبر BiometricPrompt / Device Credential، `FLAG_SECURE` وسياسات خصوصية الإشعارات.
- Attachments/evidence vault داخل مساحة التطبيق مع SHA-256 وrelative paths آمنة.

#### Natural Entry / Voice

- Natural Entry: Parser → Draft/Preview → explicit Confirmation → Save.
- Voice Dictation مع `VoiceDictationBridge` قابل للاختبار.
- حالات recognized / empty / cancelled / unavailable / launch failure.
- recognized speech يغذي Natural Entry نفسه ولا يكتب Ledger قبل التأكيد.

#### Schema v8 / v9 / v10

- **v8:** `payment_claims` لميزة «طالبني» دون Ledger mutation.
- **v9:** `attachments` وخزنة ملفات الإثباتات.
- **v10:** `group_expenses` + `group_expense_shares`.
- Group Expense يحتفظ بالعملية الأصلية كسياق تاريخي، بينما كل share تنشئ Debt عاديًا.
- unequal shares، 2+ مشاركين، عملة/اتجاه موحدان، exact total، atomic transaction، replay/idempotency، conflict detection وrollback.
- Group Expense UI: اختيار فردي/جماعي، تحرير shares، Preview ثم explicit confirmation، retry بنفس IDs، large-font/RTL وoverflow protection.

#### Adaptive / RTL

- `ltrIsolate()` مركزي للبيانات LTR داخل RTL.
- `WaslMaxContentWidth` و`shouldStackDenseRows()`.
- Adaptive hardening عبر Home/Today/Documents/Search/Security/Settings/Account Details/Timeline.
- Large-font/200% Font Scale instrumentation coverage للمناطق المزدحمة.

### Changed

- Room Schema الحالية أصبحت **v10** مع Migrations صريحة v1→v10 دون destructive migration.
- Backup contract أصبح v10 ويشمل **14 جدولًا** إضافة إلى ملفات PDF والمرفقات.
- المستندات المالية تعتمد snapshots ثابتة ولا يعاد تفسير المستند الجاهز من الحالة الحية.
- Notification/Natural/Voice actions لا تنفذ كتابة مالية مباشرة؛ كل كتابة تمر عبر المسار القياسي والتأكيد.
- Group Expense لا تنشئ Ledger موازيًا؛ shares مرتبطة بالديون العادية.
- تم نقل المشروع من مرحلة feature completion/stabilization إلى **final finishing/polish** بعد اكتمال البوابات الوظيفية.

### Fixed / Hardened

- إصلاحات سابقة في General Reminder synchronization، Exact Alarm UX، Payment/Reverse idempotency، Room exports وPDF/Backup regressions.
- إزالة AndroidTest imports القديمة لـ`androidx.compose.ui.test.onNode` دون تخفيف assertions.
- Bidi hardening لرقم المستند/العملة/التواريخ والبيانات التقنية داخل RTL.
- Existing individual-create/PaymentFlow tests عُدّلت لاختيار `create-entry-individual` بعد إضافة picker الفردي/الجماعي، مع بقاء منطق المنتج كما هو.
- Group Expense total overflow يرفض كتحقق بدل crash.
- Voice Dictation cancel/empty/unavailable/launch failure تحافظ على البيانات وتعرض fallback واضحًا.

### Security

- Ledger لا يكتب من Notification callback.
- Natural/Voice لا يحفظ ماليًا دون Preview/Confirmation.
- READY PDF/Attachment لا يفتح إذا فقد الملف أو فشل SHA-256.
- Backup/Restore يرفض schema/path/hash/FK/invariant غير صالح قبل تغيير الحالة الحية.
- App Lock لا يخزن PIN أو كلمة مرور الجهاز أو قالب بصمة.
- Keystore/signing secrets غير ملتزمة في Git.

## Verification history

### Current verified head — post Group Expense + Voice

**Android CI #967 — run `33137676461` — head `e09efee71cea4b1734afe50a025c2a3218ec2dd5`**

- Unit tests ✅
- Lint ✅
- Debug APK ✅
- Room Schema v10 ✅
- Android instrumentation: **123/123**، 0 failures/errors/skipped ✅
- Group Expense UI **4/4** ✅
- Voice Dictation **5/5** ✅
- Natural Entry explicit-confirmation regression ✅
- Legacy individual creation + PaymentFlow regressions ✅
- Payment/Debt/Account Statement PDF evidence ✅
- Instrumentation artifact `9672922910`
- SHA-256 `c5a10dcba796b337d53fcc988f41e2c4aab6bb518bc95734a1e638ef0fdb0a4f`

**Current PR head is Verified.**

### Group Expense UI post-merge

Android CI #959 — run `33135520799` — head `e2832b844f52d27145f24c2005907c3b8181500a`:

- 118/118 instrumentation ✅
- Room v10 + PDF evidence ✅
- artifact SHA-256 `952a13848d878a49c749a8e47abf615cfd4132867ea22f9567bdd00452615fc7`

### Voice functional gate

Android CI #963 — run `33136770143` — functional head `c13c52a84b03202bd842c022184f6ca2f118778c`:

- 123/123 instrumentation ✅
- voice recognized/empty/cancel/unavailable/launch-failure coverage ✅
- Room v10 + PDF evidence ✅
- instrumentation artifact `9672607105`
- SHA-256 `4e0cf315b65293123e4c6a1c75aee8f3ffe781846fc8ec8f1b48ba328e688b9e`

## مرحلة الإنهاء الحالية

بعد الرأس Verified أعلاه، العمل المتبقي هو:

1. مزامنة الوثائق القديمة إلى v10 والحالة Verified.
2. final UI visual polish على دفعات صغيرة دون تغيير behavior/invariants.
3. final PDF polish مع استمرار CI evidence.
4. acceptance gate كاملة.
5. release signing/distribution مع إبقاء المفاتيح والأسرار خارج Git.
