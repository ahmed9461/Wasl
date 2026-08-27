# HANDOFF — الحالة الحية

آخر تحديث: 2026-08-27

هذا الملف مخصص لأي جلسة تطوير جديدة لتبدأ من الرأس الصحيح دون إعادة بناء ما تم إنجازه أو الاعتماد على توثيق قديم.

## الحالة الحالية

- المشروع: **وَصل — Wasl**
- الشعار: **كل حساب له وصل**
- الإصدار: `0.1.0-dev`
- الفرع النشط: `agent/bootstrap-wasl-foundation`
- Pull Request: `#1` إلى `main`، Draft ومفتوح.
- لا تدمج `main` إلا بطلب صريح وبعد نجاح بوابة التحقق.
- Room Schema الحالي: **v9**.
- سلسلة Migrations: v1→v9 دون destructive migration.
- الرأس السابق قبل دفعة التزامن الحالية: `94ce0adf3ff64431a261042ebb62e815b42f13f1`.

## مصدر الحقيقة عند التعارض

1. الكود الحالي على الفرع النشط.
2. `WaslDatabase.kt` وRoom exported schema الحالية.
3. GitHub Actions evidence للرأس نفسه.
4. `docs/CURRENT_STATUS.md` وهذا الملف.
5. بقية ملفات المراحل والتاريخ.

لا تعتمد على وصف قديم مثل Schema v3 أو v7 إذا كان الكود/Schema الحالية تقول v9.

## ما يعمل الآن

### المصدر المالي

- أشخاص وحسابات متعددة للشخص.
- `RECEIVABLE` و`PAYABLE`.
- YER/SAR/USD دون خلط العملات.
- Money بMinor Units `Long`.
- Ledger append-only.
- Payment جزئي/كامل.
- Payment reversal بسبب موثق دون حذف الأصل.
- Idempotency وإعادة Replay لحساب الرصيد والحالة.

### الاستحقاق والمتابعة

- Due date قابل للتعديل/الإلغاء مع Audit.
- Today للاستحقاقات والمتأخرات.
- WorkManager scheduling + recovery.
- Exact Alarm اختياري للمنبه القوي مع fallback.
- General Reminder مستقل عن `due_date`: one-shot/daily/weekly/monthly.
- إشعارات آمنة: فتح الحساب، دفع جزء، تم السداد، ذكرني لاحقًا.
- لا Payment/Ledger write من Notification callback؛ كل دفع يمر بالمراجعة والتأكيد داخل وَصل.

### Promises / Installments

- Payment Promises مستقلة عن Ledger.
- Installment Plans مع revisions `ACTIVE / SUPERSEDED`.
- تقدم الأقساط مشتق من Ledger.
- Today يعرض الاستحقاق/الوعد/القسط مع بقاء النماذج مستقلة.

### البحث

- بحث Reactive أساسي ومتقدم.
- بحث في الأشخاص والحسابات والعمليات والمستندات والمبالغ والتواريخ.
- Adaptive Search موجود مع اختبارات.

### المستندات

- Payment Receipt.
- Debt Receipt.
- Account Statement متعدد الصفحات.
- Immutable snapshots، أرقام مستندات، SHA-256، page count.
- FileProvider وفحص سلامة قبل الفتح/المشاركة.

### Backup / Security

- Backup/Restore مشفر يدويًا.
- Backup schema v9 يشمل 12 جدولًا، PDF files وAttachment files.
- Restore staging + temporary Room validation + FK/invariants + rollback.
- App Lock عبر BiometricPrompt / Device Credential.
- `FLAG_SECURE` وسياسة خصوصية الإشعارات.

### Claims — «طالبني»

- منفذة في Schema v8+.
- `payment_claims` + DAO/Store/ViewModel/UI.
- `TODAY / TOMORROW / SALARY / CUSTOM`.
- `ACTIVE / RESOLVED / CANCELLED`.
- تظهر في الحساب وToday.
- Backup/Restore validation.
- مستقلة تمامًا عن Ledger والرصيد و`due_date`.

### Attachments

- منفذة في Schema v9.
- `attachments` + Store/DAO/UI/File access.
- ربط بالدين وبحركة اختيارية من نفس الدين.
- internal vault + SHA-256 + unique relative path.
- Backup/Restore للmetadata والملفات.
- فحص missing/hash mismatch/unsafe paths.

