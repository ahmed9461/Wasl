# سياق مشروع وَصل

آخر تحديث: 2026-08-29

## الهوية

- الاسم: **وَصل — Wasl**
- الشعار: **كل حساب له وصل**
- المستودع: `ahmed9461/Wasl`
- Application ID: `com.wasl.app`
- الإصدار المرشح: `0.1.0` (`versionCode = 1`) إلى أن تُغلق جولة v0.4.
- الفرع النشط: `agent/ui-ux-hardening-v0.4`
- PR النشط: #13 — Draft

## الهدف

وَصل مساعد مالي شخصي Local-first لإدارة الحقوق والالتزامات من الإنشاء حتى الإغلاق: الحسابات، الدفعات، الاستحقاقات، التذكيرات، الوعود، الأقساط، المطالبات، المستندات، المرفقات، النسخ الاحتياطي والحماية المحلية.

ليس بنكًا أو محفظة أو بوابة دفع أو ERP. تسجيل السداد يوثق واقعة داخل التطبيق ولا يحول أموالًا، والمستندات سجلات شخصية وليست ضمانًا قانونيًا تلقائيًا.

## المرحلة الحالية

القلب الوظيفي الأساسي مكتمل، وCorrective UI v0.3 تم دمجها بالفعل في `main` عبر commit:

`15f982b9a3804861f96b454431c96ed4f8c19c04`

Android CI #1100 — run `33229515030` على merge commit في `main` نجح بالكامل.

التطوير النشط الآن هو **UI/UX Hardening v0.4** على PR #13. المرجع التنفيذي الملزم:

`docs/UI_UX_HARDENING_V0.4_EXECUTION_PLAN.md`

هذه الجولة تعالج كثافة وتجربة الواجهة، المرفقات/File Picker، الأيقونة، وهوية المستندات مع صورة رأس/Banner ثابتة تاريخيًا.

## ما أُنجز في v0.4

### الواجهة

- Home يخفي العملات ذات الرصيد المفتوح صفر.
- بطاقات الحساب compact ولا تعرض حرفًا كبيرًا كـplaceholder.
- التخطيط على الهاتف القياسي أكثر أفقية وكثافة مع adaptive fallback عند ضيق العرض أو تكبير الخط.
- Group Expense يستخدم اتجاه/عملة ومشاركين بتخطيط compact/responsive.
- Account Details ينقل إجراء PDF السريع إلى المنطقة العلوية اليسرى.
- Payment Promise actions منظمة داخل Action Bar.
- Documents Hub مربوط بالـAttachment Store الحقيقي.
- Attachment picker محمي والنصوص التقنية الخاصة بالتخزين/SHA-256 لا تظهر في UX اليومي.

### هوية المستند والبانر

- Room Schema على v0.4 أصبحت **v12**.
- Migration `11→12` تضيف `banner_relative_path` و`banner_sha256` إلى `document_identities`.
- `DocumentBannerAsset` يفرض relative path آمنًا وSHA-256 والتحقق من الصورة والحجم.
- يوجد app-private content-addressed banner vault.
- `DocumentBannerSnapshotCodec` و`DocumentIdentityBannerMapper` يحفظان/يفحصان مرجع البانر بصورة fail-closed.
- Payment/Debt/Account Statement snapshots تجمد البانر المختار تاريخيًا مع backward compatibility.
- PDF renderers تتحقق من البانر التاريخي قبل الرسم ولا تستخدم fallback صامتًا عند فشل integrity.
- Encrypted Backup/Restore يحفظ أصل البانر ومرجعه مع اختبار استعادة مخصص.
- Documents UI تحتوي picker/preview/remove مدمجًا للبانر.

## وضع CI

- آخر baseline كامل مثبت قبل دفعات البانر الأخيرة: Android CI #1152 — run `33254422017` — head `c990642575ca5635a68f66342828f7d1fb411e49` ✅.
- Android CI #1195 كشف compile error واحدًا في `DocumentIdentityBannerControls.kt` بسبب import غير صالح لـ`androidx.compose.foundation.layout.weight`.
- تم إصلاح السبب في commit `7129faaccef7dadcffa66bf5f32a7c1653cf4d31` بالاعتماد على `RowScope.weight` بدون الاستيراد المباشر.
- بعد أي commit جديد، يجب اعتماد Android CI الخاص برأس PR #13 الحالي نفسه قبل وصفه بأنه أخضر.

## الوظائف الحالية

