# سياق مشروع وَصل

آخر تحديث: 2026-08-29

## الهوية

- الاسم: **وَصل — Wasl**
- الشعار: **كل حساب له وصل**
- المستودع: `ahmed9461/Wasl`
- Application ID: `com.wasl.app`
- الإصدار المرشح: `0.1.0` (`versionCode = 1`).
- الفرع النشط: `agent/ui-ux-hardening-v0.4`
- PR النشط: #13 — Draft

## الهدف

وَصل مساعد مالي شخصي Local-first لإدارة الحقوق والالتزامات من الإنشاء حتى الإغلاق: الحسابات، الدفعات، الاستحقاقات، التذكيرات، الوعود، الأقساط، المطالبات، المستندات، المرفقات، النسخ الاحتياطي والحماية المحلية.

ليس بنكًا أو محفظة أو بوابة دفع أو ERP. تسجيل السداد يوثق واقعة داخل التطبيق ولا يحول أموالًا، والمستندات سجلات شخصية وليست ضمانًا قانونيًا تلقائيًا.

## المرحلة الحالية

Corrective UI v0.3 مدمجة في `main` عند:

`15f982b9a3804861f96b454431c96ed4f8c19c04`

Android CI #1100 على merge commit في `main` نجح بالكامل.

التطوير النشط هو **UI/UX Hardening v0.4** على PR #13، ومرجعه:

`docs/UI_UX_HARDENING_V0.4_EXECUTION_PLAN.md`

functional-code checkpoint المثبت قبل مزامنة الوثائق:

`9d0622f26ab6760d68362adf279da4e7e2ca69c7`

Android CI #1248 — run `33274101583` عليه نجح بالكامل، بما في ذلك **149 instrumentation tests بدون أي failure/error/skip** وفحص ملفات PDF الثلاثة.

## ما أُنجز في v0.4

### الواجهة

- Home يخفي العملات ذات الرصيد المفتوح صفر.
- account cards compact ولا تعرض placeholder ضخمًا.
- التخطيط adaptive للهاتف القياسي والشاشات الضيقة وLarge Font.
- Group Expense compact/responsive للاتجاه والعملات والمشاركين.
- Account Details يضع PDF quick action أعلى اليسار داخل Safe Area.
- Payment Promise actions منظمة، ولا تؤثر حالة الوعد على Ledger أو أصل الاستحقاق.
- Documents Hub مربوط بالـAttachment Store الحقيقي.

### المرفقات

- File Picker مبني على OpenDocument ومسارات الفشل/الإلغاء لا تسبب crash.
- regression tests تغطي cancel، PNG، PDF، unreadable URI، وintegrity failure.
- الملفات داخل app-private vault وتفتح/تشارك فقط بعد اجتياز integrity.

### هوية المستند والبانر

- Room Schema على v0.4: **v12**.
- Migration `11→12` تضيف `banner_relative_path` و`banner_sha256` إلى `document_identities`.
- `DocumentBannerAsset` + content-addressed app-private vault مع path/hash/image/size validation.
- snapshot codec/mapper يحفظان مرجع البانر fail-closed.
- Payment/Debt/Account Statement snapshots تجمد البانر تاريخيًا.
- PDF renderers تتحقق من البانر قبل الرسم؛ tampered banner يفشل قبل كتابة PDF.
- Backup/Restore يحفظ البانر ومرجعه.
- Documents UI تدعم اختيار/معاينة/تغيير/إزالة البانر.
- `DocumentBannerCropper` يطبق crop/reposition حقيقيًا بنسبة رأس PDF مع focus أفقي/رأسي، ويدعم الصور الصغيرة جدًا.
- final banner output حتى 1800px عرضًا؛ preview decode حتى 1600px لتقليل الذاكرة.
- `DocumentIssuePreviewDialog` يفرض Preview/explicit confirmation قبل إصدار Debt Receipt أو Account Statement.

### Launcher وCI

- launcher resources: legacy/adaptive/round/monochrome.
- Android 13+ monochrome resource مغطى آليًا؛ clean-install visual acceptance يدوي.
- CI لا يكرر push+PR runs لفروع العمل، وconcurrency مهيأة لتجنب التعليق خلف تشغيلات قديمة.

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
- Document Templates: MINIMAL/BUSINESS/CLASSIC/COMPACT/MODERN مع تثبيت القالب في snapshot.
- Attachments vault + SHA-256 + FileProvider.
- Backup/Restore مشفر مع staging/FK/path/hash/invariant validation وrollback.
- App Lock / `FLAG_SECURE` / notification privacy.
- Local-first ولا صلاحية `INTERNET` في الإصدار الحالي.

## قاعدة البيانات

- `main`: Room Schema v11.
- فرع v0.4: Room Schema **v12**.
- exported schemas: `1.json → 12.json`.
- v8: `payment_claims`.
- v9: `attachments`.
- v10: `group_expenses` + `group_expense_shares`.
- v11: `document_templates`.
- v12: document identity banner metadata.
- لا destructive migration في Production.

المرجع الحي: `app/schemas/com.wasl.app.data.local.WaslDatabase/12.json`.

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

1. CI النهائي على commit مزامنة الوثائق.
2. مراجعة بصرية يدوية للشاشات المطلوبة، مع Dark/Light/Auto وRTL وLarge Font.
3. clean install والتحقق البصري من launcher icon الفعلية، بما يشمل themed/round behavior حيث ينطبق.
4. بعد القبول فقط: جعل PR #13 Ready ودمجه إلى `main`.
5. Android CI على merge commit في `main` ثم استخراج APK الاختبار النهائي من artifact الخاص بـ`main` وحساب SHA-256.

## الإصدار والنشر

- `PRIVACY_POLICY.md` يصف سلوك الإصدار الحالي.
- `docs/RELEASE_CHECKLIST.md` بوابة النشر.
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
