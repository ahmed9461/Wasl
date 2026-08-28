# سياق مشروع وَصل

آخر تحديث: 2026-08-28

## الهوية

- الاسم: **وَصل — Wasl**
- الشعار: **كل حساب له وصل**
- المستودع: `ahmed9461/Wasl`
- Application ID: `com.wasl.app`
- الإصدار المرشح: `0.1.0` (`versionCode = 1`)
- فرع الإنهاء المجمع: `agent/final-polish-doc-sync`

## الهدف

وَصل مساعد مالي شخصي Local-first لإدارة الحقوق والالتزامات من الإنشاء حتى الإغلاق: الحسابات، الدفعات، الاستحقاقات، التذكيرات، الوعود، الأقساط، المطالبات، المستندات، المرفقات، النسخ الاحتياطي والحماية المحلية.

ليس بنكًا أو محفظة أو بوابة دفع أو ERP. تسجيل السداد يوثق واقعة داخل التطبيق ولا يحول أموالًا، والمستندات سجلات شخصية وليست ضمانًا قانونيًا تلقائيًا.

## المرحلة الحالية

المراحل الوظيفية والتلميع الرئيسي مكتملة، والمصدر في **Release Candidate**. آخر integration head يحمل تغييرات المنتج قبل مزامنة الوثائق هو `5794e9a74914c8af3a3ecf750664f2f6083eaf66`.

دفعة Document Templates / Room v11 اجتازت Android CI #1017 — run `33203634720` بالكامل قبل الدمج، بما يشمل Unit/Lint/Debug، Room v11، Emulator integration والمهاجرات وBackup/Repository وPDF evidence.

أحدث رأس على فرع الإنهاء يجب أن يجتاز GitHub Actions قبل النقل إلى `main`.

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
- Document Templates v11 مع أنماط MINIMAL/BUSINESS/CLASSIC/COMPACT/MODERN وتجميد اختيار القالب داخل snapshot.
- Attachments vault + SHA-256 + FileProvider.
- Backup/Restore مشفر مع staging/FK/path/hash/invariant validation وrollback.
- App Lock / `FLAG_SECURE` / notification privacy.
- RTL/Bidi/adaptive/large-font hardening.
- Local-first ولا صلاحية `INTERNET` في الإصدار الحالي.

## قاعدة البيانات

- Room Schema الحالية: **v11**.
- exported schemas: `1.json → 11.json`.
- v8: `payment_claims`.
- v9: `attachments`.
- v10: `group_expenses` + `group_expense_shares`.
- v11: `document_templates`.
- 15 جدول Room منطقيًا.
- لا destructive migration في Production.

المرجع: `app/schemas/com.wasl.app.data.local.WaslDatabase/11.json` و`docs/DATABASE_SCHEMA.md`.

## Stack

| المجال | القرار |
|---|---|
| المنصة | Android أصلي |
| اللغة | Kotlin 2.3.21 |
| UI | Jetpack Compose + Material 3 |
| المعمارية | UI / Domain / Data مع UDF وRepositories/Stores |
| قاعدة البيانات | Room 2.8.4 + KSP، Schema v11 |
| التنقل | Navigation 3 |
| الأعمال المؤجلة | WorkManager |
| التنبيه القوي | Exact Alarm اختياري مع fallback |
| المصادقة المحلية | BiometricPrompt + Device Credential |
| الإدخال الصوتي | Voice bridge → Natural Parser → Preview/Confirmation |
| PDF | Snapshots ثابتة + integrity evidence |
| البناء | AGP 9.3.1، Gradle 9.5.0، JDK 17 |
| API | min 26، compile/target 36 |

## الإصدار

- `PRIVACY_POLICY.md` يصف سلوك الإصدار الحالي.
- `docs/RELEASE_CHECKLIST.md` هي بوابة النشر.
- `.github/workflows/release.yml` يبني APK موقعًا عند توفر الأسرار الخارجية ويؤكد التوقيع بـ`apksigner` ويولد SHA-256.
- signing secrets والkeystore لا تدخل Git.

## الثوابت المعمارية

1. Ledger مصدر الحقيقة المالي وappend-only.
2. التصحيح بالعكس، لا حذف الحدث المالي الأصلي.
3. Promise/Claim/Reminder/Installment ليست Ledger.
4. لا تجمع العملات المختلفة في إجمالي واحد.
5. Group Expense ليست Ledger موازية.
6. PDF/التقارير تعتمد Snapshots ولا تعيد تعريف قواعد المال.
7. Notification/Natural/Voice لا تنفذ كتابة مالية قبل المراجعة والتأكيد.
8. الملفات المهمة تفحص بـSHA-256.
9. كل Schema جديدة معها Migration + exported schema + tests.
10. لا secrets/signing keys في Git.

## المتبقي خارج المصدر

بعد نجاح Android CI على الرأس المجمع لا تبقى مرحلة وظيفية داخل الكود. النشر العام يتطلب مفتاح توقيع فعلي خارج Git وتشغيل Signed Release ثم استكمال متطلبات منصة التوزيع، ومنها وسيلة تواصل رسمية إذا كانت مطلوبة.