- Android أصلي: Kotlin + Compose + Material 3 + Navigation 3.
- Domain مالي مستقل؛ Money بminor units من `Long` فقط.
- Ledger append-only مع Payment/Reversal وidempotency/replay.
- أشخاص وحسابات متعددة، RECEIVABLE/PAYABLE، YER/SAR/USD دون خلط العملات.
- Due/Today/WorkManager/Exact Alarm/General Reminders.
- Promises / Installments / Claims.
- Search / Timeline / Statistics / Documents Hub / Account Details.
- Natural Entry وVoice عبر Preview/Confirmation قبل أي حفظ مالي.
- Group Expense atomic؛ shares تصبح ديونًا عادية ولا يوجد Ledger موازٍ.
- Payment/Debt/Account Statement من immutable snapshots.
- Document Templates مع أنماط MINIMAL/BUSINESS/CLASSIC/COMPACT/MODERN وتجميد اختيار القالب داخل snapshot.
- Attachments vault + SHA-256 + FileProvider.
- Backup/Restore مشفر مع staging/FK/path/hash/invariant validation وrollback.
- App Lock / `FLAG_SECURE` / notification privacy.
- Local-first ولا صلاحية `INTERNET` في الإصدار الحالي.

## قاعدة البيانات

- `main`: Room Schema v11.
- فرع v0.4: Room Schema **v12**.
- exported schemas على الفرع: `1.json → 12.json`.
- v8: `payment_claims`.
- v9: `attachments`.
- v10: `group_expenses` + `group_expense_shares`.
- v11: `document_templates`.
- v12: banner metadata داخل `document_identities`.
- لا destructive migration في Production.

المرجع الحي على الفرع: `app/schemas/com.wasl.app.data.local.WaslDatabase/12.json`.

## Stack

| المجال | القرار |
|---|---|
| المنصة | Android أصلي |
| اللغة | Kotlin 2.3.21 |
| UI | Jetpack Compose + Material 3 |
| المعمارية | UI / Domain / Data مع UDF وRepositories/Stores |
| قاعدة البيانات | Room 2.8.4 + KSP، Schema v12 على فرع v0.4 |
| التنقل | Navigation 3 |
| الأعمال المؤجلة | WorkManager |
| التنبيه القوي | Exact Alarm اختياري مع fallback |
| المصادقة المحلية | BiometricPrompt + Device Credential |
| الإدخال الصوتي | Voice bridge → Natural Parser → Preview/Confirmation |
| PDF | Immutable snapshots + integrity evidence + optional historical banner |
| البناء | AGP 9.3.1، Gradle 9.5.0، JDK 17 |
| API | min 26، compile/target 36 |

## المتبقي لإغلاق v0.4

1. Android CI كامل أخضر على رأس PR #13 النهائي.
2. تثبيت regression coverage النهائي للمرفقات/File Picker.
3. قبول banner end-to-end: اختيار/معاينة/إزالة → snapshot → PDF فعلي → tamper failure → backup/restore.
4. مراجعة بصرية يدوية لكل الشاشات المحددة في خطة v0.4، مع Dark/Light/Auto وRTL وLarge Font.
5. clean-install verification للأيقونة المرجعية وموارد launcher المختلفة.
6. تحديث CHANGELOG وPR body مع الرأس المقبول النهائي.
7. دمج PR #13 إلى `main`.
8. إعادة Android CI على merge commit في `main`، ثم استخراج APK من artifact الخاص بـ`main` وحساب SHA-256.

## الإصدار والنشر

- `PRIVACY_POLICY.md` يصف سلوك الإصدار الحالي.
- `docs/RELEASE_CHECKLIST.md` هي بوابة النشر.
- `.github/workflows/release.yml` يبني APK موقعًا عند توفر الأسرار الخارجية ويؤكد التوقيع بـ`apksigner` ويولد SHA-256.
- signing secrets والkeystore لا تدخل Git.

الأسرار الخارجية عند الاستعداد للنشر العام:

- `WASL_KEYSTORE_BASE64`
- `WASL_KEYSTORE_PASSWORD`
- `WASL_KEY_ALIAS`
- `WASL_KEY_PASSWORD`

## الثوابت المعمارية

1. Ledger مصدر الحقيقة المالي وappend-only.
2. التصحيح بالعكس، لا حذف الحدث المالي الأصلي.
3. Money بأعداد صحيحة/minor units فقط.
4. Promise/Claim/Reminder/Installment ليست Ledger.
5. لا تجمع العملات المختلفة في إجمالي واحد.
6. Group Expense ليست Ledger موازية.
7. PDF/التقارير تعتمد immutable snapshots ولا تعيد تعريف قواعد المال.
8. Notification/Natural/Voice لا تنفذ كتابة مالية قبل المراجعة والتأكيد.
9. الملفات المهمة تفحص بـSHA-256.
10. كل Schema جديدة معها Migration + exported schema + tests.
11. لا secrets/signing keys في Git.
