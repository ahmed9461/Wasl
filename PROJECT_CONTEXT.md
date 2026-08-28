# سياق مشروع وَصل

آخر تحديث: 2026-08-28

## الهوية

- الاسم: **وَصل — Wasl**
- الشعار: **كل حساب له وصل**
- المستودع: `ahmed9461/Wasl`
- Application ID: `com.wasl.app`
- فرع التطوير الرئيسي: `agent/bootstrap-wasl-foundation`
- PR #1 إلى `main`: Draft ومفتوح.

## الهدف

وَصل مساعد مالي شخصي Local-first لإدارة الحقوق والالتزامات من الإنشاء حتى الإغلاق: حسابات الأشخاص، الدفعات، الاستحقاقات، المتابعة، الوعود، الأقساط، المطالبات، المستندات، المرفقات، النسخ الاحتياطي والحماية المحلية.

وَصل ليس بنكًا أو محفظة أو بوابة دفع أو ERP. تسجيل السداد يوثق واقعة داخل التطبيق ولا يحول أموالًا، والمستندات سجلات شخصية وليست ضمانًا قانونيًا تلقائيًا.

## المرحلة الحالية

المشروع أغلق المراحل الوظيفية الرئيسية ودخل **Finishing / final polish**.

الرأس الموثق الحالي: `e09efee71cea4b1734afe50a025c2a3218ec2dd5`.

Android CI #967 / run `33137676461` على هذا الرأس:

- Unit/Lint/Debug/Room v10 ✅
- Android instrumentation **123/123** ✅
- 0 failures / 0 errors / 0 skipped
- PDF evidence للأنواع الثلاثة ✅

## الوظائف الحالية

- Android أصلي: Kotlin + Jetpack Compose + Material 3 + Navigation 3.
- `core:domain` مستقل عن Android للمال والLedger والقواعد الأساسية.
- `Money` بMinor Units من `Long` فقط.
- Ledger append-only مع Payment وPayment Reversal وIdempotency.
- أشخاص وحسابات متعددة للشخص.
- RECEIVABLE / PAYABLE مع YER/SAR/USD دون خلط العملات.
- Due dates + Audit + Today.
- WorkManager + Exact Alarm اختياري + General Reminders.
- Payment Promises وInstallment Plans/Revisions وPayment Claims.
- Basic/Advanced Search، Person Timeline، Statistics، Documents Hub، Account Details timeline.
- PDF: `PAYMENT_RECEIPT`, `DEBT_RECEIPT`, `ACCOUNT_STATEMENT` من immutable snapshots مع hash/page count.
- Attachments/evidence vault مع internal storage وSHA-256.
- Backup/Restore مشفر مع staging/FK/path/hash/invariant validation وrollback.
- App Lock عبر BiometricPrompt / Device Credential، `FLAG_SECURE` وسياسة خصوصية الإشعارات.
- Natural Entry: Parse → Preview → explicit Confirmation → Save.
- Voice Dictation testable adapter وحالات recognized/empty/cancelled/unavailable/launch failure.
- Group Expense v10: العملية الأصلية محفوظة كسياق، وكل حصة Debt عادي، مع atomic transaction وreplay/rollback وPreview/Confirmation في UI.
- Adaptive/Accessibility hardening وRTL/Bidi isolation على الشاشات الرئيسية مع large-font tests.

## قاعدة البيانات

- Room Schema الحالي: **v10**.
- سلسلة Migrations: `v1→v2→v3→v4→v5→v6→v7→v8→v9→v10`.
- لا `fallbackToDestructiveMigration` في Production.
- v8: `payment_claims`.
- v9: `attachments`.
- v10: `group_expenses` + `group_expense_shares`.
- Backup contract v10 يشمل **14 جدولًا** بالإضافة إلى ملفات PDF والمرفقات.

الملف المرجعي: `app/schemas/com.wasl.app.data.local.WaslDatabase/10.json`.

## Stack

| المجال | القرار |
|---|---|
| المنصة | Android أصلي |
| اللغة | Kotlin |
| UI | Jetpack Compose + Material 3 |
| المعمارية | UI / Domain / Data مع UDF وRepositories/Stores |
| قاعدة البيانات | Room 2.8.4 + KSP، Schema v10 |
| التنقل | Navigation 3 |
| الأعمال المؤجلة | WorkManager |
| التنبيه القوي | Exact Alarm اختياري مع fallback |
| المصادقة المحلية | BiometricPrompt + Device Credential |
| الإدخال الصوتي | Voice bridge → Natural Parser → Preview/Confirmation |
| PDF | Android PdfDocument/Text layout من Snapshots ثابتة |
| البناء | AGP 9.3.1، Gradle 9.5.0، JDK 17 |
| API | min 26، compile/target 36 |

## الثوابت المعمارية

1. Ledger مصدر الحقيقة المالي وappend-only.
2. التصحيح بالعكس، لا حذف/تعديل الحدث الأصلي.
3. Promise/Claim/Reminder/Installment Plan ليست Ledger.
4. لا تجمع العملات المختلفة في إجمالي واحد.
5. Group Expense ليست Ledger موازية؛ shares مرتبطة بديون وَصل العادية.
6. PDF والتقارير تعتمد Snapshots/Read models ولا تعيد تعريف قواعد المال.
7. Notification/Natural/Voice لا تنفذ كتابة مالية قبل مراجعة وتأكيد داخل التطبيق.
8. الملفات المهمة داخل مساحة التطبيق وتفحص بـSHA-256.
9. أي Schema جديد يأتي مع Migration + exported schema + tests + Backup/Restore update.
10. لا secrets/signing keys في Git.

## ما تبقى

1. مزامنة التوثيق الحي.
2. جولة visual polish للتطبيق على دفعات صغيرة مع الحفاظ على semantics/testTags والسلوك.
3. جولة PDF polish مع بقاء immutable snapshots وCI evidence.
4. Acceptance gate كاملة بعد التلميع.
5. Release signing/distribution في مرحلة منفصلة؛ keystore وكلمات المرور خارج Git.

فرع مرحلة الإنهاء الحالي: `agent/final-polish-doc-sync` من الرأس Verified `e09efee...`.
