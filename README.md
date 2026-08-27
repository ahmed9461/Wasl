# وَصل — Wasl

**كل حساب له وصل**

وَصل مدير مالي شخصي Local-first للديون والحقوق والالتزامات والسداد والأقساط والتذكيرات والمستندات والمرفقات. لا ينفذ تحويلات مالية فعلية، ولا يقدم كنظام محاسبة شركات أو منصة توثيق قانوني.

## الحالة الحالية

المشروع تجاوز MVP المالي الأساسي ويعمل حاليًا على استكمال الميزات المتقدمة وتثبيت بوابة التحقق قبل الإصدار.

- تطبيق Android أصلي بـKotlin وJetpack Compose وMaterial 3 وواجهة عربية RTL.
- Domain مالي مستقل في `core:domain`، مع `Money` بوحدات Minor Units من نوع `Long`.
- Ledger append-only للدفعات والعكس؛ لا حذف للتاريخ المالي.
- أشخاص وحسابات متعددة، واتجاهان: لي عند الناس / عليّ للناس، مع YER / SAR / USD دون خلط العملات.
- Room **Schema v9** مع Migrations متسلسلة من v1 حتى v9 دون destructive migration.
- الاستحقاقات، Today، WorkManager، Exact Alarm اختياري، تذكيرات متابعة عامة وإجراءات إشعار آمنة.
- Payment Promises وخطط أقساط مع Revision history وتقدم مشتق من Ledger.
- بحث محلي ومتقدم وAdaptive search.
- مستندات PDF: إيصال سداد، إيصال دين، كشف حساب متعدد الصفحات، مع immutable snapshots وSHA-256.
- Backup/Restore مشفر يشمل الجداول وملفات PDF والمرفقات.
- App Lock عبر BiometricPrompt / Device Credential وPrivacy controls.
- «طالبني» / Payment Claims كأحداث متابعة مستقلة عن Ledger.
- خزنة مرفقات محلية مرتبطة بالدين وبحركة اختيارية مع SHA-256 وفحص سلامة.
- صفحة شخص موحدة وTimeline عبر حساباته.
- قوالب رسائل سداد قابلة للنسخ والمشاركة دون إرسال تلقائي.
- إحصاءات موضوعية.
- إدخال دين باللغة الطبيعية مع Preview/Confirmation قبل الحفظ.

الحالة الحية والخطوة التالية موثقتان في `HANDOFF.md` و`docs/CURRENT_STATUS.md`.

## قاعدة البيانات

Schema الحالي: **v9**.

- v1: الأساس المالي.
- v2: reminders.
- v3: audit events.
- v4: document identities / issued documents.
- v5: payment promises.
- v6: installment plans / installments.
- v7: تعميم issued documents ودعم مستندات الحساب غير المرتبطة بحركة واحدة.
- v8: `payment_claims`.
- v9: `attachments`.

التفاصيل الدقيقة في `docs/DATABASE_SCHEMA.md`.

## Stack

- Kotlin 2.3.21
- Android Gradle Plugin 9.3.1
- Gradle 9.5.0
- Jetpack Compose
- Material 3
- Navigation 3
- Room 2.8.4
- WorkManager 2.11.2
- compileSdk / targetSdk: 36
- minSdk: 26
- JDK 17

## البناء والتحقق

المتطلبات:

- JDK 17
- Android SDK Platform 36
- Android SDK Build Tools 36.0.0

الأوامر الأساسية:

```bash
./gradlew :core:domain:test
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```

GitHub Actions يشغل Unit tests وLint وبناء Debug، يتحقق من Room Schema الحالية، ثم يشغل Android instrumentation على Emulator ويفحص أدلة PDF الفعلية.

## خريطة المستودع

| المسار | الغرض |
|---|---|
| `WASL_MASTER_PROJECT_PROMPT.md` | المرجع التأسيسي الأعلى |
| `AGENTS.md` | قواعد العمل الإلزامية |
| `PROJECT_CONTEXT.md` | صورة المشروع والبنية الحالية |
| `SPEC.md` | المواصفات التشغيلية ومعايير القبول |
| `DECISIONS.md` | القرارات المعمارية والبدائل |
| `HANDOFF.md` | الحالة الحية والخطوة التالية |
| `CHANGELOG.md` | التغييرات المهمة |
| `docs/` | المعمارية وSchema والتنقل والتصميم والأمان والاختبارات والمراحل |
| `core/domain/` | مصدر الحقيقة المالي الخالي من Android |
| `app/` | تطبيق Android وCompose وRoom والـStores والاختبارات |

## مبادئ غير قابلة للتفاوض

- لا محو للتاريخ المالي؛ التصحيح بالعكس.
- لا خلط بين العملات.
- لا Floating Point للأموال.
- لا Backend إجباري للوظائف الأساسية.
- لا أسرار أو بيانات مالية حساسة في Git أو Logs.
- لا Payment من Notification callback أو من إدخال طبيعي/صوتي دون Preview/Confirmation.
- Promise وClaim وReminder وInstallment Plan ليست مصادر حقيقة مالية.
- أي Schema جديد يأتي مع Migration + tests + Backup/Restore update.

## حالة التحقق الحالية

في Android CI #851 نجحت مرحلة `verify` كاملة، بما فيها Unit tests وLint وDebug APK وRoom Schema v9. توقفت مرحلة Android instrumentation عند compile بسبب أربعة imports قديمة في AndroidTest. يجري إصلاحها على فرع التطوير نفسه، ولا يتم دمج `main` قبل عودة البوابة كاملة إلى الأخضر.
