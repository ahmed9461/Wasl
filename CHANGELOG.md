# سجل التغييرات

آخر مزامنة: 2026-08-29.

## غير منشور — UI/UX Hardening v0.4

### تجربة الاستخدام

- الرئيسية تخفي العملات التي لا يوجد لها رصيد مفتوح بدل عرض بطاقات صفرية ثابتة.
- بطاقات الحساب أصبحت compact وأزيل الـplaceholder الكبير بحرف واحد.
- تحسين التخطيطات الأفقية/adaptive على الهاتف القياسي مع fallback للشاشات الضيقة والخط الكبير.
- ضغط واجهة Group Expense للاتجاه والعملات والمشاركين بدل التدفق العمودي الطويل.
- نقل إجراء تصدير PDF في تفاصيل الحساب إلى المنطقة العلوية اليسرى ضمن Safe Area.
- تجميع إجراءات Payment Promise المعلقة داخل Action Bar منظمة.
- ربط Documents Hub بالـAttachment Store الحقيقي وحماية تشغيل منتقي الملفات.
- إزالة نصوص SHA-256 وصلاحيات/تفاصيل التخزين التقنية من UX اليومي.

### هوية المستندات وصورة الرأس

- رفع Room Schema من v11 إلى **v12** مع Migration `11→12` وإضافة `banner_relative_path` و`banner_sha256` إلى `document_identities`.
- إضافة `DocumentBannerAsset` وapp-private content-addressed vault مع path/hash/image/size validation.
- إضافة `DocumentBannerSnapshotCodec` و`DocumentIdentityBannerMapper` مع fail-closed validation.
- تثبيت البانر المختار داخل immutable snapshots الخاصة بـPayment/Debt/Account Statement مع backward compatibility للمستندات القديمة.
- تحديث PDF renderers للتحقق من الأصل التاريخي ورسم البانر على الصفحة الأولى دون fallback صامت عند فشل integrity.
- تحديث Encrypted Backup/Restore ليحفظ أصل البانر ومرجعه، مع regression test للاستعادة.
- إضافة واجهة compact لاختيار/معاينة/إزالة صورة رأس هوية المستند.
- إضافة اختبارات Room migration وsnapshot/vault/backup وlauncher resources المرتبطة بالجولة.

### التحقق الجاري

- آخر baseline كامل مثبت قبل دفعات البانر الأخيرة: Android CI #1152 — run `33254422017` — head `c990642575ca5635a68f66342828f7d1fb411e49` ✅.
- Android CI #1195 كشف compile error في `DocumentIdentityBannerControls.kt` بسبب import مباشر غير صالح لـ`layout.weight`.
- تم إصلاح السبب في `7129faaccef7dadcffa66bf5f32a7c1653cf4d31` بالاعتماد على `RowScope.weight` الصحيح.
- لا تُغلق v0.4 ولا تُدمج قبل Android CI كامل أخضر على رأس PR #13 النهائي، ثم القبول البصري/العملي المحدد في `docs/UI_UX_HARDENING_V0.4_EXECUTION_PLAN.md`.

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

- exported Room schemas ملتزمة من v1 إلى v11 في baseline 0.1.0؛ v0.4 تضيف v12 على الفرع النشط.
- migrations صريحة دون destructive migration.
- v8: payment claims.
- v9: attachments.
- v10: group expenses + shares.
- v11: document templates.
- v12: document identity banner metadata ضمن v0.4.

### Release engineering

- `versionName = 0.1.0`, `versionCode = 1`.
- إضافة `PRIVACY_POLICY.md`.
- إضافة `docs/RELEASE_CHECKLIST.md`.
- إضافة Signed Release GitHub Actions workflow.
- signing configuration تقرأ الأسرار من environment فقط؛ لا keystore/passwords في Git.
- release workflow يتحقق من APK عبر `apksigner` ويولد SHA-256.

## Verification

### Corrective UI v0.3 — merged baseline

Corrective UI v0.3 دُمجت إلى `main` عند `15f982b9a3804861f96b454431c96ed4f8c19c04`، وAndroid CI #1100 — run `33229515030` على merge commit نجح بالكامل ✅.

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

`main` يحمل Corrective UI v0.3 المدمجة والمتحققة. التطوير النشط الآن هو v0.4 على PR #13، لذلك لا يُسلَّم APK نهائي جديد ولا يوصف رأس v0.4 بأنه مقبول حتى ينجح Android CI الكامل والقبول البصري/العملي على الرأس النهائي، ثم يدمج إلى `main` ويُعاد CI على merge commit. النشر العام الموقّع يبقى منفصلًا ويتطلب مفتاح توقيع خارجي وأسرار Release غير محفوظة في Git.
