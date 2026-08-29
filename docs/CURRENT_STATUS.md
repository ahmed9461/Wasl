# وَصل — الحالة الحالية

آخر مراجعة: 2026-08-29

هذا الملف يلخص الحالة الحية. عند التعارض يكون مصدر الحقيقة بالترتيب: الكود على الرأس الحالي → Room exported schema → GitHub Actions للرأس نفسه → هذا الملف → بقية الوثائق.

## المرشح الحالي

- المستودع: `ahmed9461/Wasl`.
- `main`: يحتوي Corrective UI v0.3 بعد دمج PR #12.
- merge commit المرجعي على `main`: `15f982b9a3804861f96b454431c96ed4f8c19c04`.
- Android CI #1100 — run `33229515030` على merge commit في `main` نجح بالكامل.
- الجولة المفتوحة: **UI/UX Hardening v0.4**.
- الفرع: `agent/ui-ux-hardening-v0.4`.
- PR: #13 — Draft حتى اكتمال القبول البصري والدمج.
- آخر functional-code checkpoint قبل مزامنة الوثائق: `9d0622f26ab6760d68362adf279da4e7e2ca69c7`.
- Android CI #1248 — run `33274101583` على هذا checkpoint نجح بالكامل ✅.
- instrumentation على المحاكي: **149 اختبارًا، 0 failures، 0 errors، 0 skipped**.
- Room على فرع v0.4: **Schema v12** مع exported schemas `1.json → 12.json`.
- الإصدار المرشح ما زال `0.1.0`، `versionCode = 1`.
- لا `fallbackToDestructiveMigration` في Production.

## المرجع التنفيذي لجولة v0.4

`docs/UI_UX_HARDENING_V0.4_EXECUTION_PLAN.md`

نجاح CI لا يغلق الجولة وحده؛ القبول البصري/العملي اليدوي في الخطة ما زال إلزاميًا قبل الدمج.

## المنجز في UI/UX Hardening v0.4

### كثافة الواجهة والتكيف

- Home يخفي العملات ذات الرصيد المفتوح صفر.
- بطاقات الحساب compact بدون Avatar/placeholder ضخم.
- تخطيطات الهاتف القياسي أكثر أفقية وكثافة مع adaptive fallback للشاشات الضيقة والخط الكبير.
- Group Expense يستخدم اتجاه/عملة ومشاركين بتخطيط compact/responsive.
- إجراء PDF السريع في Account Details موجود أعلى اليسار داخل Safe Area.
- Payment Promise actions مجمعة داخل Action Bar منظمة، وتغيير حالة الوعد لا يكتب دفعة مالية أو يغيّر أصل الدين/الاستحقاق.
- Documents Hub مربوط بالـAttachment Store الحقيقي.
- النصوص اليومية لا تعرض SHA-256 أو تفاصيل التخزين التقنية للمستخدم.

### File Picker والمرفقات

- مسار Activity Result / OpenDocument محمي من الإلغاء و`uri == null` وفشل القراءة.
- regression instrumentation يغطي: cancel، PNG سليمة، PDF سليم، URI غير قابل للقراءة، وفشل integrity.
- المرفقات تُحفظ داخل الخزنة الخاصة بالتطبيق وتفتح/تشارك فقط بعد اجتياز فحص السلامة.

### Room v12 + Document Banner

- Migration `11→12` تضيف `document_identities.banner_relative_path` و`banner_sha256` كحقول اختيارية.
- `DocumentBannerAsset` + app-private content-addressed vault مع path/hash/image/size validation.
- `DocumentBannerSnapshotCodec` وmapper يحفظان مرجعًا immutable ويعملان fail-closed.
- Payment/Debt/Account Statement snapshots تجمد البانر المختار تاريخيًا مع backward compatibility.
- PDF renderers تتحقق من البانر التاريخي قبل الرسم على الصفحة الأولى، والعبث يفشل مغلقًا.
- Encrypted Backup/Restore يحفظ أصل البانر ومرجعه ويستعيدهما مع التحقق.
- UI تدعم اختيار/معاينة/تغيير/إزالة البانر.
- بعد الاختيار، يظهر قص/تموضع فعلي ضمن نسبة رأس PDF مع تحكم أفقي ورأسي قبل الحفظ.
- القص النهائي يخرج صورة رأس بجودة محدودة عمليًا حتى 1800px عرضًا؛ معاينة Compose تستخدم decode مصغرًا حتى 1600px لتقليل الذاكرة.
- إصدار إيصال الدين/كشف الحساب يمر عبر **Preview قبل الإصدار** ولا يكتب المستند حتى التأكيد الصريح.
- اختبارات البانر تغطي ratio/focus، الصور الصغيرة جدًا، downsampling، snapshot التاريخي، PDF الفعلي، tamper failure، وbackup/restore.

