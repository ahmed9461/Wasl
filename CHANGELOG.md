# سجل التغييرات

آخر مزامنة: 2026-08-29.

## واجهة احترافية — Corrective UI v0.3

- اعتماد اللوحة المرئية التي وافق عليها المالك كمرجع تصميم ملزم بدل التحسينات العامة السابقة.
- إعادة بناء الرئيسية بهوية داكنة مدمجة، ملخص عملات مستقل، حسابات مختصرة، وزرين منفصلين لإضافة الحساب والإدخال الذكي.
- إعادة بناء إضافة الحساب كتدفق قصير وعملي؛ الاستحقاق والتذكير والمنبه ضمن خيارات إضافية بدل نموذج طولي مزدحم.
- إعادة بناء «اليوم» بملخص صغير وصياغات عربية صحيحة وإزالة النص غير الطبيعي «اليوم لديك N أمور».
- إعادة بناء الأقساط بملخص إجمالي/مسدد/متبقٍ وفلاتر وبطاقات تقدم مدمجة.
- إعادة بناء تفاصيل الحساب برصيد بارز، تقدم السداد، إجراءات داخلية، وبيانات متابعة مختصرة بدل زر عائم.
- ضغط الإعدادات إلى مجموعات متناسقة مع نفس الهوية البصرية مع الحفاظ على تلقائي/داكن/فاتح والأمان والنسخ الاحتياطي.
- استبدال الأيقونة السابقة بأيقونة «وصل» الذهبية ذات الخلفية الداكنة واللمسة الفيروزية، مع دعم adaptive/round launchers.
- المحافظة على RTL، الخطوط الكبيرة، الاختبارات التكيفية، وجميع ثوابت Ledger وسلامة البيانات المالية.
- تثبيت جولة التصحيح بتعريف Typography صريح، واستدعاءات Material 3 سليمة، واستعادة حالة DatePicker للأقساط.
- تثبيت معرفات UI مستقرة للاختبارات بدل الاعتماد على النصوص المرئية، وتحديث تدفقات الاختبار لتطابق إضافة الحساب المباشرة الجديدة.

### بوابة القبول النهائية للواجهة

Android CI **#1097** — run `33228386198` — head `acb5dea0fd54897afcc56e55ee52afc99bcb0392`:

- Unit tests / Lint / Debug APK ✅
- Room Schema v11 generated/verified ✅
- Emulator instrumentation / migrations / repository / backup ✅
- جميع اختبارات Android على المحاكي ✅
- Payment Receipt PDF inspection ✅
- Debt Receipt PDF inspection ✅
- Account Statement PDF inspection ✅
- instrumentation وPDF evidence artifacts ✅

## 0.1.0 — Release Candidate

### الأساس المالي

- تطبيق Android أصلي بـKotlin/Compose/Material 3/Navigation 3.
- `core:domain` مستقل وMoney بminor units من `Long`.
- Ledger append-only مع Payment / Payment Reversal وidempotency/replay.
- أشخاص وحسابات متعددة، RECEIVABLE/PAYABLE، وYER/SAR/USD دون خلط العملات.

### المتابعة

- Due dates + Audit + Today.
- WorkManager scheduling/recovery وExact Alarm اختياري.
- General Reminders.
- Payment Promises.
- Installment Plans/Revisions.
- Payment Claims «طالبني».

### البحث والعرض

- Basic/Advanced Search.
- Person Timeline.
- Objective Statistics.
- Documents Hub وAccount Details timeline.
- Adaptive/RTL/Bidi hardening واختبارات large-font للمناطق الرئيسية.

### الإدخال الطبيعي والصوتي

- Natural Entry: Parser → Preview → explicit Confirmation → Save.
- Voice Dictation بحالات recognized/empty/cancelled/unavailable/launch failure.
- لا حفظ مالي مباشر من الصوت أو الإشعار.

### Group Expense

- العملية الأصلية context تاريخي وليست Ledger موازيًا.
- كل share تتحول إلى Debt عادي.
- 2+ مشاركين، unequal shares، عملة واتجاه موحدان، exact total.
- atomic transaction + replay/idempotency + conflict detection + rollback.
- Preview/Confirmation إلزاميان.

### المستندات

- `PAYMENT_RECEIPT`, `DEBT_RECEIPT`, `ACCOUNT_STATEMENT` من immutable snapshots.
- numbering + page count + SHA-256 + integrity checks.
- Room Schema v11 تضيف `document_templates`.
- قوالب MINIMAL / BUSINESS / CLASSIC / COMPACT / MODERN.
- القالب المختار يثبت داخل snapshot؛ المستندات القديمة تحتفظ بتوافقها ولا تتغير بسبب إعدادات لاحقة.
- Payment/Debt/Account Statement PDF evidence ضمن CI.

### المرفقات والنسخ والأمان

- Attachments/evidence vault داخل مساحة التطبيق مع SHA-256 ومسارات آمنة.
- FileProvider للمشاركة الصريحة وفحوص integrity.
- Backup/Restore مشفر مع staging + schema/path/hash/FK/invariant validation + rollback.
- App Lock عبر BiometricPrompt/Device Credential.
- `FLAG_SECURE` وسياسة خصوصية للإشعارات الحساسة.
- Local-first، ولا صلاحية `INTERNET` في الإصدار الحالي.

### قاعدة البيانات

- exported Room schemas ملتزمة من v1 إلى v11.
- migrations صريحة دون destructive migration.
- v8: payment claims.
- v9: attachments.
- v10: group expenses + shares.
- v11: document templates.

### Release engineering

- `versionName = 0.1.0`, `versionCode = 1`.
- إضافة `PRIVACY_POLICY.md`.
- إضافة `docs/RELEASE_CHECKLIST.md`.
- إضافة Signed Release GitHub Actions workflow.
- signing configuration تقرأ الأسرار من environment فقط؛ لا keystore/passwords في Git.
- release workflow يتحقق من APK عبر `apksigner` ويولد SHA-256.

## Verification

### Corrective UI v0.3 pre-merge gate

Android CI **#1097** — run `33228386198` — head `acb5dea0fd54897afcc56e55ee52afc99bcb0392` نجح بالكامل كما هو موضح أعلاه.

### Document Templates / Room v11 pre-merge gate

Android CI #1017 — run `33203634720` — head `fdbb28b2aca59f7d0542eaa785d72502d695a431`:

- Unit/Lint/Debug APK ✅
- Room Schema v11 generated/verified ✅
- Emulator integration/migration/repository/backup ✅
- Payment/Debt/Account Statement PDF evidence ✅

### Functional baseline

Android CI #967 — run `33137676461` — head `e09efee71cea4b1734afe50a025c2a3218ec2dd5`:

- 123/123 instrumentation، 0 failures/errors/skips ✅
- Group Expense / Voice / Natural Entry / legacy PaymentFlow regressions ✅
- Room v10 + PDF evidence ✅

## حالة الإصدار

المصدر بعد Corrective UI v0.3 في مرحلة Release Candidate. بوابة الكود والواجهة الحالية نجحت على CI #1097. بعد دمج فرع الواجهة في `main` يجب اعتماد Android CI الناتج من merge نفسه قبل تسليم APK التجريبي النهائي. النشر العام الموقّع يبقى منفصلًا ويتطلب مفتاح توقيع خارجي وأسرار Release غير محفوظة في Git.