### Person Timeline / Messages / Statistics / Natural Entry

موجودة في الكود والاختبارات:

- `PersonTimelineActivity/Screen/ViewModel`.
- `PaymentMessageSection/Templates`.
- `StatisticsActivity/Screen/ViewModel` وObjective statistics.
- `NaturalEntryActivity/Parser/Draft/ConfirmationService`.
- الإدخال الطبيعي يمر Parse → Preview → Confirmation → Save، ولا يحفظ مباشرة من النص دون مراجعة.

### Voice Dictation

الإملاء الصوتي **موجود فعليًا** داخل `NaturalEntryActivity` عبر Android `RecognizerIntent`:

`Voice → RecognizerIntent result → text → NaturalEntryParser → Preview → Confirmation → Save`

- الزر الحالي: «إملاء صوتي».
- أول نتيجة معترف بها تملأ النص وتدخل التحليل.
- عدم توفر recognizer يعرض رسالة للمستخدم.
- لا حفظ مالي مباشر من الصوت.

المتبقي هنا هو **التقوية والاختبارات**، لا إعادة بناء الميزة من الصفر: adapter قابل للاختبار، حالات cancel/empty/unavailable، واختبار صريح لمسار نتيجة الصوت حتى confirmation.

## ما لم يغلق بعد

- Full CI للرأس النهائي بعد إصلاح AndroidTest imports.
- Accessibility/Adaptive audit الشامل عبر كل الشاشات.
- Voice dictation hardening واختبارات مخصصة.
- المصاريف/الديون الجماعية بحسب المواصفة الأساسية.
- جولة UI/PDF polishing النهائية.
- Release signing والتوزيع النهائي.

## حالة CI المرجعية قبل الإصلاح

Android CI #851 للرأس `94ce0adf...`:

### `verify`

نجح بالكامل:

- Unit tests ✅
- Lint ✅
- Debug APK ✅
- Room Schema v9 generated/current check ✅
- `payment_claims` ✅
- `attachments` وفهرس `relative_path` الفريد ✅

### `database-tests`

توقف في `compileDebugAndroidTestKotlin` قبل instrumentation بسبب import غير صالح:

`androidx.compose.ui.test.onNode`

في:

- `DueDateUiInstrumentedTest.kt`
- `PersonTimelineUiInstrumentedTest.kt`
- `StatisticsScreenUiInstrumentedTest.kt`
- `TodayUiInstrumentedTest.kt`

الإصلاح يزيل import فقط؛ `composeRule.onNode(...)` يبقى كما هو، ولا تخفف assertions.

## الخطوة التالية الوحيدة

1. Push الرأس النهائي الذي يجمع إصلاح imports ومزامنة كل الوثائق المتأخرة.
2. متابعة CI الجديد.
3. إذا أخضر: توثيق الرأس/رقم CI وإغلاق Claims + Attachments بعد مراجعة اختباراتهم وBackup regression.
4. إذا ظهر فشل جديد: إصلاح السبب الحقيقي فقط، بلا تعطيل اختبارات.
5. بعد الأخضر: Accessibility/Adaptive audit ثم Voice hardening ثم الميزات الجماعية.

## قواعد ثابتة لا تكسر

1. Ledger append-only؛ التصحيح بالعكس.
2. لا Floating Point للأموال.
3. لا خلط عملات في إجمالي واحد.
4. Promise ليست Payment.
5. Claim ليست Payment.
6. Reminder ليست Payment.
7. Installment Plan ليس Ledger موازيًا.
8. Notification action لا يكتب عملية مالية مباشرة.
9. Natural/Voice input لا يحفظ ماليًا دون Preview/Confirmation.
10. PDF يعتمد Snapshot ثابتًا.
11. READY document/attachment لا يفتح إذا فقد الملف أو فشل SHA-256.
12. Backup/Restore لا يتجاوز Schema/path/hash/FK/invariant validation.
13. أي Migration جديدة تأتي مع exported schema + migration tests + Backup update.
14. لا أسرار أو Signing keys في Git.
15. لا دمج إلى `main` دون طلب صريح.

## أوامر التحقق المرجعية

```bash
./gradlew :core:domain:test
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```

GitHub Actions هو بوابة التسليم المرجعية لأنه يجمع build + schema + emulator instrumentation + PDF evidence في بيئة نظيفة.