### Launcher

- موارد legacy/adaptive/round موجودة ومربوطة بالهوية الحالية.
- Android 13+ monochrome resource مغطى باختبار instrumentation.
- الاختبار الآلي يثبت packaging/resources؛ شكل الأيقونة الفعلي بعد clean install على Launcher ما زال بوابة بصرية يدوية.

## بوابة التحقق الحالية

Android CI #1248 — run `33274101583` — head `9d0622f26ab6760d68362adf279da4e7e2ca69c7` ✅

- `:core:domain:test` ✅
- `:app:testDebugUnitTest` ✅
- `:app:lintDebug` ✅
- `:app:assembleDebug` ✅
- Room Schema v12 generated/verified ✅
- `connectedDebugAndroidTest`: **149 tests / 0 failures / 0 errors / 0 skipped** ✅
- Payment Receipt PDF inspection ✅
- Debt Receipt PDF inspection ✅
- Account Statement PDF inspection ✅
- artifacts: `Wasl-debug`, `Wasl-room-schema`, `Wasl-room-instrumentation-results`, `Wasl-payment-receipt-evidence`, `Wasl-account-document-evidence` ✅

مزامنة الوثائق الحالية تغيّر الرأس فقط دون تغيير الكود؛ لذلك يجب أن ينجح Android CI النهائي على commit الوثائق أيضًا قبل القبول اليدوي.

## قاعدة البيانات الحالية على v0.4

- Room Schema: **v12**.
- exported schemas: `1.json → 12.json`.
- v8: `payment_claims`.
- v9: `attachments`.
- v10: `group_expenses` + `group_expense_shares`.
- v11: `document_templates`.
- v12: banner metadata داخل `document_identities`.

## المتبقي لإغلاق v0.4

1. نجاح CI النهائي على commit مزامنة الوثائق.
2. تثبيت APK debug من رأس PR النهائي وإجراء مراجعة بصرية يدوية: Home، multi-currency، Add Account، Group Expense، Account Details، Promise states، Documents/Attachments، banner crop/preview/PDF، Dark/Light/Auto، RTL، Large Font.
3. clean install على Android حديث والتحقق بصريًا من أيقونة Launcher المرجعية، بما يشمل round/themed/monochrome حيث ينطبق.
4. إبقاء PR #13 Draft حتى اعتماد القبول البصري؛ ثم تحويله Ready ودمجه إلى `main`.
5. تشغيل Android CI على merge commit في `main`، ثم استخراج APK الاختبار النهائي من artifact الخاص بـ`main` فقط وحساب SHA-256.

## النشر العام

Signed Release منفصل عن إغلاق v0.4 ويحتاج أسرار التوقيع الخارجية عند الاستعداد للنشر:

- `WASL_KEYSTORE_BASE64`
- `WASL_KEYSTORE_PASSWORD`
- `WASL_KEY_ALIAS`
- `WASL_KEY_PASSWORD`

لا signing keys أو passwords داخل Git، ولا يوصف التطبيق بأنه Published قبل Signed Release فعلي.

## ثوابت لا تكسر

1. Ledger append-only؛ التصحيح بالعكس لا بحذف التاريخ.
2. لا Float/Double للأموال.
3. لا cross-currency netting.
4. Promise/Claim/Reminder/Installment ليست Ledger.
5. Notification/Natural/Voice لا تنفذ financial commit قبل Preview/Confirmation.
6. Group Expense لا تنشئ Ledger موازيًا.
7. المستند READY مبني على immutable snapshot.
8. لا فتح PDF/Attachment عند فقد الملف أو فشل SHA-256.
9. Restore يفحص schema/path/hash/FK/invariants قبل الاستبدال.
10. لا Migration بلا exported schema + tests.
11. لا signing keys أو passwords داخل Git.
