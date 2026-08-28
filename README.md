# وَصل — Wasl

**كل حساب له وصل**

وَصل مدير مالي شخصي Local-first للديون والحقوق والالتزامات والسداد والأقساط والتذكيرات والمستندات والمرفقات. لا ينفذ تحويلات مالية فعلية ولا يقدم كنظام ERP أو منصة توثيق قانوني.

## الحالة

المراحل الوظيفية الرئيسية مكتملة، ومرحلة الإنهاء جمعت التلميع البصري، Document Templates v11، وسياسة الخصوصية وتجهيز مسار الإصدار. الإصدار المرشح الحالي هو **0.1.0**.

- Android أصلي: Kotlin + Jetpack Compose + Material 3 + Navigation 3.
- تجربة عربية RTL first-class مع Bidi isolation وadaptive/large-font hardening.
- `core:domain` مستقل.
- Money بminor units من `Long`؛ لا Floating Point مالي.
- Ledger append-only للدفعات والعكس.
- RECEIVABLE/PAYABLE وYER/SAR/USD دون خلط العملات.
- Room **Schema v11** مع `1.json → 11.json` ومهاجرات صريحة دون destructive migration.
- Due/Today/WorkManager/Exact Alarm/General Reminders.
- Payment Promises / Installment Plans / Payment Claims.
- Search / Person Timeline / Statistics / Documents Hub / Account Details.
- Natural Entry وVoice Dictation مع Preview/Confirmation قبل أي حفظ مالي.
- Group Expense ذري؛ كل share تصبح Debt عاديًا ولا ينشأ Ledger موازٍ.
- Payment Receipt / Debt Receipt / Account Statement من immutable snapshots.
- Document Templates v11 مع snapshot compatibility للمستندات التاريخية.
- Attachments/evidence vault + SHA-256 + FileProvider safety.
- Backup/Restore مشفر مع staging/FK/path/hash/invariant validation وrollback.
- App Lock عبر BiometricPrompt / Device Credential وPrivacy controls.
- التطبيق الحالي Local-first ولا يطلب صلاحية `INTERNET`.

## قاعدة البيانات

Schema الحالي: **v11**، وعدد جداول Room المنطقية 15. أضيف في v11 جدول `document_templates` فوق v10 التي أضافت `group_expenses` و`group_expense_shares`.

التفاصيل: `docs/DATABASE_SCHEMA.md`.

## التحقق

دفعة Document Templates/Room v11 اجتازت Android CI #1017 — run `33203634720` قبل الدمج، بما يشمل:

- Unit tests + Lint + Debug APK.
- Room v11 generation/verification.
- Emulator instrumentation والمهاجرات والـBackup/Repository regressions.
- Payment/Debt/Account Statement PDF evidence.

GitHub Actions على أحدث رأس مجمع هي بوابة القبول النهائية قبل `main`.

## الإصدار والتوقيع

- `versionName = 0.1.0`
- `versionCode = 1`
- سياسة الخصوصية: `PRIVACY_POLICY.md`
- قائمة الإصدار: `docs/RELEASE_CHECKLIST.md`
- Signed Release workflow: `.github/workflows/release.yml`

مفاتيح التوقيع وكلمات المرور **لا تحفظ في Git**. Workflow الإصدار يحتاج أسرار GitHub التالية:

- `WASL_KEYSTORE_BASE64`
- `WASL_KEYSTORE_PASSWORD`
- `WASL_KEY_ALIAS`
- `WASL_KEY_PASSWORD`

عند توفرها يبني workflow APK موقعًا، يتحقق من التوقيع بـ`apksigner`، ويولد SHA-256.

## Stack

- Kotlin 2.3.21
- Android Gradle Plugin 9.3.1
- Gradle 9.5.0
- Jetpack Compose + Material 3
- Navigation 3
- Room 2.8.4
- WorkManager 2.11.2
- compileSdk / targetSdk 36
- minSdk 26
- JDK 17

## التحقق المحلي

```bash
./gradlew :core:domain:test
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```

## مراجع المشروع

- `WASL_MASTER_PROJECT_PROMPT.md`: المرجع التأسيسي الأعلى.
- `AGENTS.md`: قواعد العمل الإلزامية.
- `PROJECT_CONTEXT.md`: سياق المنتج والبنية.
- `SPEC.md`: المواصفات ومعايير القبول؛ أي عبارات حالة تنفيذ قديمة داخله يعلو عليها `docs/CURRENT_STATUS.md`.
- `DECISIONS.md`: القرارات المعمارية.
- `HANDOFF.md`: نقطة بدء الجلسة التالية.
- `docs/CURRENT_STATUS.md`: الحالة الحية.
- `CHANGELOG.md`: سجل التغييرات.

## ثوابت

- لا محو للتاريخ المالي؛ التصحيح بالعكس.
- لا خلط عملات أو Float/Double للأموال.
- لا Backend إجباري للوظائف الأساسية.
- Notification/Natural/Voice لا تكتب ماليًا قبل Preview/Confirmation.
- Promise/Claim/Reminder/Installment ليست Ledger.
- Group Expense لا تنشئ Ledger موازيًا.
- PDF الجاهز مبني على snapshot ثابت.
- كل Schema جديد يأتي مع Migration + tests + exported schema.
- لا secrets أو signing keys في Git.
