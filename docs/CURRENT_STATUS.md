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
- PR: #13 — Draft حتى اكتمال بوابات v0.4.
- الرأس الحالي بعد إصلاح compile للبانر: `7129faaccef7dadcffa66bf5f32a7c1653cf4d31`.
- Android CI #1198 — run `33269879066` يعمل على هذا الرأس وقت تحديث الملف.
- الإصدار المرشح ما زال `0.1.0`، `versionCode = 1` إلى أن تُغلق جولة v0.4 ويُتخذ قرار الإصدار النهائي.
- Room على فرع v0.4: **Schema v12** مع exported schemas `1.json → 12.json`.
- لا `fallbackToDestructiveMigration` في Production.

## المرجع التنفيذي لجولة v0.4

`docs/UI_UX_HARDENING_V0.4_EXECUTION_PLAN.md`

لا تعتبر الجولة مكتملة لمجرد نجاح CI؛ يجب أيضًا اجتياز شروط القبول البصري والعملي المحددة في الخطة.

## ما أُغلق قبل v0.4

القلب الوظيفي الأساسي مستقر وموجود على baseline المدمج:

- أشخاص وحسابات متعددة، RECEIVABLE/PAYABLE، YER/SAR/USD دون cross-currency netting.
- Money بminor units من `Long` فقط.
- Ledger append-only مع Payment/Reversal وpartial/final payments وidempotency/replay.
- Due/Today/WorkManager/Exact Alarm/General Reminders.
- Payment Promises / Installments / Payment Claims.
- Basic/Advanced Search / Timeline / Statistics / Documents Hub / Account Details.
- Natural Entry + Voice عبر Preview/explicit Confirmation قبل أي كتابة مالية.
- Group Expense atomic؛ shares تتحول إلى ديون وَصل العادية ولا يوجد Ledger موازٍ.
- Payment Receipt / Debt Receipt / Account Statement من immutable snapshots.
- Document Templates v11 مع MINIMAL/BUSINESS/CLASSIC/COMPACT/MODERN.
- Attachments vault + FileProvider + integrity checks.
- Encrypted Backup/Restore مع staging/FK/path/hash/invariant validation وrollback.
- App Lock / `FLAG_SECURE` / notification privacy.
- Local-first ولا صلاحية `INTERNET` في الإصدار الحالي.

## المنجز في UI/UX Hardening v0.4

### Batch 1

- الرئيسية لا تعرض العملات التي أرصدتها المفتوحة صفر.
- بطاقات الحساب compact ولا تعتمد Avatar/حرفًا كبيرًا غير مفهوم.
- التخطيط على الهاتف القياسي أصبح أكثر أفقية وكثافة، مع fallback adaptive للشاشات الضيقة والخط الكبير.
- Group Expense يستخدم اتجاه/عملة وتخطيط مشاركين أكثر compact/responsive.
- زر PDF السريع في تفاصيل الحساب نُقل إلى المنطقة العلوية اليسرى.
- إجراءات Payment Promise المعلقة جُمعت في Action Bar منظمة.
- Documents Hub مربوط بالـAttachment Store الحقيقي.
- تشغيل Attachment picker محمي، والنصوص اليومية لا تعرض SHA-256 أو تفاصيل صلاحيات التخزين للمستخدم.

المرجع: `docs/V04_BATCH1_PROGRESS.md`.

### Room v12 + Document Banner

- Migration `11→12` تضيف `document_identities.banner_relative_path` و`banner_sha256` كحقول اختيارية.
- `DocumentBannerAsset` يفرض relative path آمنًا وSHA-256 والتحقق من الصورة والحدود الحجمية.
- `DocumentBannerSnapshotCodec` يحفظ مرجعًا immutable للبانر.
- هوية المستند تحفظ metadata البانر في Room بدل إسقاطها.
- Payment/Debt/Account Statement snapshots تجمد البانر المختار تاريخيًا مع backward compatibility.
- PDF renderers تتحقق من الأصل التاريخي قبل رسم البانر على الصفحة الأولى، بدون fallback صامت عند فشل integrity.
- encrypted Backup/Restore يحفظ أصل البانر ومرجعه، مع اختبار استعادة مخصص.
- واجهة هوية المستند تحتوي picker/preview/remove مدمجًا للبانر.
- تمت إضافة اختبارات migration/vault/snapshot/backup وlauncher resources ذات صلة بالجولة.

المرجع: `docs/V04_BANNER_CORE_PROGRESS.md` وPR #13.

## بوابات التحقق

### baseline المدمج

Android CI #1100 — `main` / `15f982b9...` ✅

### v0.4

- آخر baseline كامل مثبت قبل الدفعات الأخيرة: Android CI #1152 — run `33254422017` — head `c990642575ca5635a68f66342828f7d1fb411e49` ✅.
- دفعات البانر اللاحقة لديها workflows تطبيق/اختبار متخصصة ناجحة، لكن لا تُغني عن Android CI الكامل.
- Android CI #1195 على `624b6f18...` فشل في compile بسبب import مباشر غير صالح لـ`androidx.compose.foundation.layout.weight` داخل `DocumentIdentityBannerControls.kt`.
- تم إصلاح السبب على `7129faac...` بإزالة الاستيراد والاعتماد على `RowScope.weight` الصحيح.
- Android CI #1198 يعمل على الرأس المصحح وقت كتابة هذا الملف؛ لا يوصف الرأس بأنه أخضر قبل اكتماله بنجاح.

## قاعدة البيانات الحالية على v0.4

- Room Schema: **v12**.
- exported schemas: `1.json → 12.json`.
- v8: `payment_claims`.
- v9: `attachments`.
- v10: `group_expenses` + `group_expense_shares`.
- v11: `document_templates`.
- v12: banner metadata داخل `document_identities`.
- كل Migration جديدة يجب أن تأتي مع exported schema + migration tests + تحديث Backup/Restore عند الحاجة.

## المتبقي لإغلاق v0.4

1. نجاح Android CI الكامل على الرأس الحالي بعد آخر تغييرات banner UI.
2. استكمال/تثبيت regression coverage للمرفقات وFile Picker: cancel / valid image / valid PDF / unreadable URI / integrity failure.
3. التحقق النهائي من banner picker/preview/remove وإصدار PDF يحتوي البانر فعليًا.
4. مراجعة بصرية يدوية للشاشات المحددة في خطة v0.4: Home، Group Expense، Account Details، Promises، Attachments، Documents، Dark/Light/Auto، RTL، Large Font.
5. clean-install verification لأيقونة Launcher المرجعية، بما يشمل legacy/adaptive/round/monochrome حيث ينطبق.
6. مزامنة PR/HANDOFF/PROJECT_CONTEXT/CHANGELOG مع الرأس المقبول النهائي.
7. دمج PR #13 إلى `main` فقط بعد اكتمال بوابة v0.4.
8. إعادة Android CI على merge commit في `main` واستخراج APK من artifact الخاص بـ`main` فقط، ثم حساب SHA-256.

## النشر العام

Signed Release منفصل عن إغلاق v0.4 ويحتاج أسرار التوقيع الخارجية فقط عند الاستعداد للنشر:

- `WASL_KEYSTORE_BASE64`
- `WASL_KEYSTORE_PASSWORD`
- `WASL_KEY_ALIAS`
- `WASL_KEY_PASSWORD`

لا يوصف التطبيق بأنه Published قبل نجاح Signed Release واستكمال بيانات منصة التوزيع.

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
