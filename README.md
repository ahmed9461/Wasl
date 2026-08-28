# وَصل — Wasl

**كل حساب له وصل**

وَصل مدير مالي شخصي Local-first للديون والحقوق والالتزامات والسداد والأقساط والتذكيرات والمستندات والمرفقات. لا ينفذ تحويلات مالية فعلية، ولا يقدم كنظام ERP أو منصة توثيق قانوني.

## الحالة الحالية

المراحل الوظيفية الرئيسية مكتملة على فرع التطوير ومثبتة ببوابة Android كاملة. المشروع الآن في **مرحلة الإنهاء والتلميع قبل الإصدار**.

- Android أصلي بـKotlin وJetpack Compose وMaterial 3، وتجربة عربية RTL first-class.
- Domain مالي مستقل في `core:domain`.
- `Money` بMinor Units من `Long`؛ لا Floating Point في الحساب المالي.
- Ledger append-only للدفعات والعكس؛ لا حذف للتاريخ المالي.
- أشخاص وحسابات متعددة، RECEIVABLE/PAYABLE، وYER/SAR/USD دون خلط العملات.
- Room **Schema v10** مع migrations صريحة v1→v10.
- Due dates، Today، WorkManager، Exact Alarm اختياري، General Reminders.
- Payment Promises، Installment Plans/Revisions، Payment Claims.
- Search، Person Timeline، Statistics، Documents Hub، Account Details timeline.
- PDFs: إيصال سداد، إيصال دين، كشف حساب متعدد الصفحات، من immutable snapshots مع SHA-256.
- Attachments/evidence vault محلية مع فحص سلامة.
- Backup/Restore مشفر يشمل البيانات وملفات PDF والمرفقات.
- App Lock عبر BiometricPrompt / Device Credential وPrivacy controls.
- Natural Entry مع Preview/Confirmation إلزاميين.
- Voice Dictation بحالات success/empty/cancel/unavailable/failure، ولا حفظ مالي مباشر من الصوت.
- Group Expense: عملية جماعية أصلية + حصص غير متساوية تتحول إلى ديون وَصل عادية، مع atomic transaction وPreview/Confirmation.
- Adaptive UI وRTL/Bidi hardening واختبارات large-font على الشاشات الرئيسية.

الحالة الحية والخطوة التالية: `HANDOFF.md` و`docs/CURRENT_STATUS.md`.

## قاعدة البيانات

Schema الحالي: **v10**.

- v1: الأساس المالي.
- v2: reminders.
- v3: audit events.
- v4: document identities / issued documents.
- v5: payment promises.
- v6: installment plans / installments.
- v7: تعميم issued documents ودعم مستندات الحساب.
- v8: `payment_claims`.
- v9: `attachments`.
- v10: `group_expenses` + `group_expense_shares`.

Backup contract v10 يشمل **14 جدولًا** إضافة إلى ملفات PDF والمرفقات.

## آخر تحقق كامل

**Android CI #967 — run `33137676461` — head `e09efee71cea4b1734afe50a025c2a3218ec2dd5`**

- Unit tests / Lint / Debug APK / Room v10 ✅
- Android instrumentation: **123/123**، بلا failures/errors/skips ✅
- Group Expense UI 4/4 ✅
- Voice Dictation 5/5 ✅
- Natural Entry confirmation regression ✅
- Legacy individual creation + PaymentFlow regressions ✅
- Payment / Debt / Account Statement PDF evidence ✅

Instrumentation artifact: `9672922910`  
SHA-256: `c5a10dcba796b337d53fcc988f41e2c4aab6bb518bc95734a1e638ef0fdb0a4f`

## Stack

- Kotlin 2.3.21
- Android Gradle Plugin 9.3.1
- Gradle 9.5.0
- Jetpack Compose + Material 3
- Navigation 3
- Room 2.8.4
- WorkManager 2.11.2
- compileSdk / targetSdk: 36
- minSdk: 26
- JDK 17

## البناء والتحقق

```bash
./gradlew :core:domain:test
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```

GitHub Actions هي بوابة التسليم المرجعية وتجمع build + Room schema verification + Android Emulator instrumentation + PDF evidence.

## خريطة المستودع

| المسار | الغرض |
|---|---|
| `WASL_MASTER_PROJECT_PROMPT.md` | المرجع التأسيسي الأعلى |
| `AGENTS.md` | قواعد العمل الإلزامية |
| `PROJECT_CONTEXT.md` | سياق المنتج والبنية الحالية |
| `SPEC.md` | المواصفات التشغيلية ومعايير القبول |
| `DECISIONS.md` | القرارات المعمارية |
| `HANDOFF.md` | الحالة الحية والخطوة التالية |
| `CHANGELOG.md` | سجل التغييرات |
| `docs/` | المعمارية وSchema والتنقل والتصميم والأمان والتحقق |
| `core/domain/` | منطق المال الخالي من Android |
| `app/` | تطبيق Android وCompose وRoom وPDF/Backup والاختبارات |

## مبادئ غير قابلة للتفاوض

- لا محو للتاريخ المالي؛ التصحيح بالعكس.
- لا خلط بين العملات.
- لا Floating Point للأموال.
- لا Backend إجباري للوظائف الأساسية.
- لا Payment من Notification/Natural/Voice دون Preview/Confirmation.
- Promise وClaim وReminder وInstallment Plan ليست مصادر حقيقة مالية.
- Group Expense لا تنشئ Ledger موازيًا.
- أي Schema جديد يأتي مع Migration + tests + Backup/Restore update.
- لا أسرار أو signing keys في Git.
- لا دمج إلى `main` دون طلب صريح.

## المرحلة التالية

المتبقي هو **documentation sync → final UI polish → final PDF polish → acceptance gate → release signing/distribution**. التلميع يجب أن يبقى سلوكيًا محايدًا ويحافظ على invariants وtestTags والـCI الأخضر.
